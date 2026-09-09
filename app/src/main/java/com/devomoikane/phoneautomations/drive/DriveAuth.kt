package com.devomoikane.phoneautomations.drive

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials

object DriveAuth {

    val scopes: List<Scope> = listOf(Scope(DriveScopes.DRIVE))

    private const val PREFS = "google_drive_auth"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_EXPIRY = "access_token_expiry"
    private const val VALIDITY_MARGIN_MS = 60_000L

    private fun authorizationRequest(): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(scopes)
            .build()

    /** Persist the last valid access token and its expiry so the app stays connected. */
    fun saveAccessToken(context: Context, accessToken: String, expiresInSeconds: Long? = null) {
        val expiry = if (expiresInSeconds != null && expiresInSeconds > 0) {
            System.currentTimeMillis() + expiresInSeconds * 1000L
        } else {
            System.currentTimeMillis() + 55 * 60 * 1000L
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putLong(KEY_EXPIRY, expiry)
            .apply()
    }

    fun signOut(context: Context) {
        try {
            Tasks.await(Identity.getSignInClient(context).signOut())
        } catch (e: Exception) {
            // best effort; token is cleared regardless
        }
    }

    fun clearAccessToken(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_EXPIRY)
            .apply()
    }

    fun retrieveAccessToken(context: Context): String? {
        val silent = try {
            val task = Identity.getAuthorizationClient(context).authorize(authorizationRequest())
            Tasks.await(task).accessToken
        } catch (e: Exception) {
            null
        }
        if (silent != null) return silent

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val expiry = prefs.getLong(KEY_EXPIRY, 0L)
        return if (expiry - System.currentTimeMillis() > VALIDITY_MARGIN_MS) token else null
    }

    sealed class AuthResult {
        data class Authorized(val accessToken: String) : AuthResult()
        data class RequiresUserConsent(val pendingIntent: android.app.PendingIntent) : AuthResult()
        object Unavailable : AuthResult()
    }

    fun requestAuthorization(activity: Activity, onResult: (AuthResult) -> Unit) {
        Identity.getAuthorizationClient(activity)
            .authorize(authorizationRequest())
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    result.pendingIntent?.let {
                        onResult(AuthResult.RequiresUserConsent(it))
                    } ?: onResult(AuthResult.Unavailable)
                } else {
                    onResult(AuthorizedFrom(result))
                }
            }
            .addOnFailureListener { onResult(AuthResult.Unavailable) }
    }

    fun handleActivityResult(context: Context, resultData: Intent?): AuthResult? {
        if (resultData == null) return null
        return try {
            val result = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(resultData)
            AuthorizedFrom(result)
        } catch (e: Exception) {
            AuthResult.Unavailable
        }
    }

    private fun AuthorizedFrom(result: AuthorizationResult): AuthResult {
        val token = result.accessToken
        return if (token != null) {
            AuthResult.Authorized(token)
        } else {
            AuthResult.Unavailable
        }
    }

    fun buildDriveService(accessToken: String): Drive {
        val accessTokenObj = AccessToken.newBuilder()
            .setTokenValue(accessToken)
            .build()
        val credentials = GoogleCredentials.create(accessTokenObj)
        val adapter = HttpCredentialsAdapter(credentials)
        val initializer = com.google.api.client.http.HttpRequestInitializer { request ->
            request.connectTimeout = 30_000
            request.readTimeout = 120_000
            adapter.initialize(request)
        }
        return Drive.Builder(NetHttpTransport(), GsonFactory(), initializer)
            .setApplicationName("Phone Automations")
            .build()
    }
}

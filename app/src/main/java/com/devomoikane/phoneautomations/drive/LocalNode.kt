package com.devomoikane.phoneautomations.drive

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.InputStream
import java.io.OutputStream

interface LocalNode {
    val name: String
    val isDirectory: Boolean
    fun children(): List<LocalNode>
    fun ensureChildFile(name: String): LocalNode
    fun ensureChildDirectory(name: String): LocalNode
    fun openInputStream(): InputStream
    fun openOutputStream(): OutputStream
    fun lastModified(): Long
    fun delete()
}

class SafLocalNode(
    private val context: Context,
    private val documentFile: DocumentFile
) : LocalNode {

    constructor(context: Context, treeUri: Uri) : this(
        context,
        DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Could not open tree $treeUri")
    )

    val uri: Uri get() = documentFile.uri

    override val name: String get() = documentFile.name ?: ""

    override val isDirectory: Boolean get() = documentFile.isDirectory

    override fun children(): List<LocalNode> =
        documentFile.listFiles().map { SafLocalNode(context, it) }

    override fun ensureChildFile(name: String): LocalNode {
        val existing = documentFile.findFile(name)
            ?: documentFile.createFile("application/octet-stream", name)
            ?: error("Could not create file $name")
        return SafLocalNode(context, existing)
    }

    override fun ensureChildDirectory(name: String): LocalNode {
        val existing = documentFile.findFile(name)
            ?: documentFile.createDirectory(name)
            ?: error("Could not create directory $name")
        return SafLocalNode(context, existing)
    }

    override fun openInputStream(): InputStream =
        context.contentResolver.openInputStream(documentFile.uri)
            ?: error("Could not open ${documentFile.uri} for reading")

    override fun openOutputStream(): OutputStream =
        context.contentResolver.openOutputStream(documentFile.uri, "w")
            ?: error("Could not open ${documentFile.uri} for writing")

    override fun lastModified(): Long = documentFile.lastModified()

    override fun delete() {
        documentFile.delete()
    }
}

package com.genesis.formio.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

object PhotoStorage {

    private fun photosDir(context: Context): File =
        File(context.filesDir, "genesis_photos").also { it.mkdirs() }

    fun saveFromUri(context: Context, uri: Uri): String {
        val file = File(photosDir(context), "${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return file.absolutePath
    }

    fun delete(path: String) {
        if (path.isNotBlank()) File(path).delete()
    }
}

package com.genesis.formio.util

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.util.UUID

object SignatureStorage {

    private fun signaturesDir(context: Context): File =
        File(context.filesDir, "formio_signatures").also { it.mkdirs() }

    fun saveSignature(context: Context, bitmap: Bitmap): String {
        val file = File(signaturesDir(context), "${UUID.randomUUID()}.png")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file.absolutePath
    }

    fun delete(path: String) {
        if (path.isNotBlank() && !path.startsWith("data:")) File(path).delete()
    }
}

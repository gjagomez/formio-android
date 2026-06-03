package com.genesis.formio.ui.form

interface PhotoCaptureLauncher {
    fun launchCamera(fieldKey: String, onResult: (String) -> Unit)
    fun launchGallery(fieldKey: String, onResult: (String) -> Unit)
}

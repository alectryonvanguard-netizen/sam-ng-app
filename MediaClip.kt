package com.cinemotion.editor

import android.net.Uri

/** One item on the user's timeline. URI access is granted by Android's document picker. */
data class MediaClip(
    val uri: Uri,
    val displayName: String,
    val isImage: Boolean,
    val mimeType: String
)

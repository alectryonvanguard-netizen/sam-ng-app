
package com.cinemotion.editor

import android.net.Uri

/** One item on the user's timeline. Android's document picker grants URI access. */
data class MediaClip(
    val uri: Uri,
    val displayName: String,
    val isImage: Boolean,
    val mimeType: String
)

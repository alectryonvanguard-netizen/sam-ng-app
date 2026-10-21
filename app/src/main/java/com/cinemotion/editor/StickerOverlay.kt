
package com.cinemotion.editor

/**
 * A sticker placed over a clip in the CineMotion timeline.
 *
 * x and y are normalized preview positions from 0.0 to 1.0.
 * startMs and endMs determine when the sticker is visible.
 */
data class StickerOverlay(
    val id: String,
    val content: String,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f,
    val startMs: Long = 0L,
    val endMs: Long = 5000L,
    val clipIndex: Int = 0
)

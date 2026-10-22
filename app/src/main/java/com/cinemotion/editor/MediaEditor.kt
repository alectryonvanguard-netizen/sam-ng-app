package com.cinemotion.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.Transformer
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CineMotion export engine.
 *
 * Supports:
 * - Ordered photos and videos
 * - Per-clip photo durations
 * - Video start/end trimming (Trim and Split tools)
 * - Per-clip video speed (needs Media3 1.3+)
 * - Transitions at the end of each clip (see note below)
 * - Output resolution and frame rate
 * - Optional looping background music
 * - MP4 export to Movies/CineMotion
 *
 * Transition note: Media3 plays clips back to back, so exported transitions are
 * rendered as an "out then in" effect on the two neighbouring clips (fade, slide,
 * zoom, whip pan). The editor preview shows the overlapping version.
 *
 * All trim times are in milliseconds.
 */
class MediaEditor(private val context: Context) {

    companion object {
        // Keys shared with MainActivity and ExportService.
        const val EXTRA_CLIP_STARTS = "com.cinemotion.editor.extra.CLIP_STARTS"
        const val EXTRA_CLIP_ENDS = "com.cinemotion.editor.extra.CLIP_ENDS"
        const val EXTRA_CLIP_SPEEDS = "com.cinemotion.editor.extra.CLIP_SPEEDS"
        const val EXTRA_CLIP_TRANSITIONS = "com.cinemotion.editor.extra.CLIP_TRANSITIONS"
        const val EXTRA_HEIGHT = "com.cinemotion.editor.extra.HEIGHT"
        const val EXTRA_FPS = "com.cinemotion.editor.extra.FPS"
        const val EXTRA_TRANSITION_MS = "com.cinemotion.editor.extra.TRANSITION_MS"
        const val EXTRA_ASPECT = "com.cinemotion.editor.extra.ASPECT"
        const val EXTRA_CLIP_MUTES = "com.cinemotion.editor.extra.CLIP_MUTES"

        /**
         * Builds per-clip edits from the data MainActivity sends to ExportService.
         * Every array may be null (older callers); defaults are used then.
         */
        fun editsFrom(
            uris: List<String>,
            imageFlags: BooleanArray,
            defaultImageSeconds: Int,
            starts: LongArray?,
            ends: LongArray?,
            speeds: FloatArray?,
            transitions: List<String>? = null,
            mutes: BooleanArray? = null
        ): List<ClipEdit> {
            return uris.mapIndexed { i, uriString ->
                val isImage = imageFlags.getOrNull(i) ?: false
                val start = starts?.getOrNull(i) ?: 0L
                val end = ends?.getOrNull(i)?.takeIf { it > start }
                val speed = (speeds?.getOrNull(i) ?: 1f).coerceIn(0.25f, 4f)

                ClipEdit(
                    uri = Uri.parse(uriString),
                    isImage = isImage,
                    durationMs = if (isImage) {
                        if (end != null) end - start
                        else defaultImageSeconds.coerceIn(1, 30) * 1_000L
                    } else 3_000L,
                    startMs = if (isImage) 0L else start,
                    endMs = if (isImage) null else end,
                    speed = if (isImage) 1f else speed,
                    muted = mutes?.getOrNull(i) ?: false,
                    transitionOut =
                        if (i < uris.lastIndex) transitions?.getOrNull(i) ?: "None" else "None",
                    transitionIn =
                        if (i > 0) transitions?.getOrNull(i - 1) ?: "None" else "None"
                )
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun onMain(action: () -> Unit) {
        mainHandler.post { action() }
    }

    /**
     * Per-clip edit settings.
     *
     * For photos, durationMs controls display duration.
     * For videos, startMs and endMs define the retained interval
     * (leave endMs null to use the video's natural end) and speed
     * changes playback rate (1f = normal).
     * transitionOut plays at the end of this clip, transitionIn at its start.
     */
    data class ClipEdit(
        val uri: Uri,
        val isImage: Boolean,
        val durationMs: Long = 3_000L,
        val startMs: Long = 0L,
        val endMs: Long? = null,
        val speed: Float = 1f,
        val transitionOut: String = "None",
        val transitionIn: String = "None",
        val muted: Boolean = false
    )

    /**
     * Backward-compatible export function.
     */
    @OptIn(UnstableApi::class)
    fun export(
        clips: List<MediaClip>,
        musicUri: Uri?,
        imageDurationSeconds: Int,
        onProgress: (Int) -> Unit,
        onComplete: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        val defaultDurationMs =
            imageDurationSeconds.coerceIn(1, 30) * 1_000L

        val edits = clips.map { clip ->
            ClipEdit(
                uri = clip.uri,
                isImage = clip.isImage,
                durationMs = defaultDurationMs
            )
        }

        exportEdited(
            edits = edits,
            musicUri = musicUri,
            onProgress = onProgress,
            onComplete = onComplete,
            onError = onError
        )
    }

    // -----------------------------------------------------------------------
    // Transition effects
    // -----------------------------------------------------------------------
    @OptIn(UnstableApi::class)
    private fun transitionEffects(
        name: String,
        outgoing: Boolean,
        lengthUs: Long,
        durUs: Long
    ): List<Effect> {
        if (name == "None" || durUs <= 0L || lengthUs <= durUs * 2) return emptyList()

        // Visibility of the clip: 1 = fully shown, 0 = hidden.
        fun visibility(us: Long): Float =
            if (outgoing) {
                1f - ((us - (lengthUs - durUs)).toFloat() / durUs).coerceIn(0f, 1f)
            } else {
                (us.toFloat() / durUs).coerceIn(0f, 1f)
            }

        fun smooth(x: Float): Float = x * x * (3f - 2f * x)
        val sign = if (outgoing) -1f else 1f

        val fade = RgbMatrix { us, _ ->
            val v = visibility(us)
            val s = if (name == "Fade") v else smooth(v)
            floatArrayOf(
                s, 0f, 0f, 0f,
                0f, s, 0f, 0f,
                0f, 0f, s, 0f,
                0f, 0f, 0f, 1f
            )
        }

        return when (name) {
            "Slide", "Whip Pan" -> listOf(
                MatrixTransformation { us ->
                    val v = visibility(us)
                    val k = if (name == "Whip Pan") {
                        val q = 1f - v
                        q * q * q
                    } else {
                        1f - smooth(v)
                    }
                    Matrix().apply { postTranslate(sign * 2f * k, 0f) }
                }
            )

            "Zoom" -> listOf(
                MatrixTransformation { us ->
                    val v = visibility(us)
                    val s = if (outgoing) 1f + 0.9f * (1f - v) else 0.85f + 0.15f * v
                    Matrix().apply { postScale(s, s) }
                },
                fade
            )

            else -> listOf(fade) // Crossfade, Fade, Dissolve
        }
    }

    /**
     * Export with individual clip edit settings.
     */
    @OptIn(UnstableApi::class)
    fun exportEdited(
        edits: List<ClipEdit>,
        musicUri: Uri?,
        heightPx: Int = 0,
        frameRate: Int = 30,
        transitionMs: Long = 600L,
        aspect: Float = 0f,
        onProgress: (Int) -> Unit,
        onComplete: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        if (edits.isEmpty()) {
            onMain { onError("Add at least one photo or video first.") }
            return
        }

        val outputFile = File(
            context.cacheDir,
            "cinemotion-${System.currentTimeMillis()}.mp4"
        )

        val finished = AtomicBoolean(false)

        fun fail(message: String) {
            if (finished.compareAndSet(false, true)) {
                outputFile.delete()
                onMain { onError(message) }
            }
        }

        try {
            edits.forEachIndexed { index, edit ->
                if (edit.durationMs < 1_000L && edit.isImage) {
                    throw IOException(
                        "Photo ${index + 1} must last at least one second."
                    )
                }

                if (!edit.isImage) {
                    if (edit.startMs < 0L) {
                        throw IOException(
                            "Video ${index + 1} has an invalid start time."
                        )
                    }

                    if (edit.endMs != null &&
                        edit.endMs <= edit.startMs
                    ) {
                        throw IOException(
                            "Video ${index + 1} must end after it starts."
                        )
                    }

                    if (edit.speed <= 0f) {
                        throw IOException(
                            "Video ${index + 1} has an invalid speed."
                        )
                    }
                }

                context.contentResolver
                    .openFileDescriptor(edit.uri, "r")
                    ?.use { }
                    ?: throw IOException(
                        "Cannot open media item ${index + 1}."
                    )
            }

            val editedClips = edits.map { edit ->
                val mediaItemBuilder = MediaItem.Builder()
                    .setUri(edit.uri)

                // Output length of this clip in microseconds (null when unknown).
                var lengthUs: Long? = null

                if (edit.isImage) {
                    val d = edit.durationMs.coerceIn(1_000L, 300_000L)
                    mediaItemBuilder.setImageDurationMs(d)
                    lengthUs = d * 1000L
                } else {
                    val clippingBuilder =
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(edit.startMs)

                    if (edit.endMs != null) {
                        clippingBuilder.setEndPositionMs(edit.endMs)
                        lengthUs = ((edit.endMs - edit.startMs) / edit.speed).toLong() * 1000L
                    }

                    mediaItemBuilder
                        .setClippingConfiguration(clippingBuilder.build())
                }

                val audioProcessors = mutableListOf<AudioProcessor>()
                val videoEffects = mutableListOf<Effect>()

                if (!edit.isImage && edit.speed != 1f) {
                    audioProcessors.add(
                        SonicAudioProcessor().apply {
                            setSpeed(edit.speed)
                            setPitch(1f)
                        }
                    )
                    videoEffects.add(SpeedChangeEffect(edit.speed))
                }

                if (lengthUs != null) {
                    val dur = minOf(transitionMs * 1000L, lengthUs / 2)
                    videoEffects.addAll(
                        transitionEffects(edit.transitionIn, false, lengthUs, dur)
                    )
                    videoEffects.addAll(
                        transitionEffects(edit.transitionOut, true, lengthUs, dur)
                    )
                }

                EditedMediaItem.Builder(mediaItemBuilder.build())
                    .setFrameRate(frameRate.coerceIn(15, 60))
                    .setEffects(Effects(audioProcessors, videoEffects))
                    .setRemoveAudio(edit.muted)
                    .build()
            }

            val videoSequence =
                EditedMediaItemSequence.withAudioAndVideoFrom(editedClips)

            val sequences = mutableListOf(videoSequence)

            if (musicUri != null) {
                context.contentResolver
                    .openFileDescriptor(musicUri, "r")
                    ?.use { }
                    ?: throw IOException(
                        "Cannot open the selected audio file."
                    )

                val musicItem = EditedMediaItem.Builder(
                    MediaItem.fromUri(musicUri)
                ).build()

                val musicSequence =
                    EditedMediaItemSequence.withAudioFrom(listOf(musicItem))
                        .buildUpon()
                        .setIsLooping(true)
                        .build()

                sequences.add(musicSequence)
            }

            val compositionBuilder = Composition.Builder(sequences)
            val compEffects = mutableListOf<Effect>()
            if (aspect > 0f) {
                compEffects.add(
                    Presentation.createForAspectRatio(
                        aspect,
                        Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                    )
                )
            }
            if (heightPx > 0) {
                // Portrait canvases use the quality as the short side (1080p = 1080x1920).
                val h = if (aspect in 0.01f..0.999f) (heightPx / aspect).toInt() else heightPx
                compEffects.add(Presentation.createForHeight(h and 1.inv()))
            }
            if (compEffects.isNotEmpty()) {
                compositionBuilder.setEffects(Effects(emptyList(), compEffects))
            }
            val composition = compositionBuilder.build()

            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {

                    override fun onCompleted(
                        composition: Composition,
                        exportResult: androidx.media3.transformer.ExportResult
                    ) {
                        if (!finished.compareAndSet(false, true)) return

                        try {
                            val savedUri = copyToMovies(outputFile)
                            outputFile.delete()

                            onMain {
                                onProgress(100)
                                onComplete(savedUri)
                            }
                        } catch (e: Exception) {
                            outputFile.delete()
                            onMain {
                                onError(
                                    "Video rendered, but saving failed: " +
                                        (e.message ?: "unknown error")
                                )
                            }
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: androidx.media3.transformer.ExportResult,
                        exportException:
                            androidx.media3.transformer.ExportException
                    ) {
                        fail(
                            "Export failed: " +
                                (exportException.message
                                    ?: "Unsupported media or codec")
                        )
                    }
                })
                .build()

            onMain { onProgress(1) }
            transformer.start(composition, outputFile.absolutePath)

        } catch (e: Exception) {
            fail(
                "Could not start export: " +
                    (e.message ?: e.javaClass.simpleName)
            )
        }
    }

    @Throws(IOException::class)
    private fun copyToMovies(source: File): Uri {
        if (!source.exists() || source.length() == 0L) {
            throw IOException("The rendered video is empty.")
        }

        val resolver = context.contentResolver
        val filename = "CineMotion_${System.currentTimeMillis()}.mp4"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, filename)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_MOVIES}/CineMotion"
                )
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val collection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(
                    MediaStore.VOLUME_EXTERNAL_PRIMARY
                )
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

        val uri = resolver.insert(collection, values)
            ?: throw IOException(
                "Android could not create the output video."
            )

        try {
            val output = resolver.openOutputStream(uri)
                ?: throw IOException("Could not open the output file.")

            output.use { destination ->
                FileInputStream(source).use { input ->
                    input.copyTo(destination)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val ready = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                resolver.update(uri, ready, null, null)
            }

            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }
}

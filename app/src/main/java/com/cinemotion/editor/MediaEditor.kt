
package com.cinemotion.editor

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.Transformer
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class MediaEditor(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun onMain(action: () -> Unit) {
        mainHandler.post { action() }
    }

    @OptIn(UnstableApi::class)
    fun export(
        clips: List<MediaClip>,
        musicUri: Uri?,
        imageDurationSeconds: Int,
        onProgress: (Int) -> Unit,
        onComplete: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        if (clips.isEmpty()) {
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
            // Verify that each source URI can be opened before rendering.
            clips.forEach { clip ->
                context.contentResolver.openFileDescriptor(clip.uri, "r")?.use { }
                    ?: throw IOException("Cannot open ${clip.displayName}")
            }

            val editedClips = clips.map { clip ->
                val itemBuilder = MediaItem.Builder()
                    .setUri(clip.uri)

                if (clip.isImage) {
                    itemBuilder.setImageDurationMs(
                        imageDurationSeconds.coerceIn(1, 30) * 1_000L
                    )
                }

                EditedMediaItem.Builder(itemBuilder.build())
                    .setFrameRate(30)
                    .setEffects(Effects(emptyList(), emptyList()))
                    .build()
            }

            val videoSequence =
                EditedMediaItemSequence.withAudioAndVideoFrom(editedClips)

            val sequences = mutableListOf(videoSequence)

            if (musicUri != null) {
                context.contentResolver.openFileDescriptor(musicUri, "r")?.use { }
                    ?: throw IOException("Cannot open the selected audio file.")

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

            val composition = Composition.Builder(sequences).build()

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
                        exportException: androidx.media3.transformer.ExportException
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
            ?: throw IOException("Android could not create the output video.")

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

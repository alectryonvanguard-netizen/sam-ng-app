
package com.cinemotion.editor

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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

class MediaEditor(private val context: Context) {

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
            onError("Add at least one photo or video first.")
            return
        }

        val outputFile = File(
            context.cacheDir,
            "cinemotion-${System.currentTimeMillis()}.mp4"
        )

        try {
            val editedClips = clips.map { clip ->
                val builder = MediaItem.Builder()
                    .setUri(clip.uri)

                if (clip.isImage) {
                    builder.setImageDurationMs(
                        imageDurationSeconds.coerceIn(1, 30) * 1_000L
                    )
                }

                EditedMediaItem.Builder(builder.build())
                    .setFrameRate(30)
                    .setEffects(Effects(emptyList(), emptyList()))
                    .build()
            }

            val videoSequence =
                EditedMediaItemSequence.withAudioAndVideoFrom(editedClips)

            val sequences = mutableListOf(videoSequence)

            if (musicUri != null) {
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
                        try {
                            val savedUri = copyToMovies(outputFile)
                            outputFile.delete()
                            onProgress(100)
                            onComplete(savedUri)
                        } catch (e: Exception) {
                            outputFile.delete()
                            onError(
                                "The video was rendered, but saving failed: " +
                                    (e.message ?: "unknown error")
                            )
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: androidx.media3.transformer.ExportResult,
                        exportException: androidx.media3.transformer.ExportException
                    ) {
                        outputFile.delete()
                        onError(
                            "Export failed: " +
                                (exportException.message
                                    ?: "unsupported media or codec")
                        )
                    }
                })
                .build()

            transformer.start(composition, outputFile.absolutePath)
            onProgress(1)

        } catch (e: Exception) {
            outputFile.delete()
            onError(
                "Could not start export: " +
                    (e.message ?: e.javaClass.simpleName)
            )
        }
    }

    @Throws(IOException::class)
    private fun copyToMovies(source: File): Uri {
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

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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

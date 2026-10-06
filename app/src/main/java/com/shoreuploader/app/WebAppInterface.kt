package com.shoreuploader.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

class WebAppInterface(private val context: Context, private val webView: WebView) {

    private val scope = CoroutineScope(Dispatchers.Main)

    // Stream cache for large files (no file size limit, handles 1GB, 2GB, 4GB+)
    private val activeStreams = ConcurrentHashMap<String, OutputStream>()
    private val activeUris = ConcurrentHashMap<String, Uri>()
    private val activeFiles = ConcurrentHashMap<String, File>()

    @JavascriptInterface
    fun showToast(message: String) {
        scope.launch {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun triggerHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(15)
                }
            }
        } catch (_: Exception) {}
    }

    // --- CHUNKED STREAMING (Supports any file size without memory limits) ---
    @JavascriptInterface
    fun startChunkedStream(streamId: String, fileName: String, target: String): Boolean {
        try {
            val cleanFileName = if (fileName.endsWith(".mp4", ignoreCase = true)) fileName else "$fileName.mp4"

            if (target == "gallery") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, cleanFileName)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/ShoreUploader")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    val itemUri = resolver.insert(collection, values) ?: return false
                    val out = resolver.openOutputStream(itemUri) ?: return false
                    activeStreams[streamId] = out
                    activeUris[streamId] = itemUri
                } else {
                    val moviesDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                        "ShoreUploader"
                    ).apply { if (!exists()) mkdirs() }
                    val targetFile = File(moviesDir, cleanFileName)
                    val out = FileOutputStream(targetFile)
                    activeStreams[streamId] = out
                    activeFiles[streamId] = targetFile
                }
            } else {
                // For tiktok or share
                val videosDir = File(context.cacheDir, "videos").apply { if (!exists()) mkdirs() }
                val targetFile = File(videosDir, cleanFileName)
                val out = FileOutputStream(targetFile)
                activeStreams[streamId] = out
                activeFiles[streamId] = targetFile
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    @JavascriptInterface
    fun appendStreamChunk(streamId: String, base64Chunk: String): Boolean {
        return try {
            val stream = activeStreams[streamId] ?: return false
            val bytes = Base64.decode(base64Chunk, Base64.DEFAULT)
            stream.write(bytes)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @JavascriptInterface
    fun finishChunkedStream(streamId: String, target: String, fileName: String, callbackJs: String? = null) {
        scope.launch(Dispatchers.IO) {
            try {
                val stream = activeStreams.remove(streamId)
                stream?.flush()
                stream?.close()

                val uri = activeUris.remove(streamId)
                val file = activeFiles.remove(streamId)

                if (target == "gallery") {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri != null) {
                        val values = ContentValues().apply {
                            put(MediaStore.Video.Media.IS_PENDING, 0)
                        }
                        context.contentResolver.update(uri, values, null, null)
                    } else if (file != null) {
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(file.absolutePath),
                            arrayOf("video/mp4"),
                            null
                        )
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Đã lưu vào Thư viện: Movies/ShoreUploader", Toast.LENGTH_LONG).show()
                        if (!callbackJs.isNullOrEmpty()) {
                            webView.evaluateJavascript("$callbackJs(true, '$fileName')", null)
                        }
                    }
                } else if (target == "tiktok") {
                    val targetFile = file ?: return@launch
                    val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", targetFile)

                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "video/mp4"
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    val tikTokPackages = listOf(
                        "com.zhiliaoapp.musically",
                        "com.ss.android.ugc.trill",
                        "com.ss.android.ugc.aweme"
                    )

                    var found = false
                    val pm = context.packageManager
                    for (pkg in tikTokPackages) {
                        try {
                            pm.getPackageInfo(pkg, 0)
                            intent.setPackage(pkg)
                            found = true
                            break
                        } catch (_: Exception) {}
                    }

                    withContext(Dispatchers.Main) {
                        if (found) {
                            context.startActivity(intent)
                        } else {
                            val chooser = Intent.createChooser(intent, "Chia sẻ video tới TikTok:")
                            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(chooser)
                        }
                    }
                } else if (target == "share") {
                    val targetFile = file ?: return@launch
                    val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", targetFile)

                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "video/mp4"
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    withContext(Dispatchers.Main) {
                        val chooser = Intent.createChooser(intent, "Chia sẻ video MP4:")
                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(chooser)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Lỗi: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // --- FALLBACK DIRECT BASE64 METHODS ---
    @JavascriptInterface
    fun saveVideoToGallery(fileName: String, base64Data: String, callbackJs: String? = null) {
        val streamId = "direct_${System.currentTimeMillis()}"
        if (startChunkedStream(streamId, fileName, "gallery")) {
            appendStreamChunk(streamId, base64Data)
            finishChunkedStream(streamId, "gallery", fileName, callbackJs)
        }
    }

    @JavascriptInterface
    fun shareToTikTok(fileName: String, base64Data: String) {
        val streamId = "direct_tiktok_${System.currentTimeMillis()}"
        if (startChunkedStream(streamId, fileName, "tiktok")) {
            appendStreamChunk(streamId, base64Data)
            finishChunkedStream(streamId, "tiktok", fileName, null)
        }
    }

    @JavascriptInterface
    fun shareGeneric(fileName: String, base64Data: String) {
        val streamId = "direct_share_${System.currentTimeMillis()}"
        if (startChunkedStream(streamId, fileName, "share")) {
            appendStreamChunk(streamId, base64Data)
            finishChunkedStream(streamId, "share", fileName, null)
        }
    }
}

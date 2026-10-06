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

class WebAppInterface(private val context: Context, private val webView: WebView) {

    private val scope = CoroutineScope(Dispatchers.Main)

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

    @JavascriptInterface
    fun saveVideoToGallery(fileName: String, base64Data: String, callbackJs: String? = null) {
        scope.launch(Dispatchers.IO) {
            try {
                val cleanFileName = if (fileName.endsWith(".mp4", ignoreCase = true)) fileName else "$fileName.mp4"
                val bytes = Base64.decode(base64Data, Base64.DEFAULT)

                var savedUri: Uri? = null

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, cleanFileName)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/ShoreUploader")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }

                    val resolver = context.contentResolver
                    val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    val itemUri = resolver.insert(collection, values)

                    if (itemUri != null) {
                        resolver.openOutputStream(itemUri)?.use { out ->
                            out.write(bytes)
                            out.flush()
                        }
                        values.clear()
                        values.put(MediaStore.Video.Media.IS_PENDING, 0)
                        resolver.update(itemUri, values, null, null)
                        savedUri = itemUri
                    }
                } else {
                    val moviesDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                        "ShoreUploader"
                    )
                    if (!moviesDir.exists()) moviesDir.mkdirs()
                    val targetFile = File(moviesDir, cleanFileName)
                    FileOutputStream(targetFile).use { out ->
                        out.write(bytes)
                        out.flush()
                    }
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(targetFile.absolutePath),
                        arrayOf("video/mp4"),
                        null
                    )
                    savedUri = Uri.fromFile(targetFile)
                }

                withContext(Dispatchers.Main) {
                    if (savedUri != null) {
                        Toast.makeText(context, "Đã lưu vào Bộ sưu tập: Movies/ShoreUploader", Toast.LENGTH_LONG).show()
                        if (!callbackJs.isNullOrEmpty()) {
                            webView.evaluateJavascript("$callbackJs(true, '$cleanFileName')", null)
                        }
                    } else {
                        Toast.makeText(context, "Lỗi khi lưu video", Toast.LENGTH_SHORT).show()
                        if (!callbackJs.isNullOrEmpty()) {
                            webView.evaluateJavascript("$callbackJs(false, 'Save failed')", null)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Lỗi: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    if (!callbackJs.isNullOrEmpty()) {
                        webView.evaluateJavascript("$callbackJs(false, '${e.localizedMessage}')", null)
                    }
                }
            }
        }
    }

    @JavascriptInterface
    fun shareToTikTok(fileName: String, base64Data: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val file = writeToCacheFile(fileName, base64Data)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                // Try TikTok packages
                val tikTokPackages = listOf(
                    "com.zhiliaoapp.musically", // TikTok Global
                    "com.ss.android.ugc.trill", // TikTok Asia / Vietnam
                    "com.ss.android.ugc.aweme"  // Douyin
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
                        // Fallback to chooser
                        val chooser = Intent.createChooser(intent, "Chia sẻ video tới TikTok / Ứng dụng:")
                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(chooser)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Lỗi chia sẻ: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @JavascriptInterface
    fun shareGeneric(fileName: String, base64Data: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val file = writeToCacheFile(fileName, base64Data)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                withContext(Dispatchers.Main) {
                    val chooser = Intent.createChooser(intent, "Chia sẻ video MP4:")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Lỗi chia sẻ: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun writeToCacheFile(fileName: String, base64Data: String): File {
        val cleanName = if (fileName.endsWith(".mp4", ignoreCase = true)) fileName else "$fileName.mp4"
        val videosDir = File(context.cacheDir, "videos").apply { if (!exists()) mkdirs() }
        val targetFile = File(videosDir, cleanName)
        val bytes = Base64.decode(base64Data, Base64.DEFAULT)
        FileOutputStream(targetFile).use { out ->
            out.write(bytes)
            out.flush()
        }
        return targetFile
    }
}

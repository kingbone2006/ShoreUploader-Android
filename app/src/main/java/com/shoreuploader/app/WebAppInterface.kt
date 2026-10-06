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

    // Last rendered file cache
    @Volatile private var lastSavedUri: Uri? = null
    @Volatile private var lastSavedFile: File? = null
    @Volatile private var lastSavedFileName: String = ""

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
            lastSavedFileName = cleanFileName

            // Always write to a dedicated export file in Movies/ShoreUploader
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

            // Also keep a cache mirror for instant FileProvider sharing to apps like TikTok
            try {
                val cacheDir = File(context.cacheDir, "videos").apply { if (!exists()) mkdirs() }
                val cacheTarget = File(cacheDir, cleanFileName)
                // Cache file will be created when needed or copied
                lastSavedFile = cacheTarget
            } catch (_: Exception) {}

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

            // If we have a cache file, also write bytes there for 100% reliable FileProvider sharing to TikTok
            lastSavedFile?.let { cFile ->
                FileOutputStream(cFile, true).use { cOut ->
                    cOut.write(bytes)
                }
            }
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

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri != null) {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                    }
                    context.contentResolver.update(uri, values, null, null)
                    lastSavedUri = uri
                } else if (file != null) {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(file.absolutePath),
                        arrayOf("video/mp4"),
                        null
                    )
                    lastSavedUri = Uri.fromFile(file)
                    lastSavedFile = file
                }

                withContext(Dispatchers.Main) {
                    if (!callbackJs.isNullOrEmpty()) {
                        webView.evaluateJavascript("$callbackJs(true, '$fileName')", null)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Lỗi hoàn tất file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    if (!callbackJs.isNullOrEmpty()) {
                        webView.evaluateJavascript("$callbackJs(false, '${e.localizedMessage}')", null)
                    }
                }
            }
        }
    }

    // --- CAPCUT-STYLE DIRECT JUMP TO TIKTOK POSTING SCREEN ---
    @JavascriptInterface
    fun openTikTokPost() {
        scope.launch(Dispatchers.Main) {
            try {
                val uriToShare: Uri? = when {
                    lastSavedFile != null && lastSavedFile!!.exists() && lastSavedFile!!.length() > 0 -> {
                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", lastSavedFile!!)
                    }
                    lastSavedUri != null -> lastSavedUri
                    else -> {
                        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "ShoreUploader")
                        val file = if (lastSavedFileName.isNotEmpty()) File(dir, lastSavedFileName) else dir.listFiles()?.maxByOrNull { it.lastModified() }
                        if (file != null && file.exists()) {
                            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        } else null
                    }
                }

                if (uriToShare == null) {
                    Toast.makeText(context, "Chưa tìm thấy video đã render", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, uriToShare)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                // Known TikTok package identifiers (Vietnam, Global, Lite, Douyin)
                val tikTokPackages = listOf(
                    "com.ss.android.ugc.trill",      // TikTok Vietnam / SEA (most common in VN)
                    "com.zhiliaoapp.musically",       // TikTok Global
                    "com.zhiliaoapp.musically.go",    // TikTok Lite
                    "com.ss.android.ugc.aweme"        // Douyin
                )

                val pm = context.packageManager
                var launched = false

                for (pkg in tikTokPackages) {
                    try {
                        pm.getPackageInfo(pkg, 0)
                        context.grantUriPermission(pkg, uriToShare, Intent.FLAG_GRANT_READ_URI_PERMISSION)

                        val directIntent = Intent(intent).apply {
                            setPackage(pkg)
                        }
                        context.startActivity(directIntent)
                        launched = true
                        break
                    } catch (_: Exception) {}
                }

                if (!launched) {
                    // If TikTok is not installed or detected, open chooser
                    Toast.makeText(context, "Không tìm thấy app TikTok, mở danh sách chia sẻ...", Toast.LENGTH_SHORT).show()
                    val chooser = Intent.createChooser(intent, "Chia sẻ video tới TikTok:")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Lỗi mở TikTok: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Open video in default system player / gallery
    @JavascriptInterface
    fun openSavedVideo() {
        scope.launch(Dispatchers.Main) {
            try {
                val uri = when {
                    lastSavedFile != null && lastSavedFile!!.exists() -> {
                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", lastSavedFile!!)
                    }
                    lastSavedUri != null -> lastSavedUri
                    else -> null
                }

                if (uri != null) {
                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "video/mp4")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(viewIntent)
                } else {
                    Toast.makeText(context, "Không tìm thấy video đã render", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Không thể mở trình phát: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @JavascriptInterface
    fun shareGeneric() {
        scope.launch(Dispatchers.Main) {
            try {
                val uri = when {
                    lastSavedFile != null && lastSavedFile!!.exists() -> {
                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", lastSavedFile!!)
                    }
                    lastSavedUri != null -> lastSavedUri
                    else -> null
                }

                if (uri != null) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "video/mp4"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(intent, "Chia sẻ video MP4:")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Lỗi chia sẻ: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

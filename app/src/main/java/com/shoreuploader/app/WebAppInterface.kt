package com.shoreuploader.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WebAppInterface(private val context: Context, private val webView: WebView) {

    private val scope = CoroutineScope(Dispatchers.Main)

    @Volatile var selectedUri: Uri? = null
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

    fun setSelectedUriDirect(uri: Uri) {
        selectedUri = uri
        queryUriMetadata(uri)
    }

    private fun queryUriMetadata(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            try {
                var displayName = "video.mp4"
                var size: Long = 0

                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex != -1) displayName = cursor.getString(nameIndex) ?: displayName
                        if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                    }
                }

                if (size <= 0) {
                    try {
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            size = pfd.statSize
                        }
                    } catch (_: Exception) {}
                }

                // Detect video codec using MediaExtractor
                val codecInfo = detectVideoCodec(uri)

                withContext(Dispatchers.Main) {
                    webView.evaluateJavascript(
                        "if (window.onNativeFileReady) window.onNativeFileReady('${displayName.replace("'", "\\'")}', $size, '${codecInfo.replace("'", "\\'")}');",
                        null
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun detectVideoCodec(uri: Uri): String {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            var foundCodec = ""
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    foundCodec = when (mime) {
                        MediaFormat.MIMETYPE_VIDEO_AVC -> "H.264 (AVC)"
                        MediaFormat.MIMETYPE_VIDEO_HEVC -> "H.265 (HEVC)"
                        MediaFormat.MIMETYPE_VIDEO_AV1 -> "AV1 (Cần chuyển đổi)"
                        MediaFormat.MIMETYPE_VIDEO_VP9 -> "VP9 (Cần chuyển đổi)"
                        MediaFormat.MIMETYPE_VIDEO_VP8 -> "VP8 (Cần chuyển đổi)"
                        else -> mime.substringAfter("video/")
                    }
                    break
                }
            }
            if (foundCodec.isNotEmpty()) foundCodec else "Không xác định"
        } catch (_: Exception) {
            "MP4 Native"
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    // Check if video requires transcoding to H.264 (e.g. MKV, AV1, VP9, ProRes)
    private fun checkIfNeedsTranscoding(uri: Uri, fileName: String): Boolean {
        if (!fileName.endsWith(".mp4", ignoreCase = true) && !fileName.endsWith(".mov", ignoreCase = true)) {
            return true // .mkv, .webm, .avi, etc. require transcode/remux to mp4
        }
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            var needsTranscode = false
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    if (mime != MediaFormat.MIMETYPE_VIDEO_AVC && mime != MediaFormat.MIMETYPE_VIDEO_HEVC) {
                        needsTranscode = true // AV1, VP9, VP8, ProRes, etc.
                    }
                    break
                }
            }
            needsTranscode
        } catch (_: Exception) {
            false
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    // Transcode any video (AV01, VP9, ProRes, MKV, etc.) into pristine H.264 MP4 using Android Media3 Transformer
    private suspend fun transcodeToH264(inputUri: Uri, onProgress: (Int) -> Unit): File = suspendCancellableCoroutine { continuation ->
        val transcodeDir = File(context.cacheDir, "transcoded").apply { if (!exists()) mkdirs() }
        val outputFile = File(transcodeDir, "tc_${System.currentTimeMillis()}.mp4")

        val mainHandler = Handler(Looper.getMainLooper())
        var isFinished = false

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    isFinished = true
                    if (continuation.isActive) {
                        continuation.resume(outputFile)
                    }
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    isFinished = true
                    if (continuation.isActive) {
                        continuation.resumeWithException(exportException)
                    }
                }
            })
            .build()

        val progressHolder = ProgressHolder()
        val pollProgress = object : Runnable {
            override fun run() {
                if (isFinished) return
                val state = transformer.getProgress(progressHolder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(progressHolder.progress)
                }
                mainHandler.postDelayed(this, 250)
            }
        }
        mainHandler.post(pollProgress)

        try {
            val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(inputUri)).build()
            transformer.start(editedMediaItem, outputFile.absolutePath)
        } catch (e: Exception) {
            isFinished = true
            if (continuation.isActive) {
                continuation.resumeWithException(e)
            }
        }

        continuation.invokeOnCancellation {
            isFinished = true
            transformer.cancel()
        }
    }

    // --- HIGH-PERFORMANCE ZERO-RAM NATIVE OPTIMIZER (Supports 3GB, 5GB, 10GB+ without crash) ---
    @JavascriptInterface
    fun startNativeOptimization(optionsJson: String) {
        val initialUri = selectedUri
        if (initialUri == null) {
            scope.launch {
                Toast.makeText(context, "Chưa chọn file video!", Toast.LENGTH_SHORT).show()
                webView.evaluateJavascript("onNativeError('Chưa chọn video');", null)
            }
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                // 1. Determine original file name and size
                var fileName = "video.mp4"
                var fileSize: Long = -1

                context.contentResolver.query(initialUri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx != -1) fileName = cursor.getString(nameIdx) ?: fileName
                        if (sizeIdx != -1) fileSize = cursor.getLong(sizeIdx)
                    }
                }

                if (fileSize <= 0) {
                    context.contentResolver.openFileDescriptor(initialUri, "r")?.use { pfd ->
                        fileSize = pfd.statSize
                    }
                }

                // Check if file is AV01, VP9, ProRes or MKV that must be converted to H.264
                var workUri: Uri = initialUri
                var tempTranscodedFile: File? = null

                if (checkIfNeedsTranscoding(initialUri, fileName)) {
                    withContext(Dispatchers.Main) {
                        webView.evaluateJavascript("onNativeProgress(2, 'Đang tự động chuyển đổi codec sang H.264...');", null)
                    }

                    tempTranscodedFile = transcodeToH264(initialUri) { pct ->
                        val scaledPct = Math.min(40, Math.round(pct * 0.40f))
                        scope.launch(Dispatchers.Main) {
                            webView.evaluateJavascript("onNativeProgress($scaledPct, 'Đang chuyển đổi sang H.264: $pct%...');", null)
                        }
                    }

                    workUri = Uri.fromFile(tempTranscodedFile)
                    fileSize = tempTranscodedFile.length()
                }

                val cleanName = if (fileName.endsWith(".mp4", ignoreCase = true)) {
                    fileName.replace(".mp4", "_shore_tiktok.mp4", ignoreCase = true)
                } else {
                    fileName.replace(Regex("\\.[^/.]+$"), "") + "_shore_tiktok.mp4"
                }
                lastSavedFileName = cleanName

                withContext(Dispatchers.Main) {
                    webView.evaluateJavascript("onNativeProgress(45, 'Đang quét cấu trúc MP4 box...');", null)
                }

                // 2. Scan top-level boxes: ftyp, moov, mdat
                var ftypBytes: ByteArray? = null
                var moovBytes: ByteArray? = null
                var mdatStart: Long = -1
                var mdatHeaderSize: Int = 8
                var mdatDataSize: Long = -1
                var mdatTotalSize: Long = -1

                context.contentResolver.openInputStream(workUri)?.use { stream ->
                    var currentPos: Long = 0
                    val headerBuf = ByteArray(16)

                    while (true) {
                        val readHdr = readFully(stream, headerBuf, 8)
                        if (readHdr < 8) break

                        val size32 = ByteBuffer.wrap(headerBuf, 0, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xffffffffL
                        val type = String(headerBuf, 4, 4, Charsets.ISO_8859_1)

                        var hdrSize = 8
                        var totalBoxSize = size32

                        if (size32 == 1L) {
                            val readExt = readFully(stream, headerBuf, 8)
                            if (readExt < 8) break
                            totalBoxSize = ByteBuffer.wrap(headerBuf, 0, 8).order(ByteOrder.BIG_ENDIAN).long
                            hdrSize = 16
                        } else if (size32 == 0L) {
                            if (fileSize > 0) {
                                totalBoxSize = fileSize - currentPos
                            } else {
                                totalBoxSize = -1
                            }
                        }

                        val payloadSize = if (totalBoxSize > 0) totalBoxSize - hdrSize else -1

                        if (type == "ftyp") {
                            val ftypBuf = ByteArray(totalBoxSize.toInt())
                            System.arraycopy(headerBuf, 0, ftypBuf, 0, hdrSize)
                            readFully(stream, ftypBuf, payloadSize.toInt(), hdrSize)
                            ftypBytes = ftypBuf
                        } else if (type == "moov") {
                            val moovBuf = ByteArray(totalBoxSize.toInt())
                            System.arraycopy(headerBuf, 0, moovBuf, 0, hdrSize)
                            readFully(stream, moovBuf, payloadSize.toInt(), hdrSize)
                            moovBytes = moovBuf
                        } else if (type == "mdat") {
                            mdatStart = currentPos
                            mdatHeaderSize = hdrSize
                            mdatTotalSize = totalBoxSize
                            mdatDataSize = payloadSize
                            if (payloadSize > 0) {
                                skipFully(stream, payloadSize)
                            }
                        } else {
                            if (payloadSize > 0) {
                                skipFully(stream, payloadSize)
                            }
                        }

                        if (totalBoxSize > 0) {
                            currentPos += totalBoxSize
                        } else {
                            break
                        }

                        if (ftypBytes != null && moovBytes != null && mdatStart >= 0) {
                            break
                        }
                    }
                }

                if (ftypBytes == null || moovBytes == null || mdatStart < 0) {
                    throw IllegalStateException("Không tìm thấy đủ các box MP4 chuẩn (ftyp, moov, mdat). Tệp có thể không phải MP4 chuẩn.")
                }

                val ftypB64 = Base64.encodeToString(ftypBytes, Base64.NO_WRAP)
                val moovB64 = Base64.encodeToString(moovBytes, Base64.NO_WRAP)

                val mdatInfo = JSONObject().apply {
                    put("start", mdatStart)
                    put("headerSize", mdatHeaderSize)
                    put("size", mdatTotalSize)
                    put("dataSize", mdatDataSize)
                }

                val actualFileSize = if (fileSize > 0) fileSize else (mdatStart + mdatTotalSize)

                withContext(Dispatchers.Main) {
                    webView.evaluateJavascript("onNativeProgress(55, 'Tối ưu hóa thẻ màu và Ghost Samples...');", null)

                    val jsCall = "window.ShoreEngine.patchMp4AtomsBase64('$ftypB64', '$moovB64', '${mdatInfo.toString().replace("'", "\\'")}', $actualFileSize, '${optionsJson.replace("'", "\\'")}');"
                    webView.evaluateJavascript(jsCall) { jsResult ->
                        scope.launch(Dispatchers.IO) {
                            handlePatchResult(workUri, cleanName, jsResult, mdatStart, mdatHeaderSize, mdatDataSize)
                            tempTranscodedFile?.delete() // Cleanup temporary transcode file
                        }
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Lỗi tối ưu hóa: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    webView.evaluateJavascript("onNativeError('${e.localizedMessage?.replace("'", "\\'")}');", null)
                }
            }
        }
    }

    private suspend fun handlePatchResult(
        srcUri: Uri,
        outFileName: String,
        jsResultJsonString: String?,
        mdatStart: Long,
        mdatHeaderSize: Int,
        mdatDataSize: Long
    ) {
        try {
            if (jsResultJsonString == null || jsResultJsonString == "null") {
                throw IllegalStateException("Lỗi thực thi ShoreEngine patcher.")
            }

            // Strips surrounding quotes from evaluateJavascript
            val unquoted = if (jsResultJsonString.startsWith("\"") && jsResultJsonString.endsWith("\"")) {
                val sub = jsResultJsonString.substring(1, jsResultJsonString.length - 1)
                // unescape quotes
                sub.replace("\\\"", "\"").replace("\\\\", "\\")
            } else {
                jsResultJsonString
            }

            val json = JSONObject(unquoted)
            if (!json.optBoolean("success", false)) {
                val errMsg = json.optString("error", "Lỗi xử lý atom")
                throw IllegalStateException(errMsg)
            }

            val dBytes = Base64.decode(json.getString("D"), Base64.DEFAULT)
            val tBytes = Base64.decode(json.getString("T"), Base64.DEFAULT)
            val fBytes = Base64.decode(json.getString("F"), Base64.DEFAULT)
            val mBytes = Base64.decode(json.getString("M"), Base64.DEFAULT)
            val lBytes = Base64.decode(json.getString("L"), Base64.DEFAULT)
            val reportObj = json.getJSONObject("report")

            withContext(Dispatchers.Main) {
                webView.evaluateJavascript("onNativeProgress(30, 'Đang xuất video ra Movies/ShoreUploader...');", null)
            }

            // 4. Create destination in Movies/ShoreUploader
            var destUri: Uri? = null
            var destFile: File? = null
            var outStream: OutputStream? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, outFileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/ShoreUploader")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val itemUri = resolver.insert(collection, values) ?: throw IllegalStateException("Không thể tạo file trong MediaStore")
                outStream = resolver.openOutputStream(itemUri) ?: throw IllegalStateException("Không thể mở luồng ghi MediaStore")
                destUri = itemUri
            } else {
                val moviesDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "ShoreUploader"
                ).apply { if (!exists()) mkdirs() }
                val targetFile = File(moviesDir, outFileName)
                outStream = FileOutputStream(targetFile)
                destFile = targetFile
            }

            // Also prepare cache mirror file for direct FileProvider sharing
            val cacheDir = File(context.cacheDir, "videos").apply { if (!exists()) mkdirs() }
            val cacheTarget = File(cacheDir, outFileName)
            val cacheOut = FileOutputStream(cacheTarget)

            outStream.use { out ->
                cacheOut.use { cOut ->
                    // Write new ftyp
                    out.write(dBytes); cOut.write(dBytes)
                    // Write free box
                    out.write(tBytes); cOut.write(tBytes)
                    // Write new moov (now at the front of the file: faststart!)
                    out.write(fBytes); cOut.write(fBytes)
                    // Write new mdat header
                    out.write(mBytes); cOut.write(mBytes)

                    // 5. Direct Stream Copy of raw mdat video samples (Fast & 0 RAM!)
                    context.contentResolver.openInputStream(srcUri)?.use { srcStream ->
                        skipFully(srcStream, mdatStart + mdatHeaderSize)

                        val buffer = ByteArray(8 * 1024 * 1024) // 8MB native buffer
                        var remaining = mdatDataSize
                        var totalCopied: Long = 0
                        var lastReportTime = System.currentTimeMillis()

                        while (remaining > 0) {
                            val toRead = Math.min(buffer.size.toLong(), remaining).toInt()
                            val bytesRead = srcStream.read(buffer, 0, toRead)
                            if (bytesRead <= 0) break

                            out.write(buffer, 0, bytesRead)
                            cOut.write(buffer, 0, bytesRead)

                            totalCopied += bytesRead
                            remaining -= bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastReportTime > 200) {
                                lastReportTime = now
                                val pct = 30 + Math.round((totalCopied.toDouble() / mdatDataSize) * 65.0).toInt()
                                withContext(Dispatchers.Main) {
                                    webView.evaluateJavascript("onNativeProgress($pct, 'Đang xuất tệp: $pct%...');", null)
                                }
                            }
                        }
                    }

                    // Write ghost sample bytes (8 bytes)
                    out.write(lBytes); cOut.write(lBytes)
                    out.flush(); cOut.flush()
                }
            }

            // 6. Finalize MediaStore entry
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && destUri != null) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                context.contentResolver.update(destUri, values, null, null)
                lastSavedUri = destUri
            } else if (destFile != null) {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    arrayOf("video/mp4"),
                    null
                )
                lastSavedUri = Uri.fromFile(destFile)
                lastSavedFile = destFile
            }
            lastSavedFile = cacheTarget

            withContext(Dispatchers.Main) {
                webView.evaluateJavascript("onNativeProgress(100, 'Hoàn tất!');", null)
                webView.evaluateJavascript("onNativeSuccess('${reportObj.toString().replace("'", "\\'")}', '$outFileName');", null)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Lỗi xuất video: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                webView.evaluateJavascript("onNativeError('${e.localizedMessage?.replace("'", "\\'")}');", null)
            }
        }
    }

    private fun readFully(stream: InputStream, b: ByteArray, len: Int, offset: Int = 0): Int {
        var n = 0
        while (n < len) {
            val count = stream.read(b, offset + n, len - n)
            if (count < 0) break
            n += count
        }
        return n
    }

    private fun skipFully(input: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        val skipBuf = ByteArray(65536)
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                val bytesRead = input.read(skipBuf, 0, Math.min(skipBuf.size.toLong(), remaining).toInt())
                if (bytesRead == -1) break
                remaining -= bytesRead
            } else {
                remaining -= skipped
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

                val tikTokPackages = listOf(
                    "com.ss.android.ugc.trill",      // TikTok Vietnam / SEA
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
                    Toast.makeText(context, "Mở bảng chọn ứng dụng...", Toast.LENGTH_SHORT).show()
                    val chooser = Intent.createChooser(intent, "Chia sẻ video tới TikTok:")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Lỗi mở TikTok: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

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

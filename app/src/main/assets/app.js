// Shore Uploader Mobile Application Controller v1.0.0
document.addEventListener("DOMContentLoaded", () => {
    // Elements
    const fileInput = document.getElementById("fileInput");
    const dropzone = document.getElementById("dropzone");
    const btnBrowse = document.getElementById("btnBrowse");
    const videoMetaCard = document.getElementById("videoMetaCard");
    const videoPreview = document.getElementById("videoPreview");

    // Metadata labels
    const metaName = document.getElementById("metaName");
    const metaSize = document.getElementById("metaSize");
    const metaDuration = document.getElementById("metaDuration");
    const metaResolution = document.getElementById("metaResolution");
    const metaFps = document.getElementById("metaFps");
    const metaCodec = document.getElementById("metaCodec");

    // Steppers & Panels
    const stepNav1 = document.getElementById("step-nav-1");
    const stepNav2 = document.getElementById("step-nav-2");
    const stepNav3 = document.getElementById("step-nav-3");
    const line1 = document.getElementById("line-1");
    const line2 = document.getElementById("line-2");

    const stepPanel1 = document.getElementById("step-1");
    const stepPanel2 = document.getElementById("step-2");
    const stepPanel3 = document.getElementById("step-3");

    // Buttons
    const btnProceedToStep2 = document.getElementById("btnProceedToStep2");
    const btnBackToStep1 = document.getElementById("btnBackToStep1");
    const btnStartOptimize = document.getElementById("btnStartOptimize");
    const step2Actions = document.getElementById("step2Actions");
    const processingBox = document.getElementById("processingBox");
    const progressFill = document.getElementById("progressFill");
    const processStageTitle = document.getElementById("processStageTitle");
    const processStageSubtitle = document.getElementById("processStageSubtitle");
    const processLog = document.getElementById("processLog");

    // Advanced options
    const chkGhost = document.getElementById("chkGhost");
    const chkBt709 = document.getElementById("chkBt709");
    const chkHdr = document.getElementById("chkHdr");
    const txtHandler = document.getElementById("txtHandler");

    // Step 3 Reports & Buttons
    const repOutputSize = document.getElementById("repOutputSize");
    const repElapsed = document.getElementById("repElapsed");
    const repGhosts = document.getElementById("repGhosts");
    const repColor = document.getElementById("repColor");
    const savedFilePathNotice = document.getElementById("savedFilePathNotice");

    const btnShareTikTok = document.getElementById("btnShareTikTok");
    const btnOpenVideo = document.getElementById("btnOpenVideo");
    const btnShareGeneric = document.getElementById("btnShareGeneric");
    const btnResetAll = document.getElementById("btnResetAll");

    // State
    let currentFile = null;
    let processedBlob = null;
    let detectedFps = null;
    let outputFileName = "";

    // Helper: Haptic feedback
    function haptic() {
        if (window.AndroidBridge && window.AndroidBridge.triggerHaptic) {
            window.AndroidBridge.triggerHaptic();
        }
    }

    // Helper: Format bytes
    function formatBytes(bytes) {
        if (!bytes || bytes === 0) return "0 B";
        if (bytes >= 1e9) return (bytes / 1e9).toFixed(2) + " GB";
        if (bytes >= 1e6) return (bytes / 1e6).toFixed(1) + " MB";
        return (bytes / 1e3).toFixed(1) + " KB";
    }

    // Helper: Format duration
    function formatDuration(seconds) {
        if (!seconds || isNaN(seconds)) return "--";
        const mins = Math.floor(seconds / 60);
        const secs = Math.floor(seconds % 60);
        return `${mins}:${secs < 10 ? "0" : ""}${secs}`;
    }

    // Helper: ArrayBuffer to Base64 in 32KB chunks
    function arrayBufferToBase64(buffer) {
        let binary = '';
        const bytes = new Uint8Array(buffer);
        const len = bytes.byteLength;
        const chunkSize = 0x8000;
        for (let i = 0; i < len; i += chunkSize) {
            const chunk = bytes.subarray(i, Math.min(i + chunkSize, len));
            binary += String.fromCharCode.apply(null, chunk);
        }
        return window.btoa(binary);
    }

    // Helper: Stream large blob in 4MB chunks to Android native with live progress bar
    async function streamBlobToAndroid(blob, target, fileName, onProgress) {
        if (!window.AndroidBridge || !window.AndroidBridge.startChunkedStream) return false;
        const streamId = "s_" + Date.now();
        const ok = window.AndroidBridge.startChunkedStream(streamId, fileName, target);
        if (!ok) return false;

        const CHUNK_SIZE = 4 * 1024 * 1024; // 4MB chunks
        const total = blob.size;
        for (let offset = 0; offset < total; offset += CHUNK_SIZE) {
            const slice = blob.slice(offset, Math.min(offset + CHUNK_SIZE, total));
            const buf = await slice.arrayBuffer();
            const b64 = arrayBufferToBase64(buf);
            window.AndroidBridge.appendStreamChunk(streamId, b64);

            const sent = Math.min(offset + slice.size, total);
            const pct = Math.min(99, Math.round((sent / total) * 100));
            if (onProgress) {
                onProgress(pct, sent, total);
            }
            await new Promise(r => setTimeout(r, 6));
        }
        window.AndroidBridge.finishChunkedStream(streamId, target, fileName, "onSaveFinished");
        if (onProgress) {
            onProgress(100, total, total);
        }
        return true;
    }

    // Helper: Read file with real live progress bar
    function readFileWithProgress(file, onProgress) {
        return new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onprogress = (e) => {
                if (e.lengthComputable && e.total > 0) {
                    const pct = Math.round((e.loaded / e.total) * 100);
                    onProgress(pct, e.loaded, e.total);
                }
            };
            reader.onload = () => resolve(reader.result);
            reader.onerror = () => reject(reader.error || new Error("Không thể đọc tệp từ bộ nhớ."));
            reader.readAsArrayBuffer(file);
        });
    }

    // Step Navigation
    function goToStep(step) {
        haptic();
        [stepPanel1, stepPanel2, stepPanel3].forEach(p => p.classList.add("hidden"));
        stepNav1.classList.remove("active", "completed");
        stepNav2.classList.remove("active", "completed");
        stepNav3.classList.remove("active", "completed");
        line1.classList.remove("active");
        line2.classList.remove("active");

        if (step === 1) {
            stepPanel1.classList.remove("hidden");
            stepNav1.classList.add("active");
        } else if (step === 2) {
            stepPanel2.classList.remove("hidden");
            stepNav1.classList.add("completed");
            stepNav2.classList.add("active");
            line1.classList.add("active");
            processingBox.classList.add("hidden");
            step2Actions.classList.remove("hidden");
        } else if (step === 3) {
            stepPanel3.classList.remove("hidden");
            stepNav1.classList.add("completed");
            stepNav2.classList.add("completed");
            stepNav3.classList.add("active");
            line1.classList.add("active");
            line2.classList.add("active");
        }
        window.scrollTo({ top: 0, behavior: "smooth" });
    }

    // File Selection Handlers
    dropzone.addEventListener("click", () => {
        haptic();
        fileInput.click();
    });

    btnBrowse.addEventListener("click", (e) => {
        e.stopPropagation();
        haptic();
        fileInput.click();
    });

    dropzone.addEventListener("dragover", (e) => {
        e.preventDefault();
        dropzone.classList.add("dragover");
    });

    dropzone.addEventListener("dragleave", () => {
        dropzone.classList.remove("dragover");
    });

    dropzone.addEventListener("drop", (e) => {
        e.preventDefault();
        dropzone.classList.remove("dragover");
        if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
            handleFile(e.dataTransfer.files[0]);
        }
    });

    fileInput.addEventListener("change", (e) => {
        if (e.target.files && e.target.files.length > 0) {
            handleFile(e.target.files[0]);
        }
    });

    async function handleFile(file) {
        currentFile = file;
        outputFileName = file.name.replace(/\.[^/.]+$/, "") + "_shore_tiktok.mp4";

        metaName.textContent = file.name;
        metaSize.textContent = formatBytes(file.size);
        metaDuration.textContent = "Đang kiểm tra...";
        metaResolution.textContent = "Đang kiểm tra...";
        metaFps.textContent = "Đang kiểm tra...";

        videoMetaCard.classList.remove("hidden");

        const objectUrl = URL.createObjectURL(file);
        videoPreview.src = objectUrl;

        videoPreview.onloadedmetadata = () => {
            metaDuration.textContent = formatDuration(videoPreview.duration);
            metaResolution.textContent = `${videoPreview.videoWidth} x ${videoPreview.videoHeight}`;
        };

        // Probe FPS using only the first 5MB slice (Instant - prevents memory freeze on large files)
        try {
            const probeSlice = await file.slice(0, 5 * 1024 * 1024).arrayBuffer();
            if (window.ShoreEngine && window.ShoreEngine.getFps) {
                const fps = window.ShoreEngine.getFps(probeSlice);
                if (fps !== null) {
                    detectedFps = fps;
                    metaFps.textContent = `${fps.toFixed(1)} fps (Chuẩn xác)`;
                } else {
                    metaFps.textContent = "MP4 Chuẩn";
                }
            } else {
                metaFps.textContent = "MP4 Chuẩn";
            }
        } catch (err) {
            metaFps.textContent = "MP4 Native";
        }
    }

    btnProceedToStep2.addEventListener("click", () => {
        if (!currentFile) return;
        goToStep(2);
    });

    btnBackToStep1.addEventListener("click", () => {
        goToStep(1);
    });

    // Native Bridge Callbacks
    window.onNativeFileReady = (name, size, codecInfo) => {
        metaName.textContent = name;
        metaSize.textContent = formatBytes(size);
        if (metaCodec) {
            metaCodec.textContent = codecInfo || "Tự động nhận diện";
        }
        videoMetaCard.classList.remove("hidden");
        outputFileName = name.replace(/\.[^/.]+$/, "") + "_shore_tiktok.mp4";
        if (!currentFile) {
            currentFile = { name: name, size: size };
        }
    };

    window.onNativeProgress = (pct, log) => {
        progressFill.style.width = pct + "%";
        processStageTitle.textContent = `Đang xử lý: ${pct}%`;
        processStageSubtitle.textContent = log;
        processLog.textContent = log;
    };

    window.onNativeSuccess = (reportJsonString, outFileName) => {
        haptic();
        try {
            const report = JSON.parse(reportJsonString);
            repOutputSize.textContent = `${formatBytes(report.outputSize)} (${report.sizeDelta >= 0 ? "+" : ""}${formatBytes(report.sizeDelta)})`;
            repGhosts.textContent = (report.ghostSamples || 0).toLocaleString();
            repColor.textContent = report.dynamicRange || "BT.709";
            repElapsed.textContent = "Hoàn tất";
        } catch (_e) {
            repOutputSize.textContent = formatBytes(currentFile ? currentFile.size : 0);
        }
        if (savedFilePathNotice) {
            savedFilePathNotice.textContent = "Đã lưu tại: Movies/ShoreUploader/" + outFileName;
        }
        goToStep(3);
    };

    window.onNativeError = (errMsg) => {
        step2Actions.classList.remove("hidden");
        processingBox.classList.add("hidden");
        alert("Lỗi khi tối ưu hóa: " + (errMsg || "Không xác định"));
    };

    // STEP 2: RUN OPTIMIZATION & RENDER DIRECTLY TO DISK
    btnStartOptimize.addEventListener("click", async () => {
        if (!currentFile) {
            alert("Vui lòng chọn video trước!");
            return;
        }

        haptic();
        step2Actions.classList.add("hidden");
        processingBox.classList.remove("hidden");

        const updateProgress = (pct, title, sub, log) => {
            progressFill.style.width = pct + "%";
            if (title) processStageTitle.textContent = title;
            if (sub) processStageSubtitle.textContent = sub;
            if (log) processLog.textContent = log;
        };

        const opts = {
            ghostSamples: chkGhost.checked ? 9112 : 0,
            forceHdr: chkHdr.checked,
            videoHandler: txtHandler.value.trim() || "shoreuploader-coded",
            audioHandler: txtHandler.value.trim() || "shoreuploader-coded"
        };

        // If running in Android App: Use zero-RAM streaming pipeline (Fastest, handles 3GB, 5GB, 10GB+)
        if (window.AndroidBridge && window.AndroidBridge.startNativeOptimization) {
            updateProgress(5, "Đang tối ưu video...", "Đang đọc cấu trúc MP4 box...", "Bắt đầu streaming zero-RAM...");
            window.AndroidBridge.startNativeOptimization(JSON.stringify(opts));
            return;
        }

        // Web browser fallback
        updateProgress(0, "Đang nạp video...", "Đang đọc dữ liệu từ bộ nhớ...", "Bắt đầu đọc (" + formatBytes(currentFile.size) + ")...");

        try {
            // [1/4] Read file with live progress (0% -> 40%)
            const arrayBuffer = await readFileWithProgress(currentFile, (pct, loaded, total) => {
                const scaledPct = Math.round(pct * 0.40);
                updateProgress(
                    scaledPct,
                    `Đang nạp video: ${pct}%`,
                    `Đã nạp ${formatBytes(loaded)} / ${formatBytes(total)}`,
                    `[1/4] Đang đọc file từ máy...`
                );
            });

            // [2/4] MP4 Box parsing & optimization (40% -> 65%)
            updateProgress(50, "Đang phân tích cấu trúc MP4...", "Kiểm tra container và tracks...", "[2/4] Quét cấu trúc atom...");
            await new Promise(r => setTimeout(r, 50));

            updateProgress(60, "Tối ưu hóa Container...", "Gắn thẻ màu BT.709 & chèn 9,112 Ghost Samples...", "[3/4] Cấu trúc lại MP4 Box...");
            await new Promise(r => setTimeout(r, 50));

            const t0 = performance.now();
            const result = window.ShoreEngine.patchMp4(arrayBuffer, opts);
            const elapsed = performance.now() - t0;

            // Assemble final blob
            processedBlob = new Blob(result.parts, { type: "video/mp4" });
            const finalSize = processedBlob.size;

            // [3/4] RENDER & WRITE DIRECTLY TO DISK (65% -> 98%)
            updateProgress(70, "Đang xuất tệp ra máy...", "Lưu trực tiếp vào Movies/ShoreUploader...", "[4/4] Đang ghi file...");

            if (window.AndroidBridge && window.AndroidBridge.startChunkedStream) {
                await streamBlobToAndroid(processedBlob, "gallery", outputFileName, (pct, sent, total) => {
                    const scaledWritePct = 70 + Math.round(pct * 0.28);
                    updateProgress(
                        scaledWritePct,
                        `Đang lưu file: ${pct}%`,
                        `Đang xuất ra Movies/ShoreUploader (${formatBytes(sent)} / ${formatBytes(total)})`,
                        `[4/4] Ghi file vào bộ nhớ máy: ${pct}%...`
                    );
                });
            } else {
                updateProgress(90, "Đang hoàn tất tệp...", "Đóng gói MP4...", "Xong!");
                await new Promise(r => setTimeout(r, 100));
            }

            // Update Step 3 reports
            repOutputSize.textContent = `${formatBytes(finalSize)} (${result.report.sizeDelta >= 0 ? "+" : ""}${formatBytes(result.report.sizeDelta)})`;
            repElapsed.textContent = `${elapsed.toFixed(0)} ms`;
            repGhosts.textContent = opts.ghostSamples.toLocaleString();
            repColor.textContent = opts.forceHdr ? "HDR10" : "BT.709 nclx";
            if (savedFilePathNotice) {
                savedFilePathNotice.textContent = "Đã lưu tại: Movies/ShoreUploader/" + outputFileName;
            }

            updateProgress(100, "Đã xuất xong!", "Video đã sẵn sàng trên máy", "Hoàn tất trong " + elapsed.toFixed(0) + "ms!");
            await new Promise(r => setTimeout(r, 150));

            goToStep(3);

        } catch (err) {
            console.error("Optimization failed:", err);
            step2Actions.classList.remove("hidden");
            processingBox.classList.add("hidden");
            alert("Lỗi khi tối ưu hóa video: " + (err.message || err));
        }
    });

    // STEP 3: CAPCUT-STYLE DIRECT ACTIONS
    btnShareTikTok.addEventListener("click", () => {
        haptic();
        if (window.AndroidBridge && window.AndroidBridge.openTikTokPost) {
            // Jumps directly into TikTok's video publishing screen!
            window.AndroidBridge.openTikTokPost();
        } else {
            alert("Tính năng nhảy thẳng vào màn hình đăng TikTok khả dụng khi cài app trên điện thoại!");
        }
    });

    btnOpenVideo.addEventListener("click", () => {
        haptic();
        if (window.AndroidBridge && window.AndroidBridge.openSavedVideo) {
            window.AndroidBridge.openSavedVideo();
        } else if (processedBlob) {
            const url = URL.createObjectURL(processedBlob);
            window.open(url);
        }
    });

    btnShareGeneric.addEventListener("click", () => {
        haptic();
        if (window.AndroidBridge && window.AndroidBridge.shareGeneric) {
            window.AndroidBridge.shareGeneric();
        } else if (processedBlob) {
            const url = URL.createObjectURL(processedBlob);
            const a = document.createElement("a");
            a.href = url;
            a.download = outputFileName;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
        }
    });

    btnResetAll.addEventListener("click", () => {
        haptic();
        currentFile = null;
        processedBlob = null;
        fileInput.value = "";
        videoPreview.src = "";
        videoMetaCard.classList.add("hidden");
        goToStep(1);
    });

    window.onSaveFinished = (success, info) => {
        if (success) {
            console.log("File saved to device:", info);
        }
    };
});

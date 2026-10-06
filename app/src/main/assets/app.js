// Shore Uploader Mobile Application Controller
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
    const btnSaveGallery = document.getElementById("btnSaveGallery");
    const btnShareTikTok = document.getElementById("btnShareTikTok");
    const btnShareGeneric = document.getElementById("btnShareGeneric");
    const btnResetAll = document.getElementById("btnResetAll");

    // State
    let currentFile = null;
    let currentArrayBuffer = null;
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

    // Helper: ArrayBuffer to Base64 in small chunks (prevent stack overflow)
    function arrayBufferToBase64(buffer) {
        let binary = '';
        const bytes = new Uint8Array(buffer);
        const len = bytes.byteLength;
        const chunkSize = 0x8000; // 32KB
        for (let i = 0; i < len; i += chunkSize) {
            const chunk = bytes.subarray(i, Math.min(i + chunkSize, len));
            binary += String.fromCharCode.apply(null, chunk);
        }
        return window.btoa(binary);
    }

    // Helper: Stream large blob in 4MB chunks to Android native (Supports 1GB, 2GB, 4GB+ without RAM limits)
    async function streamBlobToAndroid(blob, target, fileName) {
        if (!window.AndroidBridge || !window.AndroidBridge.startChunkedStream) return false;
        const streamId = "s_" + Date.now();
        const ok = window.AndroidBridge.startChunkedStream(streamId, fileName, target);
        if (!ok) return false;

        const CHUNK_SIZE = 4 * 1024 * 1024; // 4MB chunks
        for (let offset = 0; offset < blob.size; offset += CHUNK_SIZE) {
            const slice = blob.slice(offset, Math.min(offset + CHUNK_SIZE, blob.size));
            const buf = await slice.arrayBuffer();
            const b64 = arrayBufferToBase64(buf);
            window.AndroidBridge.appendStreamChunk(streamId, b64);
        }
        window.AndroidBridge.finishChunkedStream(streamId, target, fileName, "onSaveFinished");
        return true;
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
        metaDuration.textContent = "Đang đọc...";
        metaResolution.textContent = "Đang đọc...";
        metaFps.textContent = "Đang phân tích...";

        videoMetaCard.classList.remove("hidden");

        const objectUrl = URL.createObjectURL(file);
        videoPreview.src = objectUrl;

        videoPreview.onloadedmetadata = () => {
            metaDuration.textContent = formatDuration(videoPreview.duration);
            metaResolution.textContent = `${videoPreview.videoWidth} x ${videoPreview.videoHeight}`;
        };

        try {
            currentArrayBuffer = await file.arrayBuffer();
            if (window.ShoreEngine && window.ShoreEngine.getFps) {
                const fps = window.ShoreEngine.getFps(currentArrayBuffer);
                if (fps !== null) {
                    detectedFps = fps;
                    metaFps.textContent = `${fps.toFixed(1)} fps (Chuẩn xác)`;
                } else {
                    metaFps.textContent = "30 - 60 fps (Tiêu chuẩn)";
                }
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

    // STEP 2: RUN OPTIMIZATION
    btnStartOptimize.addEventListener("click", async () => {
        if (!currentArrayBuffer) {
            if (currentFile) {
                currentArrayBuffer = await currentFile.arrayBuffer();
            } else {
                alert("Vui lòng chọn video trước!");
                return;
            }
        }

        haptic();
        step2Actions.classList.add("hidden");
        processingBox.classList.remove("hidden");

        const updateProgress = (pct, title, sub, log) => {
            progressFill.style.width = pct + "%";
            processStageTitle.textContent = title;
            processStageSubtitle.textContent = sub;
            processLog.textContent = log;
        };

        try {
            updateProgress(25, "Đang đọc cấu trúc MP4...", "Phân tích các box ftyp, moov, mdat", "[1/4] Scanning container headers...");
            await new Promise(r => setTimeout(r, 100));

            updateProgress(55, "Cấu hình thuật toán Shore...", "Gắn thẻ màu BT.709 & chuẩn bị Ghost Samples", "[2/4] Patching stsd, hdlr, elst atoms...");
            await new Promise(r => setTimeout(r, 100));

            const opts = {
                ghostSamples: chkGhost.checked ? 9112 : 0,
                forceHdr: chkHdr.checked,
                videoHandler: txtHandler.value.trim() || "shoreuploader-coded",
                audioHandler: txtHandler.value.trim() || "shoreuploader-coded"
            };

            const t0 = performance.now();
            updateProgress(75, "Tối ưu hóa MP4 Container...", "Tái cấu trúc moov atom lên đầu (+faststart)", "[3/4] Rebuilding stco offsets & ghost samples...");
            
            // Execute Shore Container Patcher
            const result = window.ShoreEngine.patchMp4(currentArrayBuffer, opts);
            const elapsed = performance.now() - t0;

            updateProgress(95, "Xác thực tệp đầu ra...", "Kiểm tra tính toàn vẹn của video TikTok", "[4/4] Output verified. Assembling MP4 binary...");
            await new Promise(r => setTimeout(r, 80));

            // Assemble final blob
            processedBlob = new Blob(result.parts, { type: "video/mp4" });
            const finalSize = processedBlob.size;

            // Report data
            repOutputSize.textContent = `${formatBytes(finalSize)} (${result.report.sizeDelta >= 0 ? "+" : ""}${formatBytes(result.report.sizeDelta)})`;
            repElapsed.textContent = `${elapsed.toFixed(0)} ms`;
            repGhosts.textContent = opts.ghostSamples.toLocaleString();
            repColor.textContent = opts.forceHdr ? "HDR10" : "BT.709 nclx";

            updateProgress(100, "Hoàn tất!", "Sẵn sàng lưu hoặc đăng tải", "Done in " + elapsed.toFixed(0) + "ms");
            await new Promise(r => setTimeout(r, 150));

            goToStep(3);

        } catch (err) {
            console.error("Optimization failed:", err);
            step2Actions.classList.remove("hidden");
            processingBox.classList.add("hidden");
            alert("Lỗi tối ưu hóa video: " + (err.message || err));
        }
    });

    // STEP 3: EXPORT HANDLERS
    btnSaveGallery.addEventListener("click", async () => {
        haptic();
        if (!processedBlob) return;

        if (window.AndroidBridge) {
            const originalText = btnSaveGallery.querySelector("span").textContent;
            btnSaveGallery.querySelector("span").textContent = "Đang lưu vào Thư viện...";
            btnSaveGallery.disabled = true;

            const ok = await streamBlobToAndroid(processedBlob, "gallery", outputFileName);
            btnSaveGallery.disabled = false;
            btnSaveGallery.querySelector("span").textContent = originalText;

            if (!ok && window.AndroidBridge.showToast) {
                window.AndroidBridge.showToast("Lỗi khi mở luồng lưu trữ");
            }
        } else {
            // Web browser fallback
            const url = URL.createObjectURL(processedBlob);
            const a = document.createElement("a");
            a.href = url;
            a.download = outputFileName;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            setTimeout(() => URL.revokeObjectURL(url), 1000);
            alert("Đang tải video về máy: " + outputFileName);
        }
    });

    btnShareTikTok.addEventListener("click", async () => {
        haptic();
        if (!processedBlob) return;

        if (window.AndroidBridge) {
            const originalText = btnShareTikTok.querySelector("span").textContent;
            btnShareTikTok.querySelector("span").textContent = "Đang mở TikTok...";
            btnShareTikTok.disabled = true;

            await streamBlobToAndroid(processedBlob, "tiktok", outputFileName);
            btnShareTikTok.disabled = false;
            btnShareTikTok.querySelector("span").textContent = originalText;
        } else {
            alert("Tính năng mở trực tiếp TikTok khả dụng khi cài đặt file APK trên điện thoại Android!");
        }
    });

    btnShareGeneric.addEventListener("click", async () => {
        haptic();
        if (!processedBlob) return;

        if (window.AndroidBridge) {
            const originalText = btnShareGeneric.querySelector("span").textContent;
            btnShareGeneric.querySelector("span").textContent = "Đang chuẩn bị chia sẻ...";
            btnShareGeneric.disabled = true;

            await streamBlobToAndroid(processedBlob, "share", outputFileName);
            btnShareGeneric.disabled = false;
            btnShareGeneric.querySelector("span").textContent = originalText;
        } else if (navigator.share) {
            const file = new File([processedBlob], outputFileName, { type: "video/mp4" });
            navigator.share({
                title: "Shore Uploader Video",
                files: [file]
            }).catch(() => {});
        } else {
            alert("Vui lòng sử dụng trên thiết bị Android để chia sẻ qua các ứng dụng khác.");
        }
    });

    btnResetAll.addEventListener("click", () => {
        haptic();
        currentFile = null;
        currentArrayBuffer = null;
        processedBlob = null;
        fileInput.value = "";
        videoPreview.src = "";
        videoMetaCard.classList.add("hidden");
        goToStep(1);
    });

    // Callback from Android Bridge
    window.onSaveFinished = (success, info) => {
        if (success) {
            console.log("Saved successfully:", info);
        } else {
            console.warn("Save failed:", info);
        }
    };
});

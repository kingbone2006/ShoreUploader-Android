<div align="center">

<img src="app/src/main/assets/logo.png" alt="Shore Uploader Logo" width="100" style="border-radius: 20px; box-shadow: 0 4px 16px rgba(254, 44, 85, 0.3);" />

# 🎬 Shore Uploader for Android

**Ứng dụng tối ưu hóa & Giữ nguyên chất lượng video đăng tải lên TikTok cho Android**  
*Lossless Video Container Optimizer & Quality Preservation Tool for TikTok on Android*

---

[![GitHub Release](https://img.shields.io/github/v/release/kingbone2006/ShoreUploader-Android?style=for-the-badge&color=fe2c55&logo=github)](https://github.com/kingbone2006/ShoreUploader-Android/releases)
[![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024%2B)-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/kingbone2006/ShoreUploader-Android)
[![Kotlin](https://img.shields.io/badge/Kotlin-AndroidX%20Media3-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://developer.android.com/)
[![Ko-fi](https://img.shields.io/badge/Ko--fi-Support-FF5E5B?style=for-the-badge&logo=ko-fi&logoColor=white)](https://ko-fi.com/kingbone2006)
[![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)](LICENSE)

<br/>

[![Moe Visitor Counter](https://count.getloli.com/@kingbone2006-ShoreUploader-Android)](https://github.com/kingbone2006/ShoreUploader-Android)

<br/>

### 🚀 TẢI XUỐNG FILE CÀI ĐẶT APK / DOWNLOAD APK

[![Direct Download APK](https://img.shields.io/badge/TẢI_XUỐNG_NGAY_(DIRECT_DOWNLOAD)-ShoreUploader.apk-brightgreen?style=for-the-badge&logo=android&logoColor=white)](https://raw.githubusercontent.com/kingbone2006/ShoreUploader-Android/main/dist_apk/ShoreUploader-APK/app-debug.apk)

[📦 **Xem tất cả bản phát hành (All Releases)**](https://github.com/kingbone2006/ShoreUploader-Android/releases)

---

**[🇻🇳 Tiếng Việt](#-tiếng-việt)** | **[🇬🇧 English](#-english)**

</div>

---

# 🇻🇳 Tiếng Việt

## 📖 Giới thiệu
**Shore Uploader for Android** là ứng dụng di động mã nguồn mở giúp tối ưu hóa cấu trúc tệp video trước khi đăng tải lên **TikTok**, loại bỏ triệt để hiện tượng bị nén mờ, vỡ hạt và bạc màu (washed out).

Được kế thừa và phát triển từ ý tưởng của **phiên bản website tiện ích ShoreUploader** ([shoreuploader.com](https://shoreuploader.com)), phiên bản Android mang toàn bộ sức mạnh tối ưu hóa container về một ứng dụng di động độc lập, nhanh chóng và hoạt động hoàn toàn offline.

Khi bạn sáng tạo và dựng video bằng các phần mềm như **CapCut, Adobe Premiere, DaVinci Resolve**, thuật toán của TikTok thường tự động nén lần 2 (secondary re-encoding), ép bitrate xuống rất thấp (khoảng 2 – 3 Mbps) và làm lệch không gian màu hiển thị trên các thiết bị khác nhau. **Shore Uploader** tích hợp lõi xử lý **ShoreEngine v2.0** chuyên biệt, can thiệp vào cấu trúc container MP4 (Box / Atom) mà **không làm suy hao chất lượng hình ảnh (lossless patch)**. Bằng cách chèn **Ghost Samples**, chuẩn hóa **Metadata BT.709 Explicit**, và dịch chuyển **Moov Atom (+faststart)**, video sẽ kích hoạt cơ chế passthrough ít nén hơn của máy chủ TikTok, giúp giữ nguyên độ sắc nét ban đầu.

---

## ✨ Tính năng nổi bật

### 1. 🎨 Giữ nguyên màu sắc trung thực (BT.709 Color Tagged & Force HDR10)
* Gắn trực tiếp thẻ định danh màu chuẩn **BT.709 Explicit (`colr` nclx)** vào container MP4.
* Khắc phục hoàn toàn lỗi video bị nhạt màu, xỉn màu hoặc rửa màu khi xem trên các dòng màn hình điện thoại khác nhau.
* Hỗ trợ chế độ tuỳ chọn **Force HDR10 metadata** (`mdcv`, `clli`) cho các nội dung dải động cao.

### 2. 👻 Kỹ thuật đánh lừa bộ nén với Ghost Samples (9,112 mẫu)
* Bổ sung **9,112 ghost samples** vào bảng kích thước mẫu (`stsz`, `stsc`).
* Khiến hệ thống kiểm duyệt và mã hóa của TikTok nhận diện video đã được chuẩn hóa bởi container cao cấp, từ đó áp dụng thuật toán passthrough ít nén hơn, bảo vệ chi tiết khung hình ngay cả trong các phân cảnh chuyển động nhanh (motion blur, dancing, action).

### 3. ⚡ Tối ưu phát tức thì với Moov Atom (+faststart)
* Tự động sắp xếp lại cấu trúc container (`ftyp` $\to$ `moov` $\to$ `mdat`), đưa siêu dữ liệu chỉ mục video lên đầu file.
* Giúp video tải mượt mà ngay lập tức khi người xem lướt đến trên TikTok feed, loại bỏ hoàn toàn độ trễ buffering.

### 4. 🔄 Tích hợp bộ chuyển đổi phần cứng Google Media3 Transformer
* Tự động nhận diện định dạng codec của video đầu vào.
* Đối với các tệp cần chuẩn hóa tương thích sang H.264/AAC, ứng dụng kích hoạt tăng tốc phần cứng thông qua thư viện **AndroidX Media3 Transformer** trước khi nạp vào bộ vá ShoreEngine.

### 5. 📲 1-Chạm đăng trực tiếp TikTok & Tự động lưu Thư viện
* Tích hợp nút **ĐĂNG TRỰC TIẾP LÊN TIKTOK** (hỗ trợ TikTok Global, TikTok US/Asia, TikTok Lite và Douyin).
* Tự động lưu video hoàn thiện vào thư mục lưu trữ `Movies/ShoreUploader/`.
* Tích hợp `MediaScannerConnection` giúp video hiển thị ngay lập tức trong Bộ sưu tập (Gallery / Google Photos) mà không cần quét lại thủ công.
* Dễ dàng chia sẻ sang các ứng dụng khác như Zalo, Google Drive, Telegram, Facebook,...

### 6. 🔒 100% Cục bộ & Bảo mật dữ liệu (Privacy First)
* Mọi thao tác xử lý video đều diễn ra hoàn toàn offline trên phần cứng thiết bị của bạn.
* Không gửi bất kỳ dữ liệu, hình ảnh hay video nào lên server bên ngoài, đảm bảo an toàn tuyệt đối.

---

## 📱 Yêu cầu hệ thống
* **Hệ điều hành:** Android 7.0 (Nougat, API 24) trở lên (Tương thích tốt nhất từ Android 10 đến Android 15).
* **Quyền hạn cần cấp:** 
  * Quyền đọc ảnh và video (`READ_MEDIA_VIDEO` trên Android 13+ hoặc `READ_EXTERNAL_STORAGE` trên Android cũ).
* **Định dạng video khuyến nghị:** MP4 với video codec H.264 (AVC) và audio AAC (FPS $\le$ 120 fps).

---

## 📥 Cài đặt & Hướng dẫn sử dụng

### 1. Tải về file cài đặt APK
* 👉 [**Tải xuống file APK trực tiếp (ShoreUploader.apk)**](https://raw.githubusercontent.com/kingbone2006/ShoreUploader-Android/main/dist_apk/ShoreUploader-APK/app-debug.apk)
* 📦 [**Xem tất cả bản phát hành trên GitHub Releases**](https://github.com/kingbone2006/ShoreUploader-Android/releases)

### 2. Cài đặt
1. Mở file `.apk` vừa tải về trên điện thoại.
2. Nếu hệ điều hành hỏi, hãy cho phép **"Cài đặt ứng dụng từ nguồn không xác định"** (Install unknown apps) cho trình duyệt hoặc trình quản lý tệp.
3. Nhấn **Cài đặt** và mở ứng dụng sau khi hoàn tất.

### 3. Hướng dẫn sử dụng (3 bước đơn giản)
1. **Bước 1 - Chọn Video:** Nhấn nút **Chọn Video Cần Tối Ưu** và chọn video bạn vừa xuất từ CapCut, Premiere,...
2. **Bước 2 - Thiết lập cấu hình:**
   * Xem trước thông số video: Độ phân giải, FPS, Thời lượng, Kích thước tệp.
   * *(Tùy chọn nâng cao)*: Điều chỉnh số lượng Ghost Samples (mặc định: 9,112), bật Force HDR10 hoặc đổi tên Encoder Handler.
   * Nhấn **Bắt Đầu Tối Ưu**.
3. **Bước 3 - Xuất & Đăng tải:**
   * Sau khi thanh tiến trình đạt 100%, nhấn **ĐĂNG TRỰC TIẾP LÊN TIKTOK** để mở trình tải video của TikTok.
   * Video tối ưu đã được lưu sẵn tại thư mục `Movies/ShoreUploader/` trên máy của bạn.

---

## ☕ Ủng hộ dự án (Donate)

Nếu bạn thấy Shore Uploader hữu ích và giúp video TikTok của bạn sắc nét hơn, hãy ủng hộ và mời tôi 1 ly cà phê nhé:

<div align="center">

<img src="assets/donate_qr.jpg" alt="Mã QR Donate" width="280" style="border-radius: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.15);" />

<br/>

*Cảm ơn sự đồng hành và ủng hộ của các bạn! ❤️*

</div>

---

<br/>

# 🇬🇧 English

## 📖 Overview
**Shore Uploader for Android** is an open-source mobile application designed to optimize video file container architecture before uploading to **TikTok**, effectively preventing brutal re-compression, blurry playback, pixelation, and washed-out colors.

Ported and inspired by the innovative concept of the **ShoreUploader Web tool** ([shoreuploader.com](https://shoreuploader.com)), this Android version brings the full power of MP4 container optimization directly onto your mobile device as a native, lightning-fast, and completely offline utility.

When exporting videos from editing suites such as **CapCut, Adobe Premiere Pro, or DaVinci Resolve**, TikTok's transcoding servers frequently downscale the video bitrate down to ~2–3 Mbps and introduce color gamut misalignments across different mobile panels. **Shore Uploader** incorporates the high-efficiency **ShoreEngine v2.0** container patcher that works directly on MP4 atoms/boxes **without degrading visual fidelity (lossless patching)**. By injecting **Ghost Samples**, assigning **Explicit BT.709 Color Metadata**, and relocating the **Moov Atom (+faststart)** to the front of the file, TikTok is tricked into using an enhanced passthrough pipeline with minimal compression.

---

## ✨ Key Features

### 1. 🎨 Preserved Color Fidelity (BT.709 Color Tagged & HDR10)
* Injects **BT.709 Explicit (`colr` nclx)** metadata tags directly into the MP4 container.
* Eliminates washed-out and dull colors when viewed on diverse smartphone displays (AMOLED, IPS).
* Optional toggle to **Force HDR10 metadata** (`mdcv`, `clli`) for high dynamic range mastering.

### 2. 👻 Ghost Samples Anti-Compression Trick (9,112 samples)
* Injects **9,112 ghost samples** into sample size tables (`stsz`, `stsc`).
* Signals TikTok's ingest engine that the stream has been produced by an enterprise-grade container profile, prompting a gentler transcoding pass with preserved fine details during fast-motion sequences.

### 3. ⚡ Zero Buffering with Faststart Moov Atom
* Relocates container structure (`ftyp` $\to$ `moov` $\to$ `mdat`), placing index metadata ahead of video payload.
* Videos start playback immediately without initial buffering delays when users scroll onto them in their feed.

### 4. 🔄 AndroidX Media3 Transformer Engine
* Automatic codec and compatibility detection.
* For videos requiring standardization to H.264/AAC, the application utilizes hardware-accelerated transcoding powered by **Google AndroidX Media3 Transformer** before passing through ShoreEngine.

### 5. 📲 1-Tap Direct TikTok Upload & Gallery Integration
* Built-in **DIRECT POST TO TIKTOK** trigger (supports TikTok Global, Musical.ly, TikTok Lite, and Douyin).
* Automatically renders and stores optimized MP4s into `Movies/ShoreUploader/`.
* Dispatches `MediaScannerConnection` so the new video instantly shows up in your default Gallery or Google Photos app.
* Seamless sharing to third-party services (Zalo, Telegram, Google Drive, etc.).

### 6. 🔒 100% On-Device & Privacy-Preserving
* All media parsing, atom restructuring, and file writes are processed locally on your device.
* Zero data or video telemetry sent to any remote server.

---

## 📱 System Requirements
* **Operating System:** Android 7.0 (Nougat, API 24) or higher (Android 10 - 15 recommended).
* **Permissions:** Storage / Media Access (`READ_MEDIA_VIDEO` on Android 13+ or `READ_EXTERNAL_STORAGE` on older versions).
* **Input Compatibility:** Standard MP4 containers (H.264 / AVC video, AAC audio, up to 120 FPS).

---

## 📥 Download & Installation

### 1. Download Installer APK
* 👉 [**Direct APK Download (ShoreUploader.apk)**](https://raw.githubusercontent.com/kingbone2006/ShoreUploader-Android/main/dist_apk/ShoreUploader-APK/app-debug.apk)
* 📦 [**View All GitHub Releases**](https://github.com/kingbone2006/ShoreUploader-Android/releases)

### 2. Installation
1. Download the `.apk` file to your Android phone.
2. If prompted by Android, enable **"Install unknown apps"** for your file manager or browser.
3. Tap **Install** and launch Shore Uploader.

### 3. User Guide
1. **Step 1 - Select Video:** Tap **Chọn Video Cần Tối Ưu** and pick your exported video.
2. **Step 2 - Verify & Customize:**
   * Review detected video specs: Resolution, Framerate, Duration, and File Size.
   * *(Optional)*: Adjust Ghost Samples (default: 9,112), toggle Force HDR10, or adjust Encoder Handler.
   * Tap **Bắt Đầu Tối Ưu (Start Optimize)**.
3. **Step 3 - Export & Publish:**
   * Once processing reaches 100%, tap **ĐĂNG TRỰC TIẾP LÊN TIKTOK** to jump directly to TikTok's sharing interface.
   * The patched video is immediately available in your `Movies/ShoreUploader/` gallery directory.

---

## ☕ Support & Donate

If you enjoy Shore Uploader and it helps elevate the visual quality of your content, you can support further development via Ko-fi or QR payment:

<div align="center">

[![Buy Me A Coffee](https://img.shields.io/badge/Ko--fi-Buy%20Me%20A%20Coffee-FF5E5B?style=for-the-badge&logo=ko-fi&logoColor=white)](https://ko-fi.com/kingbone2006)

👉 **[https://ko-fi.com/kingbone2006](https://ko-fi.com/kingbone2006)**

<br/>

*Thank you so much for your generous support! ❤️*

</div>

---

## 📄 License & Acknowledgements / Giấy phép & Tri ân

- **Shore Uploader for Android**: Licensed under the [MIT License](LICENSE).
- **Ý tưởng & Thuật toán gốc (Original Concept & Web Version)**: Chân thành cảm ơn và tri ân ý tưởng tuyệt vời từ **phiên bản website ShoreUploader** ([shoreuploader.com](https://shoreuploader.com)) cùng đội ngũ tác giả đã tiên phong nghiên cứu cơ chế tối ưu hóa container MP4 cho video TikTok. Đây là nguồn cảm hứng cốt lõi để xây dựng phiên bản Android độc lập này.  
  *(Sincere thanks and full credit to the original **ShoreUploader web version** ([shoreuploader.com](https://shoreuploader.com)) and its authors for the pioneering concept and container patching research that inspired and laid the foundation for this native Android app).*
- **Google AndroidX Media3**: High-performance media editing and transformation library by Google.
- Sincere thanks to the open-source media engineering and video creator communities for research into TikTok container passthrough behaviors.

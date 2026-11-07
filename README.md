<p align="center">
  <img src="app/src/main/res/drawable/tv_banner.png" alt="MWPlayer Banner" width="680" style="border-radius: 12px;"/>
</p>

<h1 align="center">🎬 MWPlayer (Modern Media & Video Player)</h1>

<p align="center">
  <strong>Trình phát đa phương tiện cao cấp và xem phim trực tuyến hiện đại dành cho Android & Android TV.</strong>
</p>

<p align="center">
  <a href="releases/MWPlayer-arm64-v8a.apk"><img src="https://img.shields.io/badge/Download%20APK-arm64--v8a%20(Khuy%C3%AAn%20d%C3%B9ng)-7C4DFF?style=for-the-badge&logo=android&logoColor=white" alt="Download APK arm64-v8a"/></a>
  <a href="releases/MWPlayer-armeabi-v7a.apk"><img src="https://img.shields.io/badge/Download%20APK-armeabi--v7a%20(M%C3%A1y%20c%C5%A9)-651FFF?style=for-the-badge&logo=android&logoColor=white" alt="Download APK armeabi-v7a"/></a>
</p>

---

## 📥 Tải về Cài đặt (Direct Downloads)

Tải trực tiếp bản APK mới nhất đã được build sẵn từ repository:

| Phiên bản | Kiến trúc CPU | Dung lượng | Tải xuống trực tiếp |
| :--- | :--- | :--- | :--- |
| **MWPlayer arm64-v8a** (Khuyên dùng) | `arm64-v8a` (Hầu hết điện thoại/TV mới) | ~43.6 MB | 🔗 [**Tải file APK (arm64-v8a)**](releases/MWPlayer-arm64-v8a.apk?raw=true) |
| **MWPlayer armeabi-v7a** | `armeabi-v7a` (Máy đời cũ, 32-bit) | ~48.1 MB | 🔗 [**Tải file APK (armeabi-v7a)**](releases/MWPlayer-armeabi-v7a.apk?raw=true) |

> 💡 **Mẹo:** Nếu bạn không rõ kiến trúc máy của mình, hãy tải bản **`arm64-v8a`**. Các thiết bị Android sản xuất từ năm 2017 đến nay đều hỗ trợ `arm64-v8a`.

---

## ✨ Tính năng Nổi bật (Key Features)

### 1. 📺 Xem Phim Trực tuyến (Movie Online)
- **Đa nguồn chất lượng cao (Cache thông minh & Pull-to-Refresh):** Tích hợp trực tiếp **VSMov** (mặc định Tab 1) và **KKPhim** (Tab 2) với bộ nhớ đệm từng nguồn, chỉ tải lại khi vuốt từ trên xuống (Pull-to-Refresh).
- **Featured Hero Carousel:** Slide banner chuyển tự động mượt mà các phim nổi bật nhất.
- **Quản lý phim toàn diện & tinh gọn:**
  - ⭐ **Phim Đang Theo Dõi (Watchlist):** Hiển thị trực tiếp kệ phim theo dõi ngay trên màn hình chính của Movie (phía trên kệ Phim Xem Gần Đây) và trong bộ lọc nhanh.
  - 🎬 **Phim Xem Gần Đây (Gộp chung Đang xem dở & Lịch sử):** Tự động lưu tiến trình xem, hiển thị thanh tiến trình % ngay trên thẻ phim và nút *"Tiếp tục xem: Tập X (xx%)"* khôi phục chính xác vị trí đang xem dở. Hỗ trợ **chọn xóa từng phim** hoặc xóa nhanh một chạm trên từng thẻ phim bên cạnh nút xóa tất cả.
- **☁️ Đánh dấu & Theo dõi Phim đã kéo về Tele Cloud:**
  - Tự động đánh dấu các bộ phim đã kéo về kho Telegram Cloud (`MovieBox #43264`), hiển thị tiến độ tập đã kéo (`☁️ Đã kéo X/Y`) ngay trên thẻ phim và trang chi tiết.
  - Tự động kiểm tra tập mới phát hành từ nguồn online, gửi **thông báo đẩy (Android Notification + Telegram)** và hỗ trợ **tự động kéo tập mới** về Tele Cloud.

### 2. 🔥 Tab AV 18+ Chuyên Biệt & Bảo Mật Riêng Tư
- **Tích hợp 3 nguồn JAV / 18+ lớn nhất:**
  - **AVDB OpenAPI (`avdbapi.com`):** Kho hơn **530.000+ phim JAV** đầy đủ thông tin mã phim (`SONE`, `IPZZ`, `FC2-PPV`...), diễn viên, phân loại Không che (Uncensored), Uncensored Leak, Có che, Amateur/FC2, Chinese AV, Hentai.
  - **XXVN OpenAPI (`xxvnapi.com`):** Kho phim **JAV Vietsub**, JAV HD, Không che, Clip Việt Nam, Hàn Quốc 18+, Châu Âu tốc độ cao trên CDN TikTok.
  - **MissAV (`missav.ws` / `missav.ai`):** Tích hợp bộ giải mã luồng `eval(p,a,c,k,e,d)` trực tiếp kèm cơ chế **Smart Fallback** tự động chuyển đổi sang OpenAPI khi nhà mạng chặn SNI/DNS.
- **Giao diện Thumbnail Ngang 16:9 & Tìm kiếm theo Diễn viên:** Hiển thị trọn vẹn ảnh bìa JAV chuẩn 16:9, hỗ trợ tìm nhanh theo mã phim hoặc bấm trực tiếp vào thẻ tên diễn viên (`👩 Actress`) để lọc toàn bộ phim của diễn viên đó.
- **🔒 Bảo mật Khóa PIN 4 số & Ẩn Tab AV:**
  - **Khóa mã PIN 4 số:** Đặt/Đổi/Xóa mã PIN 4 số và nút **Khóa ngay lập tức (`🔒`)** bảo vệ quyền riêng tư tuyệt đối.
  - **Ẩn / Hiện Tab AV trên thanh điều hướng:** Tùy chọn ẩn hoàn toàn nút `AV 18+` khỏi thanh điều hướng dưới cùng; khi ẩn vẫn có thể mở nhanh bằng cách **nhấn giữ nút `Movie`** ở thanh dưới cùng hoặc mở từ menu **Hồ sơ (Profile)**.
  - **Tách biệt hoàn toàn Lịch sử & Theo dõi:** Danh sách `⭐ AV Đang Theo Dõi` và `🎬 AV Xem Gần Đây` được lưu trữ độc lập trong `AvStore`, tuyệt đối không hiển thị trộn lẫn sang màn hình `Movie` hay `Home`.

### 3. ✈️ Nguồn Video Telegram Đỉnh cao
- Đồng bộ mượt mà với tài nguyên video từ Telegram (Saved Messages, channels, groups, supergroups, topics).
- **Bộ lọc & Sắp xếp linh hoạt:**
  - Gộp nút *Mới nhất / Cũ nhất* thành 1 nút toggle chuyển đổi nhanh.
  - Thêm tính năng **Sắp xếp ngẫu nhiên (Random)** để khám phá nội dung mới.
  - Tìm kiếm video siêu tốc theo từ khóa, hashtag, định dạng.
- **Thêm nhanh vào Yêu thích (*):**
  - Tích hợp nút ⭐ trên thanh điều khiển của trình phát.
  - **Tự động Forward thông minh:** Tùy chọn cấu hình tự động forward video yêu thích về một kho tin nhắn / group / channel / topic riêng biệt (hỗ trợ `drop_author = true` giữ trọn sự riêng tư).

### 4. 🎥 Trình phát Video Hiện đại (Advanced Video Player)
- **Chuyển tập trực tiếp ở cụm điều khiển trung tâm (Online Movie Player):** Tích hợp nút **Tập trước / Tập tiếp** ngay hai bên nút Play/Pause chính giữa màn hình giúp chuyển tập tức thì, lược bỏ các nút phụ rườm rà ở thanh dưới.
- **Vuốt ngang tua thông minh theo thời lượng & tốc độ vuốt (Dynamic Swipe-to-Seek):** Tự động tăng tốc độ tua khi vuốt ngang tỷ lệ thuận với độ dài video (30% tổng thời lượng, lên tới 45 phút cho một lượt vuốt trên phim dài 1–2 giờ) kết hợp nhân gia tốc theo tốc độ lướt ngón tay — áp dụng đồng bộ cho cả trình phát Online Movie và trình phát Telegram.
- **Tua nhanh thông minh:** Bổ sung cụm phím tua nhanh chuyên dụng **-10m, -1m** (bên trái) và **+1m, +10m** (bên phải) với icon tùy biến sang trọng, cùng chạm đúp hai bên màn hình để tua nhanh 10 giây.
- **Đa âm thanh & Phụ đề:** Tích hợp bộ chọn kênh âm thanh (Audio tracks) và phụ đề ngoài (Subtitles) đa ngôn ngữ.
- **Chế độ phát:** Hỗ trợ Picture-in-Picture (PiP), phát nền khi tắt màn hình (Background playback), xoay ngang/dọc nhanh, khóa màn hình cảm ứng chống chạm nhầm.

### 5. 🎨 Thiết kế Thương hiệu MWPlayer Đồng bộ
- **Giao diện:** Thiết kế giao diện Dark / Light Mode hiện đại, tối ưu độ tương phản và bảo vệ mắt.
- **Màn hình khởi động (Splash Screen):** Áp dụng giao diện Splash Screen 1080x1920 sắc nét với hiệu ứng mờ dần (fade-out) mượt mà khi mở ứng dụng.
- **Icon thích ứng (Adaptive Icon):** Căn giữa và co giãn chuẩn xác theo tỷ lệ an toàn của Android (Safe Zone Circle), không bị méo hình hay cắt góc trên bất kỳ giao diện launcher nào (Samsung OneUI, Google Pixel, Xiaomi HyperOS/MIUI...).
- **Tối ưu Android TV:** Hỗ trợ điều khiển mượt mà bằng Remote D-Pad, banner Android TV chuẩn 16:9.

---

## 🛠 Hướng dẫn Cài đặt

1. Tải file APK từ mục [Tải về Cài đặt](#-tải-về-cài-đặt-direct-downloads) ở trên về điện thoại hoặc Android TV.
2. Mở file APK vừa tải về trên thiết bị.
3. Nếu thiết bị hỏi quyền cài đặt ứng dụng không rõ nguồn gốc:
   - Vào **Cài đặt (Settings)** ➔ **Bảo mật (Security)**.
   - Bật tùy chọn **Cho phép cài đặt ứng dụng từ nguồn này (Install unknown apps)**.
4. Bấm **Cài đặt (Install)** và tận hưởng trải nghiệm xem phim đỉnh cao cùng **MWPlayer**!

---

## 💻 Hướng dẫn Build từ Source Code

Yêu cầu môi trường:
- **JDK:** OpenJDK 17 trở lên.
- **Android SDK:** Compile SDK 37, Min SDK 23.
- **Công cụ:** Gradle Wrapper (đi kèm dự án).

Clone dự án và build file APK:

```bash
# 1. Clone repository
git clone https://github.com/mwuan216/MWPlayer.git
cd MWPlayer

# 2. Build APK Debug
./gradlew assembleDebug

# File APK tạo ra tại: app/build/outputs/apk/debug/
```

---

## 📜 Giấy phép & Bản quyền

- Dự án phát triển dựa trên nền tảng mã nguồn mở hiện đại (Jetpack Compose, AndroidX Media3/ExoPlayer, TDLib).
- Logo, biểu tượng và bộ nhận diện thương hiệu thuộc quyền sở hữu của **MWPlayer**.

# Quy tắc làm việc (Project Rules)

## Quy trình Git:
1. **Trước khi thực hiện bất kỳ thay đổi nào:**
   - Kéo commit mới nhất từ remote repository về máy local:
     ```bash
     git pull
     ```
2. **Sau khi hoàn tất thay đổi:**
   - Kiểm tra các file đã thay đổi: `git status`
   - Thêm vào staging và commit với thông điệp rõ ràng:
     ```bash
     git add .
     git commit -m "Mô tả ngắn gọn và chính xác các thay đổi"
     ```
   - Push code lên remote repository nếu có remote:
     ```bash
     git push
     ```

## Quy tắc với Project Android (Mobile App):
- **Tối ưu thời gian xử lý & Tốc độ thực thi:**
  - Bỏ qua toàn bộ các bước run test (Unit test, UI/Instrumented test, lint check...), tuyệt đối không chạy các lệnh test tốn thời gian.
  - Quy trình thực thi tinh gọn: **Analyze (Phân tích) ➔ Sửa code ➔ Build APK ➔ Gửi APK qua Telegram**.
- **Build APK & Gửi Telegram:**
  - Sau khi hoàn thành sửa code, commit và `git push` thành công:
    - Bắt buộc tiến hành build file APK (`./gradlew assembleDebug` hoặc build tool tương ứng của project).
    - Tự động gửi file APK debug/hoàn chỉnh qua Telegram cho người dùng thông qua Telegram Bot API (sử dụng `$env:BOT_TOKEN` và Chat ID `1982424293` hoặc chat ID được cấu hình trong `ALLOWED_USER_IDS`).

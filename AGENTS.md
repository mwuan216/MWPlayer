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
- **Tối ưu thời gian xử lý & Tốc độ thực thi (Bắt buộc):**
  - Bỏ qua toàn bộ các bước run test (Unit test, UI/Instrumented test, lint check...), tuyệt đối không chạy các lệnh test tốn thời gian (`-x test -x lint`).
  - **Gọi công cụ song song & Đọc có trọng tâm (Batch Parallel & Targeted Read):**
    - Tuyệt đối không đọc/sửa từng file nhỏ lẻ qua nhiều vòng lặp (turns).
    - Trong 1 turn phân tích: phải gom tất cả các lệnh `read_file` cần thiết vào **cùng 1 lượt gọi song song**, đồng thời **chỉ đọc đúng khoảng dòng cần thiết (`200–350 dòng/file`)** thay vì đọc tràn 800 dòng trên các file lớn để giữ Context luôn nhỏ gọn (`< 50k tokens`), phản hồi tức thì và tránh lỗi `stream idle timeout (5m0s)`.
    - Trong 1 turn chỉnh sửa: phải gom tất cả các lệnh `edit_file` / `write_file` trên các file khác nhau vào **cùng 1 lượt gọi song song**.
  - **Gộp lệnh Hoàn tất & Giữ tiến trình Build đến khi xong 100% (Single-Command Pipeline & No Early Exit):**
    - Quy trình thực thi tinh gọn: **Analyze (1-2 turns) ➔ Sửa code (1-2 turns) ➔ Build APK + Copy APK + Git Commit/Push + Gửi Telegram trong 1 lệnh duy nhất**.
    - **QUAN TRỌNG (Chống ngắt tiến trình trên CLI `agy.exe -p`):** Khi lệnh `run_command` (như `gradlew assembleDebug`) chạy quá 10 giây và chuyển sang **Background Task**, **TUYỆT ĐỐI KHÔNG ĐƯỢC kết thúc turn (end turn)** để trả lời tạm, vì kết thúc turn ở chế độ CLI sẽ làm `agy.exe` thoát ngay lập tức và giết chết tiến trình build đang chạy. Bắt buộc phải gọi tiếp `schedule(DurationSeconds=45, TimerCondition="any", Prompt="Wait for build task")` (lặp lại nếu task vẫn đang chạy) cho tới khi Background Task báo hoàn tất 100% rồi mới kết thúc turn.
- **Build APK & Gửi Telegram:**
  - Sau khi hoàn thành sửa code, commit và `git push` thành công:
    - Bắt buộc tiến hành build file APK (`./gradlew assembleDebug` hoặc build tool tương ứng của project).
    - Tự động gửi thông báo / file APK qua Telegram cho người dùng thông qua Telegram Bot API (sử dụng `$env:BOT_TOKEN` và Chat ID `1982424293` hoặc chat ID được cấu hình trong `ALLOWED_USER_IDS`).
    - **Quy tắc giới hạn kích thước Telegram (50MB):**
      - Nếu file APK ≤ 50MB: Gửi trực tiếp file APK qua Telegram.
      - Nếu file APK > 50MB (vượt quá giới hạn upload của bot): **Không cần gửi file**, chỉ cần gửi tin nhắn thông báo kèm link tải trực tiếp file APK từ GitHub repository (ví dụ: `https://github.com/mwuan216/MWPlayer/raw/master/releases/MWPlayer-arm64-v8a.apk`).

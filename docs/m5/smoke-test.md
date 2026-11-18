# M5 smoke test — bản release **đã bật R8** trên emulator

Chạy ngày 18/11/2026, sau khi `app/build.gradle.kts` có `isMinifyEnabled = true` + `isShrinkResources = true` và
`app/proguard-rules.pro`. Đây là bằng chứng cho tiêu chí "8/8 bước pass, logcat sạch" của Stage 1: unit test và lint
đều chạy trên `debug`, nên **chỉ** bản release cài trên emulator mới chứng minh được R8 không làm hỏng app.

## 1. Môi trường

| Thứ | Giá trị |
|---|---|
| Emulator | `Pixel_6_API_34` (Google APIs Play Store, x86_64), Android **14** (API 34), 1080×2400, RAM 4 GB |
| Lệnh dựng máy | `emulator.exe -avd Pixel_6_API_34 -no-snapshot-load -no-boot-anim` — boot xong sau ~50 s (`sys.boot_completed=1`) |
| APK cài | `app-release-unsigned.apk` (4.739.435 B) **ký tạm bằng debug keystore** → `app-release-smoke.apk` (4.825.538 B) |
| Chữ ký | `apksigner` (build-tools **36.0.0**): `CN=Android Debug`, SHA-256 `b525c887…64e105e` |
| SHA-256 APK smoke | `8A4C952E733E85233B1063963B19725FF0A4149EA17D1A3F3179E14AF9F13B9C` |
| APK gốc chưa ký | SHA-256 `65B201D86775CE22EFE5C575748DB02AFF1427845F3CA26DCFDEB75A22D09414` |
| Tài khoản | `demo3@trippocket.test`, tạo mới bằng chính màn hình **Register** của app (không có sẵn trong repo) |
| Cách thao tác | `adb shell input tap` theo toạ độ lấy từ `uiautomator dump` sau **mỗi** bước (bố cục đổi giữa các màn hình, toạ độ cũ không dùng lại được) |
| Logcat | `adb logcat -c` ngay trước khi cài, đọc lại bằng `adb logcat -d` ở cuối |

**Lưu ý về chữ ký:** APK này ký bằng debug keystore **chỉ để cài lên emulator** (Android từ chối APK chưa ký).
Keystore release thật là việc của Stage 2 — lúc đó `app-release.apk` mới là bản để nộp.

## 2. Kết quả 8 bước

| # | Bước (theo brief) | Kết quả | Bằng chứng quan sát được |
|---|---|---|---|
| 1 | Đăng nhập Firebase (email/password) → Trip list | **PASS** | Đăng ký `demo3@trippocket.test` trên màn Register → app tự đăng nhập, mở `MainActivity` ("My trips"). Sau đó `pm clear` dữ liệu app và **đăng nhập lại** bằng màn Sign in: cũng vào thẳng `MainActivity` |
| 2 | Danh sách chuyến (Room) | **PASS** | Lúc đầu: "No trips yet" + "Plan your first trip: …". Tạo chuyến "Hoi An test", điểm đến "Hoi An", 18–20/11/2026 (chọn bằng DateRangePicker) → danh sách: "1 trip", mục "Happening now", thẻ chuyến "18 Nov – 20 Nov 2026 · 3 days", "1 member · 200,000 ₫ spent", nhãn "Backed up" |
| 3 | Itinerary: thêm / sửa / xoá hoạt động | **PASS** | Thêm "Walk old town", 09:00, chi phí 150.000 ₫, nhắc trước 30 phút → item hiện "Est. 150,000 ₫" + icon "Reminder set". Mở lại, sửa chi phí thành **175.000 ₫** → danh sách hiện "175,000 ₫". Xoá → hộp thoại "Delete Walk old town?" → sau khi xoá: "Nothing planned for this day" |
| 4 | Expenses: thêm khoản chi + Exchange rates | **PASS** | Thêm thành viên "Duy" (Members 0 → 1), thêm chi "Lunch" 200.000 ₫, người trả "Duy", nhóm Food, chia đều. Header đổi thành "Spent so far **200,000 ₫**", cột Food = 200,000. Màn Exchange rates tải thật từ `open.er-api.com`: "Updated: Wed, 18 Nov 2026 00:02:31 +0000", 1 USD ≈ 25.704 ₫, 1 EUR ≈ 28.271 ₫, 1 JPY ≈ 161 ₫, 1 KRW ≈ 19.25 ₫, 1 THB ≈ 760 ₫ |
| 5 | Weather: geocoding, forecast, ảnh (Glide) | **PASS** | Ô tìm kiếm gọi Open-Meteo Geocoding: gõ "Hoi An" → "Hoi An / Da Nang City, Vietnam – 15.88, 108.34". Lưu địa điểm → "Hoi An, Vietnam", "Last updated 18 Nov, 13:30", hiện tại **29°**, "Showers · 24° low", "Rain 73%", đoạn tóm tắt từ Wikipedia, danh sách dự báo Day 1/2/3 (Rain 73%, 29°/24°…). Glide được kiểm riêng ở bước 7 (ảnh bìa Trip Ideas tải và hiển thị thật) |
| 6 | Itinerary → Nearby sights → Add to day | **PASS** | Nút "Nearby sights" → "Within 10 km of Hoi An", danh sách có khoảng cách (247 m, 328 m, 616 m, 644 m, 997 m). Bấm "Add to day" của "Museum of Trade Ceramics" → nút đổi thành "**Added**"; quay lại Itinerary: "09:00 · Museum of Trade Ceramics · Free" |
| 7 | Trip Ideas CRUD (MockAPI) | **PASS** | **GET**: danh sách có "3 days in Da Lat", "Ha Long Bay cruise" (dữ liệu seed). **POST**: "Test idea R8" (1.000.000 ₫, cover URL, mô tả) → hiện ngay đầu danh sách, "Shared by you". **PUT**: mở Edit, đổi tên thành "Edited me" → lưu, danh sách hiện "Edited me". **DELETE**: xoá cả 2 idea test → "…will be removed for everyone" → biến mất khỏi danh sách. **Glide**: idea "Glide check" có cover URL `https://picsum.photos/seed/glide/600/400` hiển thị **ảnh thật** trong thẻ (chụp màn hình lúc chạy), idea không có URL hiện placeholder → Glide + OkHttp hoạt động trên bản R8 |
| 8 | Backup Firestore → Restore | **PASS** | Thẻ chuyến hiện "Backed up" sau khi có dữ liệu. Bấm "Restore from backup" khi dữ liệu còn đủ → snackbar "**This phone already has everything in your backup**" (chứng minh app đọc + giải mã document Firestore được). Sau đó `adb shell pm clear` (máy như mới, đăng nhập lại → "No trips yet"), bấm "Restore from backup" → "**1 trip restored**", thẻ chuyến trở lại đúng "1 member · 200,000 ₫ spent"; mở chuyến: itinerary còn "Museum of Trade Ceramics", Expenses còn "Lunch" 200,000 ₫ (Food), Members còn "Duy" |

**Tổng: 8/8 PASS.**

## 3. Logcat

Lệnh lọc theo đúng brief:

```powershell
& adb logcat -d | Select-String "FATAL|VerifyError|NoSuchField|NoSuchMethod|ClassNotFoundException|NoClassDefFoundError|E/AndroidRuntime"
```

Kết quả: **không có dòng nào** (0 hit) trên 21.029 dòng log của phiên chạy (13:24:57 → 13:44:42).

Bổ sung vì `logcat` chỉ giữ buffer gần nhất (các bước 1–3 chạy trước mốc 13:24):

- `dumpsys activity exit-info vn.edu.vlu.trippocket`: chỉ có 1 mục — `reason=10 (USER REQUESTED) subreason=21 (FORCE STOP)` lúc 13:42:03, do chính lệnh `pm clear` của mình, **không** phải crash.
- `dumpsys dropbox --print`: mục duy nhất của TripPocket là `data_app_anr` lúc **2026-11-17 22:07:56**, `Package: vn.edu.vlu.trippocket v1 (0.3-final)` — bản cài cũ trước M5, không phải bản 1.1.0 này. Các ANR ngày 18/11 (13:13–13:15) thuộc `com.google.android.gms` và `com.google.android.googlequicksearchbox` (emulator khởi động với ~3 GB RAM trống), không phải app.
- Không có `data_app_crash` nào cho `vn.edu.vlu.trippocket` trong ngày 18/11.

## 4. Số liệu đo được trên bản R8

| Chỉ số | Giá trị |
|---|---|
| Cold start lần đầu sau khi cài (qua `am start -W`) | 7.674 ms (WaitTime 7.698 ms) |
| Cold start sau `am force-stop` rồi mở lại | 1.149 ms (WaitTime 1.275 ms) |
| Lint `debug` | 0 errors, 14 warnings |
| Lint `release` (`.\gradlew.bat lintRelease`) | **0 errors, 14 warnings** — không phát sinh lỗi mới so với baseline |

## 5. Ghi chú / phát hiện phụ (không phải lỗi R8)

1. **Không thêm được expense khi chưa có member:** bấm "Add expense" lúc Members (0) không có phản ứng gì; thêm 1
   thành viên rồi bấm lại thì form mở bình thường. Đây là logic của app (expense cần người trả), nên ghi lại để báo
   cáo chứ không phải lỗi minify.
2. **Trip Ideas: danh sách không tự refresh ngay sau khi tạo.** Tạo idea với ô "What makes it good" để trống vẫn
   tạo **thành công trên server** nhưng danh sách không cập nhật, nên lần đầu trông như "bấm Share không có gì xảy
   ra"; sau khi tạo thêm một idea khác (danh sách refresh) thì idea cũ mới hiện ra. Không liên quan R8 (cùng một luồng
   MockAPI đã POST thành công), nhưng nên sửa ở M6: refresh list ngay sau POST/PUT/DELETE.
3. Dữ liệu test đã dọn: xoá hết idea "Test idea R8", "Edited me", "Glide check" khỏi MockAPI; chuyến "Hoi An test"
   vẫn còn trên tài khoản `demo3@trippocket.test` (dùng lại được cho video demo Stage 9).
4. Bàn phím ảo che nút bấm: mọi lần bấm nút ở nửa dưới màn hình phải `input keyevent 4` để đóng IME trước, nếu
   không lệnh tap rơi vào bàn phím. Đây là mẹo thao tác, không phải lỗi app.

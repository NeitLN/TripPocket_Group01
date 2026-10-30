# X1 — Kiểm tích hợp theo từng đợt
Feature: X1 · Giao cho: cả nhóm (Tiến điều phối) · Chạy trên bản `develop` đã ghép, không chạy trên nhánh riêng.

## Các đợt
| Ngày | Kiểm gì |
|---|---|
| CN 01/11 | Gate F0: build/lint/test; 4 tab; xoay màn hình; build khi không có json |
| T2 02/11 | Dữ liệu còn nguyên sau khi tắt app mở lại, ở cả 4 module (phần nào đã merge) |
| T3 03/11 | Checkpoint kỹ thuật: upload thật một chuyến; một alarm thật |
| T4 04/11 | Sửa offline → bật mạng → backup tự chạy; restore tối thiểu |
| T6 06/11 | A→B→A với dữ liệu PENDING và chuyến đã xoá; restore đầy đủ; nhắc sau khi đăng xuất/đăng nhập |
| CN 08/11 | Diễn tập toàn bộ kịch bản demo (proposal 3.4) ở chế độ máy bay |
| T2 09/11 | Kiểm chéo: Bảo → Weather, Duy Anh → Expenses, Minh → Itinerary, Tiến → toàn bộ kịch bản demo |
| T3 10/11 | Release gate (dưới đây) |

## Release gate (T3 10/11)
- [ ] `./gradlew testDebugUnitTest lintDebug assembleDebug` → PASS
- [ ] Kịch bản demo chạy từ đầu tới cuối trên máy thật; phần offline ở chế độ máy bay
- [ ] Gỡ app → cài lại → DB **trống** (allowBackup đã tắt) → đăng nhập → Restore → đủ dữ liệu; forecast tải lại được
- [ ] Đăng xuất, đăng nhập tài khoản khác → không thấy chuyến, không nhận nhắc của tài khoản trước
- [ ] Nhắc: đúng giờ khi có quyền exact; khởi động lại máy vẫn đến
- [ ] Xoay màn hình và "Don't keep activities" ở mọi tab: không crash, không mất `trip_id`
- [ ] Profiler: mở/đóng TripDetail 10 lần, không leak Fragment/Activity
- [ ] Không có `google-services.json` hay secret nào trong repo: `git ls-files | grep google-services` không ra gì
- [ ] Schema đã đóng băng; bản demo không dùng destructive fallback

Lỗi ghi vào GitHub Issues, gắn nhãn module. Sau khi PASS: merge `develop` → `main`, tag `v1.0`, cài cùng một APK lên
máy demo và máy dự phòng.

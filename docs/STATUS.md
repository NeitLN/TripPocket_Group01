# STATUS
Cập nhật: T6 30/10/2026 (tối)

## Đang ở đâu
- Mốc hiện tại: M0. F0 đang làm trên `feature/tien-foundation` (đã push). **Gate F0 PASS** (bằng chứng ở dưới và
  trong `docs/tasks/F0-foundation.md`). Chưa merge vào `develop`.
- Đã có: Room đủ 8 bảng + DAO khung, `TripWriter`/`TripChangeTracker`, `Limits`, `AppContainer`, Retrofit cho 2 host
  Open-Meteo, danh sách chuyến đọc từ Room, TripDetail chạy trên Navigation với 4 tab (thêm Checklist), manifest đủ
  quyền + receiver stub, `allowBackup=false`, model/string/test tách theo module, `TripSnapshot`.
- Tạm thời: `DevAuthRepository` (luôn là `dev-user`, seed chuyến mẫu ở bản debug) cho tới A1; các màn Itinerary,
  Weather, Expenses vẫn đọc MockData qua `SampleTripRepository` cho tới khi từng người chuyển sang Room.

## Quyết định đang chờ (Tiến)
| Câu hỏi | Đề xuất | Việc bị chặn | Phải chốt trước |
|---|---|---|---|
| File CI `.github/workflows/ci.yml` (đã soạn, chưa ghi) | Duyệt | Hàng rào PR | T7 31/10 |
| Chữ ký 3 repository của Bảo/Duy Anh/Minh | Họp T7 | Phần Room của I/W/E | T7 31/10 |
| Ngày demo chính xác | — | Lịch W3 | T2 02/11 |

## Bước tiếp theo
1. Tiến duyệt CI → ghi file, đặt quyền thực thi cho `gradlew` (git đang lưu là 100644).
2. Tiến tạo Firebase + deploy rules (RUNBOOK), gửi `google-services.json` cho nhóm.
3. Gửi 3 phiếu `docs/tasks/{I,W,E}-*.md` cho các bạn; họp T7 31/10 chốt chữ ký repository.
4. Review + merge `feature/tien-foundation` → `develop` (T7), rồi bắt đầu A1.

## Bộ kiểm nhanh (chạy đầu mỗi session)
- `./gradlew testDebugUnitTest lintDebug assembleDebug`
- Máy Tiến (đường dẫn có dấu): `subst T: "<repo>"`, `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`,
  `ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk`, chạy Gradle từ `T:\` (RUNBOOK).

## Bằng chứng gần nhất
- `feature/tien-foundation`, máy Tiến, 30/10: `testDebugUnitTest lintDebug assembleDebug` **PASS**, 16/16 test,
  lint chỉ có thông báo phiên bản (`AndroidGradlePluginVersion`, `GradleDependency`, `NewerVersionAvailable`).
- Emulator Pixel_7 API 34, bản debug: danh sách 3 chuyến từ Room; 4 tab mở được; xoay màn hình giữ tab; process death
  ở tab Weather → khôi phục đúng chuyến; logcat crash buffer trống. **PASS**.
- Chưa kiểm: build có `google-services.json` (chưa có Firebase project) — NOT RUN.

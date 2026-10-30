# STATUS
Cập nhật: T6 30/10/2026

## Đang ở đâu
- Hiện tại: Milestone 2 xong (MVVM + ViewBinding, 5 màn hình chạy trên `MockData`). Chưa có Room, Retrofit, Firebase,
  Navigation, nhánh `develop`.
- Mong muốn: đủ 100% proposal, demo 11–13/11 (`docs/PROJECT.md`, `docs/ROADMAP.md`).
- Phải giữ nguyên: quy ước MVVM của Milestone 2 (README), các test hiện có.
- Chưa biết: ngày demo chính xác; giờ rảnh của từng người (lịch đang giả định làm cả cuối tuần); KSP/Robolectric có
  chạy với AGP 9.4/SDK 37 không (spike F0).
- Mốc hiện tại: M0. Docs kế hoạch + phiếu việc (`docs/tasks/`) đã được rà soát độc lập qua 3 vòng; **chưa commit,
  chờ Tiến duyệt**.

## Quyết định đang chờ (Tiến)
| Câu hỏi | Đề xuất | Việc bị chặn | Phải chốt trước |
|---|---|---|---|
| 6 quyết định "chờ duyệt" trong ROADMAP/ARCHITECTURE | Như trong file | F0, I1, A3 | T7 31/10 |
| CI GitHub Actions (test/lint/build trên PR vào `develop`) | Có; soạn sẵn, duyệt rồi mới ghi file | — | T7 31/10 |
| Giờ rảnh mỗi người, có làm cuối tuần không | — | Lịch theo ngày | T7 31/10 |
| Ngày demo chính xác | — | Lịch W3 | T2 02/11 |

## Bước tiếp theo
1. Tiến duyệt docs → commit docs lên `main`, tạo `develop`.
2. Tiến tạo Firebase + deploy rules (RUNBOOK), gửi `google-services.json` cho nhóm.
3. F0 trên `feature/tien-foundation`: spike KSP + Robolectric **trước tiên**.
4. Gửi `docs/tasks/<module>.md` cho Bảo, Duy Anh, Minh: làm phần "Làm ngay T6–CN"; họp ngắn T7 31/10 để chốt contract.

## Bộ kiểm nhanh (chạy đầu mỗi session)
- `./gradlew testDebugUnitTest lintDebug assembleDebug`
- Máy Tiến (đường dẫn có dấu): `subst T: "<repo>"`, `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`,
  `ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk`, rồi chạy Gradle từ `T:\` (RUNBOOK).

## Bằng chứng gần nhất
- `testDebugUnitTest` trên `24a3efb` (cây làm việc chỉ thêm docs), máy Tiến, JDK của Android Studio: **PASS**
  (cảnh báo deprecated `bundleOf` ở `TripDetailActivity.kt:70`, F0 sẽ bỏ). `lintDebug`, `assembleDebug`: NOT RUN.

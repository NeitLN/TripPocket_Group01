# STATUS
Cập nhật: T2 17/11/2026 (sau release `v1.0`)

## Đang ở đâu
- **Đã phát hành**: `develop` `1c86a8f` → PR #54 → `main` `62a2552`, tag **`v1.0`**, GitHub Release
  https://github.com/NeitLN/TripPocket_Group01/releases/tag/v1.0 kèm `app-debug.apk`
  (SHA-256 `E662B8553B574AB058787981C6F6FB43C034104B3052C3118D9DC0B339BBCC1B`).
- 9 PR còn treo đã merge hết trong ngày 17/11: #53, #52, #49, #27, #25, #33, #50, #51, #34; mỗi lần merge đều chờ
  CI `develop` xanh (gồm cả job `firestore-rules` mới). **0 PR, 0 issue mở.**
- `main` xanh: `build` + `firestore-rules`, run `44115689826` (3m41s).
- M4 (network/cloud) vào `develop` từ trước qua #46; A3/A4/W5/A5 và seed I2 vào nốt trong đợt này.

## Bằng chứng theo phiếu (unit test, bản `main` `62a2552`)
Cột "Test" là test tự động chứng minh điều kiện; "Máy thật" là phần unit test không thay được, kiểm ở release gate.

| Điều kiện (phiếu) | Test | Máy thật |
|---|---|---|
| A1 đăng nhập, sai mật khẩu/mất mạng không kẹt Loading | `LoginViewModelTest` (12) | mở lại app vẫn đăng nhập |
| A2 CRUD offline; B không mở được chuyến của A | `TripRepositoryTest.anotherAccount_cannotOpenEditOrDeleteTheTrip` | — |
| A3 sửa offline → SYNCED; sửa lúc worker chạy; A→B→A | `BackupRepositoryTest` (12), gồm `anEditDuringTheUpload_…`, `anotherAccountSignedIn_stopsTheRun_…` | upload thật lên Firestore |
| A4 cài lại → Restore đủ; snapshot hỏng/vượt giới hạn bị từ chối | `RestoreRepositoryTest` (12), `TripSnapshotValidatorTest` (8), `TripSnapshotCodecTest` (7) | gỡ app → cài lại → Restore |
| A5 snapshot đầy giới hạn < 1 MiB; rules 2 tài khoản | `FullTripSnapshotSizeTest` (4); rules: PR #34 (job `firestore-rules`) | — |
| E1 chặn xoá thành viên đang dùng | `ExpenseRepositoryTest.deletingAPayerOrParticipant_…` | — |
| E2 100.001 chia 3 → 33.334/33.334/33.333; lệch tổng không lưu | `ExpenseRepositoryTest` (17) | — |
| E3 A trả 300.000 cho 3 → B, C nợ A 100.000 | `ExpenseBalancesTest.proposalDemo_…` | — |
| E4 dự tính so với thực chi, share text | `ExpensesViewModelTest`, `ExpenseFlowCrossCheckTest` | share sheet |
| I1 CRUD; hoạt động 201 không ghi, revision giữ nguyên | `ItineraryRepositoryTest.the201stActivity_…` | — |
| I2 checklist, tối đa 100 | `ChecklistRepositoryTest` (10) | — |
| I3 kéo thả một transaction; đổi giờ sắp lại | `ItineraryRepositoryTest.reorder_…`, `afterAReorder_changingTheTime_…` | — |
| I4 exact/inexact; sửa/xoá đổi/huỷ nhắc | `AlarmReminderSchedulerTest`, `ActivityReminderSyncTest` | nhắc đến đúng giờ |
| I5 khởi động lại, đăng xuất, đổi múi giờ | `ReminderReconcilerTest`, `BootReceiverTest` | khởi động lại máy |
| W1 "da lat", "xyz", mất mạng, Retry cùng query | `WeatherViewModelTest` (45) | — |
| W2 trùng tên khác toạ độ; địa điểm 31 bị chặn | `SavedPlaceRepositoryTest` | — |
| W3 chế độ máy bay vẫn hiện kèm giờ cập nhật | `WeatherViewModelTest.aFailedRefresh_keepsTheCachedRows_…` | chế độ máy bay |
| W5 offline vẫn hiện; chưa có địa điểm thì hướng dẫn | `ItineraryViewModelTest.weather_…` | — |

Kết quả chạy 17/11 (`main` `62a2552`, Windows, JDK `D:\android studio\jbr`): **452/452 PASS**, 0 lỗi, lint không có
lỗi chặn build, `BUILD SUCCESSFUL` (2m46s khi build lại từ `main`; 4m13s trên `develop`).

## Release gate X1 (phiếu `X1-integration.md`)
| Mục | Trạng thái |
|---|---|
| `testDebugUnitTest lintDebug assembleDebug` | **PASS 17/11** — 452 test trên `main` và trên `develop` |
| Không có `google-services.json` hay secret trong git | **PASS 17/11** (`git ls-files` không ra gì) |
| Schema đóng băng | **PASS** — `app/schemas/.../1.json` trong repo, `version = 1` |
| Bản demo không dùng destructive fallback | **PASS 17/11** sau #50: `git grep fallbackToDestructive` rỗng, `AppDatabaseMigrationTest` xanh |
| Gỡ app → cài lại → DB trống | **PASS một nửa 17/11**: cài lại trên emulator API 34 → `databases/` + `shared_prefs/` rỗng, app ra màn Sign in; phần đăng nhập → Restore kiểm trên máy thật |
| Kịch bản demo trên máy thật, chế độ máy bay | kiểm khi quay video (dùng APK trong Release) |
| Đăng xuất → tài khoản khác: không thấy chuyến, không nhận nhắc | kiểm khi quay video |
| Nhắc đúng giờ; khởi động lại máy vẫn đến | kiểm trên máy thật |
| Xoay màn hình, "Don't keep activities" mọi tab | kiểm trên máy thật |
| Profiler: TripDetail mở/đóng 10 lần không leak | Itinerary đã kiểm (issue #35: 21 vòng, số về 1 activity / 1 ViewRootImpl); các tab khác kiểm khi quay |

Chi tiết lệnh + số đo: `.ai/evidence/X1-release-gate-2026-11-17.md`; checklist máy thật + kịch bản quay:
`.ai/evidence/X1-device-checklist-17-11.md`.

## Bước tiếp theo
1. Cài `app-debug.apk` trong Release lên **cả hai** máy (cùng một file), chạy 5 mục máy thật còn lại.
2. ~~Quay video + upload~~ **xong 17/11**: https://youtu.be/4aHB0uAgqk4 (Unlisted, 1:53).
3. ~~Worksheet~~ **xong 17/11**: `docs/worksheet.md` (mục lục video, 10 ảnh trong `docs/images/`, bảng endpoint, MVVM, Git + WBS, đối chiếu rubric).
4. Nộp LMS: link PR tích hợp (#46, #54), link Release `v1.0`, video, worksheet.
5. Nếu có sửa code sau `v1.0`: tag `v1.0.1`, cài lại cùng một APK mới lên cả hai máy.

## Bộ kiểm nhanh (chạy đầu mỗi session)
- `./gradlew testDebugUnitTest lintDebug assembleDebug`
- Máy Tiến (đường dẫn có dấu): `subst T: "<repo>"`, `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`,
  `ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk`, chạy Gradle từ `T:\` (RUNBOOK). Build trong terminal VS Code mà báo lỗi
  `JdkImageTransform`/`jlink` thì chạy `gradlew --stop` trước: extension Java dựng daemon bằng JRE không có `jlink`.

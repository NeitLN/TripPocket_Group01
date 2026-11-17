# STATUS
Cập nhật: T3 10/11/2026

## Đang ở đâu
- `develop` có đủ feature trừ **A3, A4, W5**: ba PR này CI xanh và đang chờ review/merge (#24 → #25, #27).
  Còn chờ thêm: #49 (seed Packing list cho chuyến mẫu, issue #35), #50 (bỏ destructive fallback, X1), #34 (test
  rules, đã approve), #33 (Minh gộp lại `develop` sau khi #24 vào).
- `develop` đi trước `main` 30 commit; chưa có tag `v1.0`.
- M4 (network/cloud) đã vào `develop` qua #46.

## Bằng chứng theo phiếu (unit test, bản `develop` + #24 #25 #27 #49 gộp thử)
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

Kết quả chạy 10/11 (máy Tiến, `--rerun-tasks`): **441/441 PASS**, 0 lỗi, trên `develop` gộp thử #24 #25 #27 #49
(gộp không conflict).

## Release gate X1 (phiếu `X1-integration.md`)
| Mục | Trạng thái |
|---|---|
| `testDebugUnitTest lintDebug assembleDebug` | PASS 10/11 trên bản gộp thử; chạy lại sau khi merge thật |
| Không có `google-services.json` hay secret trong git | PASS 10/11 (`git ls-files` không ra gì) |
| Schema đóng băng | PASS 10/11: `1.json` sinh lại từ entity không đổi so với 30/10, `version = 1` |
| Bản demo không dùng destructive fallback | Lệch đã sửa ở PR #50 (chờ merge): APK debug (RUNBOOK) vẫn bật fallback; PR bỏ nó ở mọi bản build, có test |
| Kịch bản demo trên máy thật, chế độ máy bay | chưa chạy |
| Gỡ app → cài lại → DB trống → đăng nhập → Restore đủ | chưa chạy (cần #24, #25) |
| Đăng xuất → tài khoản khác: không thấy chuyến, không nhận nhắc | chưa chạy |
| Nhắc đúng giờ; khởi động lại máy vẫn đến | chưa chạy |
| Xoay màn hình, "Don't keep activities" mọi tab | chưa chạy |
| Profiler: TripDetail mở/đóng 10 lần không leak | Itinerary đã kiểm (issue #35, Minh); các tab khác chưa |

## Bước tiếp theo
1. Review lại #24 (Bảo, Minh), merge #24 → #25, #27, #49, #34, #50; Minh gộp `develop` vào #33.
2. Chạy release gate trên `develop` đã gộp, trên máy thật; ghi kết quả vào bảng trên.
3. PASS → merge `develop` → `main`, tag `v1.0`, cài cùng APK lên máy demo và máy dự phòng (RUNBOOK).

## Bộ kiểm nhanh (chạy đầu mỗi session)
- `./gradlew testDebugUnitTest lintDebug assembleDebug`
- Máy Tiến (đường dẫn có dấu): `subst T: "<repo>"`, `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`,
  `ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk`, chạy Gradle từ `T:\` (RUNBOOK). Build trong terminal VS Code mà báo lỗi
  `JdkImageTransform`/`jlink` thì chạy `gradlew --stop` trước: extension Java dựng daemon bằng JRE không có `jlink`.

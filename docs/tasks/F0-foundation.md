# F0 — Nền dùng chung
Feature: F0 · Hạn: T7 31/10 (chốt contract), CN 01/11 (gate) · Giao cho: Tiến · Nhánh: `feature/tien-foundation` → `develop`
Phụ thuộc: — · Chặn **bắt đầu** phần Room/Navigation của mọi module

## Mục tiêu
Dựng mọi contract mà 4 module cùng dùng, cộng **một lát dọc thật**: danh sách chuyến đọc từ Room qua Flow. Từ CN
01/11, các bạn chỉ còn viết trong file của chính mình.

## T6 30/10 — spike (làm trước tiên)
- Room + **KSP2** với AGP 9.4.1 (Kotlin tích hợp sẵn trong AGP). **Không** thêm plugin `org.jetbrains.kotlin.android`
  hay kapt. Ghi lại phiên bản đã chạy được.
- Robolectric (ghim phiên bản) + `@Config(sdk = [35])` + Application riêng cho test; một DAO test chạy code Room đã
  sinh, kiểm khoá ngoại. Không đạt thì chuyển sang instrumented và ghi lại lý do.

## T7 31/10 — chốt contract (merge)
- Dependency: Room, KSP, Retrofit + OkHttp logging + Gson, Firebase BoM (auth, firestore), WorkManager, Navigation,
  SwipeRefreshLayout, coroutines-test, Robolectric.
- 8 entity theo proposal 4.2 và ARCHITECTURE (thời gian, khoá ngoại `CASCADE`/`RESTRICT`); `AppDatabase`
  (`exportSchema = true`) với accessor cho mọi DAO; DAO khung của từng module (chỉ có chữ ký, chủ module tự viết query).
- `TripChangeTracker` + **ví dụ chạy được**: `TripDao`/`TripRepository` dạng Flow, theo đúng khuôn ghi trong
  ARCHITECTURE; test cả ca thành công lẫn rollback.
- `domain/Limits.kt`; interface `BackupScheduler` (bản thật ở A3, bản fake cho test); data class snapshot (mapper ở A3).
- Chữ ký (interface) của 4 repository, factory nhận repository từ `AppContainer`, `AuthRepository.currentUid`.

## CN 01/11 — gate (merge)
- `TripPocketApp` + `AppContainer`, Firebase lấy lười; plugin google-services `apply false` ở root, chỉ apply ở app
  khi có file json; `google-services.json` trong `.gitignore`.
- `TripDetailActivity` dùng NavHost: `nav_trip_detail.xml` + 4 graph con, mỗi graph có màn bắt đầu thật; menu 4 tab
  (thêm Checklist), ID menu trùng ID graph. `TripDetailViewModel` dùng chung cho Activity, giữ `trip_id`, trạng thái
  Loading/Ready/NotFound, kiểm `ownerUid`. Bỏ `bundleOf` đã deprecated.
- Danh sách chuyến đọc Flow từ Room (thay MockData) với dữ liệu mẫu được seed.
- Tách `Models.kt` thành model theo module; tách `ViewModelTest` theo module; chuyển string sang `strings_<module>.xml`
  (giữ nguyên tên). Thêm test application/fake dùng chung.
- Manifest: `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`,
  `RECEIVE_BOOT_COMPLETED`, `android:allowBackup="false"`, `android:name=".TripPocketApp"`, receiver stub của Bảo
  (boot, đổi múi giờ, đổi giờ).

## Gate (tiêu chí chấp nhận)
- [ ] `./gradlew testDebugUnitTest lintDebug assembleDebug` → PASS, test cũ vẫn xanh
- [ ] DAO test: ghi bảng con → trip PENDING, revision +1 (đúng một lần); vượt giới hạn → rollback, revision giữ nguyên
- [ ] DAO test: không thấy chuyến của `ownerUid` khác; chuyến `isDeleted` bị ẩn; xoá payer/participant bị chặn
- [ ] Máy **không có** `google-services.json` vẫn build và chạy test được
- [ ] Cả 4 tab mở được; xoay màn hình và "Don't keep activities" không mất `trip_id`

## Ngoài phạm vi
Logic nghiệp vụ và query DAO của từng module (chủ module tự viết từ CN 01/11).

# ARCHITECTURE

Chỉ ghi những quy ước mà hai người có thể làm mâu thuẫn nhau. Luồng MVVM tổng quát xem proposal 2.2 và README.
Mọi quyết định trong file này đã được leader duyệt ngày 30/10/2026.

## Ranh giới module và quyền sở hữu file

```
vn.edu.vlu.trippocket
├── TripPocketApp.kt          Application, tạo AppContainer                      — Tiến
├── di/AppContainer.kt        ServiceLocator thủ công (không Hilt)               — Tiến
├── data/local/               AppDatabase, entity/, TripChangeTracker            — Tiến (schema)
├── data/local/dao/           mỗi DAO thuộc module của nó (bảng dưới)
├── data/model/               model theo module: TripModels.kt, ItineraryModels.kt, WeatherModels.kt, ExpenseModels.kt
├── data/remote/openmeteo/    Retrofit API, DTO                                  — Duy Anh
├── data/remote/firebase/     Auth, Firestore backup                             — Tiến
├── data/repository/          một repository mỗi module
├── domain/                   logic Kotlin thuần, có unit test
├── ui/<module>/              Fragment, ViewModel, Adapter của module đó
├── work/                     BackupWorker, BackupScheduler                      — Tiến
└── reminder/                 ReminderScheduler, ReminderReconciler, receiver    — Bảo
```

| Module | Repository | DAO | Package UI | Test |
|---|---|---|---|---|
| Auth/Trips/Backup — Tiến | `AuthRepository`, `TripRepository`, `BackupRepository` | `TripDao`, `BackupDao` | `ui/login`, `ui/trips`, `ui/detail` | `ui/trips/*Test`, `data/*Backup*Test` |
| Itinerary — Bảo | `ItineraryRepository` | `ActivityDao`, `ChecklistDao` | `ui/itinerary`, `ui/checklist` | `ui/itinerary/*Test` |
| Weather — Duy Anh | `WeatherRepository` | `SavedPlaceDao`, `WeatherCacheDao` | `ui/weather` | `ui/weather/*Test` |
| Expenses — Minh | `ExpenseRepository` | `TripMemberDao`, `ExpenseDao` | `ui/expenses`, `ui/members` | `ui/expenses/*Test`, `domain/ExpenseCalculatorTest` |

Quy tắc:
- F0 tạo khung (chữ ký hàm, interface, entity). Từ CN 01/11, mỗi người tự viết query DAO và phần cài đặt repository
  của module mình, theo đúng chữ ký đã chốt.
- Module này **chỉ đọc** dữ liệu của module khác qua repository của module đó, không gọi thẳng DAO của người khác.
  Các phụ thuộc chéo đã biết:
  - Thẻ chuyến (Tiến) cần số thành viên và tổng chi từ `ExpenseRepository` (Minh).
  - Thời tiết từng ngày trong Itinerary (Bảo) lấy từ `WeatherRepository` (Duy Anh).
  - Budget (Minh) cần `ItineraryRepository.estimatedTotal` (Bảo).
- Fragment không gọi DAO hay Retrofit. ViewModel không giữ `Context`/View. Repository được tiêm vào factory từ
  `AppContainer`; ViewModel không tự `new` repository.
- File dùng chung, **chỉ Tiến sửa**: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `AndroidManifest.xml`,
  `AppDatabase.kt`, `entity/*`, `nav_trip_detail.xml`, `menu_trip_detail.xml`, `AppContainer.kt`. Cần thay đổi ở đó
  thì gửi kèm **đúng đoạn cần sửa** trong PR, Tiến ghép vào lần merge kế tiếp.
- Mỗi module có file string riêng: `values/strings_<module>.xml`. F0 chuyển các chuỗi hiện có sang file của module
  tương ứng **một lần**, giữ nguyên tên. `strings.xml` gốc chỉ giữ chuỗi dùng chung.

## Dữ liệu (Room, proposal 4.2)
- Đủ 8 entity của proposal được tạo trong F0. Đổi field sau đó phải qua Tiến.
- Entity (`data/local/entity`) tách khỏi model (`data/model`); repository map giữa hai bên.
- Repository trả `Flow` khi đọc và dùng `suspend` khi ghi. Dữ liệu tải bất đồng bộ, nên trạng thái ban đầu là Loading;
  không coi `null` lúc đầu là lỗi.
- **Tiền:** `Long` VND.
- **ID:** UUID string do client tạo.
- **Khoá ngoại:** bảng con → Trip `CASCADE`. `Expense.payerId` và `ExpenseShare.memberId` → TripMember `RESTRICT`.
  Repository kiểm thêm rằng mọi thành viên được dùng đều thuộc đúng chuyến của chi phí.
- **Giới hạn (proposal 6.2):** hằng số nằm trong `domain/Limits.kt`. Form kiểm trước; repository kiểm lại trong
  cùng transaction với lệnh insert.

### Thời gian
- `Trip.startDate`, `Trip.endDate`, `Expense.spentAt`: **epoch day**.
- `Activity.startTime`: **phút trong ngày** (0–1439), là **giờ địa phương trôi theo máy**: không lưu múi giờ, và
  "09:00 ngày 2" luôn là 09:00 theo múi giờ hiện tại của máy.
- `updatedAt`, `createdAt`, `fetchedAt`: epoch millis.
- Alarm được tính lại từ múi giờ hiện tại khi: khởi động máy, đổi múi giờ, đổi giờ hệ thống, đổi ngày của chuyến,
  restore, đăng nhập/đăng xuất (xem `ReminderReconciler`).

### Ghi bảng con và đánh dấu backup
Mọi thao tác ghi theo **một khuôn duy nhất**, mỗi thao tác logic chỉ tăng revision **đúng một lần** (kể cả khi
sửa chi phí kèm toàn bộ shares, hoặc kéo thả nhiều dòng):
```kotlin
db.withTransaction {
    limits/ownership check   // vượt giới hạn -> ném lỗi, transaction rollback, revision giữ nguyên
    mutate rows
    tripChangeTracker.touch(tripId)   // PENDING, localRevision + 1, updatedAt = now (một câu SQL)
}
backupScheduler.request(uid)          // gọi SAU khi transaction đã commit
```
F0 có ví dụ chạy được và test cả ca thành công lẫn ca rollback.

### Thứ tự hoạt động trong ngày
`orderIndex` là thứ tự hiển thị. Khi tạo mới hoặc đổi giờ/ngày của một hoạt động: sắp lại ngày đó theo giờ (sắp
ổn định) rồi đánh số lại. Kéo thả chỉ đổi `orderIndex`. Sửa tên, ghi chú hay chi phí thì giữ nguyên thứ tự.

### Migration
`exportSchema = true`. Mỗi lần đổi schema thì **tăng version**; chỉ trong lúc phát triển mới được dùng destructive
fallback (dữ liệu thử, xoá được). Đóng băng schema **trước khi tạo dữ liệu demo**; bản demo không dùng destructive
fallback.

## Tài khoản, phiên và backup
- Quyền sở hữu được kiểm **ở các điểm vào**, không cần kiểm trong từng query bảng con:
  - TripDetail chỉ mở chuyến có `ownerUid` bằng uid hiện tại; không đúng thì vào trạng thái NotFound. Kiểm lại khi
    Activity được tạo lại.
  - Khi đăng xuất: huỷ phiên TripDetail đang mở, huỷ alarm của tài khoản đó, giữ nguyên dữ liệu local.
  - Alarm chỉ được đặt, và thông báo chỉ được hiện, khi uid hiện tại là chủ chuyến.
- **Backup (proposal 6.2):**
  - Mỗi tài khoản một unique work `trip-backup:<uid>`, policy `APPEND_OR_REPLACE`.
  - Worker xử lý lần lượt cho tới khi hết chuyến PENDING của uid đó. Mỗi chuyến: đọc snapshot + revision trong một
    transaction → chờ Firestore ghi xong → `UPDATE … SET SYNCED WHERE tripId=? AND localRevision=?`.
  - Lỗi mạng tạm thời → retry. Snapshot ≥ 1 MiB → FAILED (cũng có điều kiện revision), rồi xử lý chuyến tiếp theo.
  - Uid đang đăng nhập khác uid của job → kết thúc thành công, giữ PENDING.
  - Chuyến đã xoá mềm: xoá document trên Firestore trước, rồi xoá local có điều kiện.
  - Upload và restore dùng chung **một mutex cho mỗi tài khoản**.
  - Khi mở app và khi đăng nhập: còn chuyến PENDING thì enqueue lại.
- `android:allowBackup="false"`: Firestore là bản sao lưu duy nhất, và cài lại app phải bắt đầu với DB trống thì
  mới kiểm restore được.

## Điều hướng
- Giữ `LoginActivity` → `MainActivity` → `TripDetailActivity`.
- `TripDetailActivity` dùng Navigation: `nav_trip_detail.xml` (Tiến) `<include>` 4 graph con, mỗi graph có màn bắt
  đầu thật: `nav_itinerary.xml`, `nav_checklist.xml` (Bảo), `nav_weather.xml` (Duy Anh), `nav_expenses.xml` (Minh).
  ID của menu bottom nav trùng ID của graph con.
- `trip_id` nằm trong **`TripDetailViewModel` dùng chung cho cả Activity** (lấy từ intent, lưu trong
  `SavedStateHandle`). Fragment lấy ViewModel này qua `activityViewModels()`, rồi truyền `tripId` vào factory của
  ViewModel module mình. Form nhận id của bản ghi đang sửa (vd `activityId`) qua nav argument của graph con.
- `TripDetailViewModel` có trạng thái Loading / Ready / NotFound; module chỉ tải dữ liệu khi đã Ready.

## Remote
- **Open-Meteo:** chỉ `WeatherRepository` gọi Retrofit, với 2 base URL (geocoding và forecast). Forecast ghi vào
  Room trước, UI đọc từ Room. Kết quả tìm kiếm giữ trong ViewModel. `CancellationException` phải được ném lại.
- **Firebase:** plugin google-services khai báo `apply false` ở root và chỉ apply ở app khi có
  `app/google-services.json`. Firebase được lấy **lười** qua `AppContainer`. Test dùng Application riêng và fake,
  không bao giờ khởi tạo Firebase. Firestore rules phải được deploy trước lần thử cloud đầu tiên.

## Bất đồng bộ và UI
- Room/network chạy trên `Dispatchers.IO`. UI state là `StateFlow` (Loading/Success/Error/Empty).
- Collect bằng `repeatOnLifecycle` trên `viewLifecycleOwner`; `_binding = null` trong `onDestroyView`.
- `ListAdapter` + `DiffUtil`.

## Test
- DAO test chạy bằng **Robolectric** với `@Config(sdk = [35])`, nằm trong `testDebugUnitTest`, nên máy nào cũng chạy
  được mà không cần emulator. Nếu spike F0 thất bại thì chuyển sang instrumented (`connectedDebugAndroidTest`).
- DAO test phải chạy code Room đã sinh ra thật: kiểm rollback, revision và khoá ngoại.

## Git và review
- `main`: chỉ code demo được. `develop`: tích hợp. Nhánh: `feature/<tên>-<feature>`.
- PR vào `develop` **bắt buộc Tiến duyệt** (proposal 6.5). Trước đó có một người review lượt đầu:
  Bảo → Weather, Duy Anh → Expenses, Minh → Itinerary. Người review lượt đầu kiểm bằng chứng và lỗi rõ ràng.
- Trước khi mở PR: `./gradlew testDebugUnitTest lintDebug assembleDebug` xanh, kèm ảnh/clip màn hình.

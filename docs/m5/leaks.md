# M5 Stage 3 — LeakCanary + memory audit

Đo ngày 18/11/2026 trên nhánh `feature/m5-hardening-release` (sau commit `22826ba` "M5 stage 2: release signing
config"), emulator **`Pixel_6_API_34`** (Android 14, 1080x2400, AVD có sẵn từ M4). Mọi số dưới đây copy từ output lệnh
thật, log lưu ở `C:\Users\LENOVO\AppData\Local\Temp\opencode\m5s3\`.

## 1. Cấu hình LeakCanary (chỉ debug)

| Thứ | Giá trị |
|---|---|
| Phiên bản | **2.14** (`com.squareup.leakcanary:leakcanary-android`) |
| Khai báo | `gradle/libs.versions.toml` (`leakcanary = "2.14"`, `leakcanary-android`) |
| Nối vào app | `app/build.gradle.kts`: `debugImplementation(libs.leakcanary.android)` — **không** `implementation` |
| Cấu hình thêm | không. Mặc định 2.14: tự watch `Activity`/`Fragment`/`Fragment view` đã huỷ, dump heap khi object còn sống sau 5 s, giữ 7 lần dump gần nhất |

Bằng chứng bản release **không** chứa LeakCanary (một dòng cho PDF):

```
gradlew :app:dependencies --configuration releaseRuntimeClasspath | Select-String leakcanary
  -> 0 dòng

gradlew :app:dependencies --configuration debugRuntimeClasspath  | Select-String leakcanary
  -> 22 dòng: com.squareup.leakcanary:leakcanary-android:2.14 -> leakcanary-android-core ->
     shark-android / shark-graph / shark-hprof / plumber-android ...
```

Trong APK debug, LeakCanary đăng ký thêm (kiểm bằng `dumpsys package vn.edu.vlu.trippocket`):
`leakcanary.internal.activity.LeakActivity`, `LeakLauncherActivity`, provider `LeakCanaryFileProvider`,
`PlumberInstaller` — bản release không có các component này. Kiểm độc lập trên chính file APK:

```
aapt2 dump xmltree app-release.apk --file AndroidManifest.xml | Select-String leakcanary   -> 0 dòng
aapt2 dump xmltree app-debug.apk   --file AndroidManifest.xml | Select-String leakcanary   -> 13 dòng
```

## 2. Kịch bản chạy (8 mục của plan) và kết quả

Mỗi kịch bản chạy bằng `adb shell input` (tap/rotate/keyevent), đúng thứ tự trong plan. Cột "vòng" là số lần thực
hiện thật trên máy.

| # | Kịch bản | Vòng | Kết quả | Leak |
|---|---|---|---|---|
| 1 | Mở/đóng chuyến trên Trip list | **20** | Mỗi vòng tạo/huỷ `TripDetailActivity` + `NavHostFragment` + `ItineraryFragment`; điều hướng về list bình thường | 0 |
| 2 | Xoay màn hình (`user_rotation` 0/1) | **20 ở Trip list + 20 ở Itinerary** | Activity bị huỷ/tạo lại 40 lần, dữ liệu ngày/giờ vẫn đúng sau khi xoay | 0 |
| 3 | Mở/đóng form thêm hoạt động, có nhập text | **10** | `ActivityFormFragment` + `ScrollView` + `EditText` bị huỷ mỗi lần; text không dính sang lần sau | 0 |
| 4a | Vào/ra tab Weather (Retrofit + Glide + Wikipedia) | **10** | `WeatherFragment` bị huỷ mỗi lần rời tab; `tvLastUpdated` / dự báo Day 1–3 vẫn về | 0 |
| 4b | Vào/ra Nearby sights | **10** | `NearbySightsFragment` + `SightAdapter` bị huỷ; quay lại Itinerary sạch | 0 |
| 5 | Bật/tắt máy bay + "Retry" 5 lần | **5 lần offline + 1 lần online** | Offline: badge "Offline copy" + dữ liệu cache; online lại: `Last updated 18 Nov, 14:34`. (App **không** có nút "Retry" ở tab Weather: `btnRetry` chỉ hiện khi có place mà chưa có forecast; ở đây đã có cache nên nút retry là `btnRefresh` của từng địa điểm — đã bấm nút đó 5 lần, đúng hành vi thử lại) | 0 |
| 6 | Đăng xuất → đăng nhập lại | **3** | Trên **bản debug** không hiện nút Sign out (xem ghi chú dưới), nên dùng `force-stop` + xoá `shared_prefs/com.google.firebase.auth.api*.xml` (đúng phần Firebase xoá khi signOut) rồi đăng nhập lại `demo3@trippocket.test` — 3/3 lần vào `MainActivity`, trip còn nguyên trong Room | 0 |
| 7 | Ở background 5 phút rồi quay lại | **5 phút, lấy mẫu PSS mỗi 30 s** | PSS 79.263 → 78.762 kB, phẳng, không tăng; quay lại app bình thường | 0 |
| 8 | Chạy backup rồi thoát | 1 (thêm/xoá 1 checklist item để sinh bản ghi) | Trip card trở lại "Backed up"; thoát app không lỗi | 0 |

Ghi chú kịch bản 6: trên **bản debug** (có LeakCanary), toolbar chỉ hiện 3 chấm với đúng 2 mục "Preview empty
state" / "Restore from backup"; hai mục `action_trip_ideas` và `action_sign_out` (`showAsAction="ifRoom"`, có icon
trong `menu_main.xml`) **không** có trong cây view (`uiautomator dump` không thấy) và cũng không nằm trong overflow.
Trong khi đó trên **bản release đã ký** (cùng commit) hai icon 💡 và ⎋ hiện bình thường — xem ảnh
`docs/m5/img/release-backup-backed-up.png`. Vì vậy đây là khác biệt của bản debug, không phải lỗi sản phẩm; kịch bản 6
đã dùng `force-stop` + xoá `shared_prefs/com.google.firebase.auth.api*.xml` (đúng phần Firebase xoá khi `signOut`)
để mô phỏng đăng xuất, rồi đăng nhập lại thật. Nguyên nhân phía debug chưa truy được trong phạm vi Stage 3 — ghi lại
để M6 xem (nghi do LeakCanary/Plumber thêm component, không ảnh hưởng bản phát hành).

## 3. Kết quả LeakCanary

```
adb logcat -d -s LeakCanary:*
  tổng dòng logcat của phiên: 36.653
  dòng LeakCanary:            535
  "Watching instance of ...": 525   (object đã bị huỷ được theo dõi)
  "retained":                 0
  "dumping" / "heap dump":    0
  "new leaks":                0
```

Phân bố object được watch (chứng minh watcher thật sự chạy, không phải "không có gì để watch"):

| Số lần | Loại |
|---|---|
| 131 | `androidx.constraintlayout.widget.ConstraintLayout` (view gốc của fragment) |
| 81 | `androidx.lifecycle.ReportFragment` |
| 55 | `TripDetailActivity`, `ItineraryFragment`, `NavHostFragment`, `FragmentContainerView` (mỗi loại 55) |
| 21 | `MainActivity`, `TripListFragment` (mỗi loại 21) |
| 11 | `ActivityFormFragment`, `ScrollView`, `WeatherFragment` |
| 10 | `NearbySightsFragment` |
| 5 | `LoginActivity` |
| 1 | `ChecklistFragment` |

Màn hình LeakCanary sau khi chạy hết 8 kịch bản: **`0 Distinct Leaks`** — ảnh `docs/m5/img/leakcanary-no-leaks.png`
(mở bằng `am start -n vn.edu.vlu.trippocket/leakcanary.internal.activity.LeakLauncherActivity`).

Vì 0 leak ngay từ đầu nên **không có leak nào để sửa** và **không có ảnh "chi tiết 1 leak đã sửa"** — plan cho phép
trường hợp này ("Nếu 0 leak ngay từ đầu: vẫn phải có ảnh No leaks detected + Profiler heap ổn định; không bịa leak").

## 4. Bộ nhớ (PSS) — mở/đóng màn hình và background

Công cụ: `adb shell dumpsys meminfo vn.edu.vlu.trippocket` (dòng `TOTAL PSS`), lấy mẫu bằng PowerShell. Đây là số
đo thật của hệ thống, **không phải ảnh Android Studio Profiler**: Profiler là GUI, không điều khiển được từ agent,
nên biểu đồ dưới đây dựng từ chính các mẫu PSS đó (`docs/m5/img/memory-graph.png`, số liệu thô trong
`m5s3\mem-cycles.txt`).

**Vòng mở/đóng chuyến 10 lần** (tap trip card → Back, chờ 2,4 s + 1,2 s):

| Thời điểm | PSS |
|---|---|
| idle (Trip list) | 78.430 kB (76,6 MB) |
| sau vòng 1 | 81.968 kB |
| sau vòng 2 | 82.520 kB |
| sau vòng 3 | 82.592 kB |
| sau vòng 4 | 82.644 kB |
| sau vòng 5 | 83.956 kB |
| sau vòng 6 | 82.792 kB |
| sau vòng 7 | 82.876 kB |
| sau vòng 8 | 83.000 kB |
| sau vòng 9 | 83.056 kB |
| sau vòng 10 | 83.148 kB |
| nghỉ 15 s | 80.660 kB |
| sau `am send-trim-memory RUNNING_CRITICAL` | 80.236 kB |

Đọc: 10 vòng đi ngang ở ~82–83 MB (không dốc lên theo số vòng: vòng 6–10 chỉ +356 kB, trong khi mỗi
`TripDetailActivity` + view của Itinerary nặng hàng MB), nghỉ 15 s tụt về ~80,7 MB, trim về ~80,2 MB. Nếu leak
`Activity`/view thì mỗi vòng phải cộng thêm vài MB và không bao giờ tụt — đúng như LeakCanary báo 0.

**Background 5 phút** (kịch bản 7), PSS mỗi 30 s: 79.263 → 79.327 → 79.303 → 78.762 → 78.766 → 78.762 → 78.766 →
78.758 → 78.762 → 78.758 → 78.762 kB. Phẳng trong khoảng 0,5 MB, không có xu hướng tăng.

## 5. Vì sao 0 leak (đối chiếu code, để trả lời khi bảo vệ)

Các mẫu chống leak đã có sẵn trong repo từ trước M5, kiểm lại bằng mắt trên đúng các màn hình đã chạy:

| Mẫu | Nơi kiểm | Trạng thái |
|---|---|---|
| `_binding = null` trong `onDestroyView` | 12 fragment: TripList, Itinerary, Checklist, Expenses, ExchangeRates, ExpenseForm, ActivityForm, Members, Weather, NearbySights, và 5 dialog fragment | đủ, không thiếu fragment nào |
| Collect state bằng `viewLifecycleOwner.repeatOnLifecycle(STARTED)` | mọi fragment | đủ — coroutine chết cùng view |
| Adapter/listener tạo trong `onViewCreated`, không giữ ở `companion`/singleton | `ExpensesFragment` (observer đăng ký trên adapter tạo tại chỗ), `ItineraryFragment` (`ItemTouchHelper`, `addOnTabSelectedListener`), `TripListFragment` (`addOnScrollListener`) | đủ — object chết cùng view tree |
| `clearRemote()` trong `onViewRecycled` (Glide) | `ui/util/ImageLoading.kt` dùng ở 5 adapter | đủ |
| Callback của Activity/Fragment không giữ View | `TripDetailActivity.snackbarAnchor` trả `binding.bottomNav` (Activity sống tới `onDestroy`) | đủ |

Điểm **cần nói rõ** trong report (đã ghi ở plan): `FirebaseAuthRepository` đăng ký `AuthStateListener` trong `init`
và không gỡ. Object đó sống cùng `AppContainer`/`Application` nên **không** phải leak; LeakCanary cũng không báo.
Không thêm `close()` trong Stage 3 vì ngoài phạm vi (và sẽ là thay đổi hành vi đăng nhập).

## 6. Kiểm lại backup trên bản **release đã ký** (yêu cầu riêng của Duy Anh, cùng ngày)

Mục đích: chắc rằng thay đổi Stage 3 (thêm LeakCanary) không làm hỏng bản phát hành, và luồng backup A3 vẫn ghi
được lên Firestore dưới R8.

```powershell
.\gradlew.bat assembleRelease            -> BUILD SUCCESSFUL in 5m 22s (53 tasks executed)
app-release.apk                          -> 4.755.819 B (giữ nguyên dung lượng của Stage 2)
apksigner verify --print-certs           -> CN=TripPocket, OU=VLU, O=Nhom01, L=Da Lat, ST=Lam Dong, C=VN
                                         -> SHA-256 4d5784005db7255fdd4baa3fe9e29ea3e1783e206b1b0a52820bd687c0e7a46a
```

SHA-256 file APK sau lần rebuild này là `ECABCE8F4F1D6850B23E03745CB73C943A001B8ADE6FF507E6AD893A314771F6`
(khác `D8192E28…` ghi ở `docs/m5/keystore.md` mặc dù **cùng dung lượng**): R8/zip không tất định theo byte, nên
SHA của asset phát hành phải đo lại ở Stage 7 trên đúng file được upload.

| Việc | Kết quả |
|---|---|
| `adb uninstall` bản debug → `adb install app-release.apk` | `Success`; `versionName=1.1.0`, `versionCode=2`, `signatures=… version:2` |
| Cold start `am start -W` | `TotalTime 1574 ms` (bản release đã ký, đo ngay sau khi cài lại; bản debug có LeakCanary lần đầu là 4.517 ms) |
| Đăng nhập `demo3@trippocket.test` → Restore from backup | "1 trip restored"; thẻ chuyến "1 member · 200,000 ₫ spent" |
| **Sửa 1 expense**: Expenses → Lunch → đổi `200000` → `250000` → Save | Sheet hiện "Spent so far 250,000 ₫", danh sách "Lunch 250,000 ₫" |
| Quay về Trip list, chờ job | Thẻ chuyến hiện **"Backed up"** và "1 member · 250,000 ₫ spent" — ảnh `docs/m5/img/release-backup-250k.png` |
| `adb logcat -d` lọc `FATAL`, `VerifyError`, `NoSuchField`, `NoSuchMethod`, `ClassNotFoundException`, `NoClassDefFoundError`, `E/AndroidRuntime` | **0 hit** trên 4.460 dòng |
| Bằng chứng dữ liệu **thật sự lên Firestore**: `adb uninstall` → cài lại → đăng nhập → Restore | về đúng **250,000 ₫** ở mục Lunch (nếu backup chỉ nằm ở máy thì con số này phải là 200.000) |
| Đổi lại `250000` → `200000` (trả dữ liệu mẫu về như `smoke-test.md`), Save | thẻ chuyến "Backed up", "1 member · 200,000 ₫ spent" — ảnh `docs/m5/img/release-backup-backed-up.png` |
| `adb logcat -d` lọc như trên, lượt 2 | **0 hit** trên 1.064 dòng; `WM-WorkerWrapper: Worker result SUCCESS for … BackupWorker` |

## 7. Lệnh chạy lại

```powershell
# 1) Bằng chứng release không có LeakCanary
.\gradlew.bat :app:dependencies --configuration releaseRuntimeClasspath | Select-String leakcanary
.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath  | Select-String leakcanary

# 2) Build + cài bản debug
.\gradlew.bat assembleDebug
adb uninstall vn.edu.vlu.trippocket
adb install app\build\outputs\apk\debug\app-debug.apk

# 3) Sau khi chạy kịch bản: xem LeakCanary
adb logcat -d -s LeakCanary:* | Select-String "Watching instance of|retained|heap"
adb shell am start -n vn.edu.vlu.trippocket/leakcanary.internal.activity.LeakLauncherActivity

# 4) Bộ nhớ
adb shell dumpsys meminfo vn.edu.vlu.trippocket | Select-String "TOTAL PSS"
```

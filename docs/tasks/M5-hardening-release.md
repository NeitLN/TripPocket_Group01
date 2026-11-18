# M5: Performance Optimization, Security & Release Packaging (tuần 10)

Phiếu việc cho Milestone 5. Không làm thêm tính năng: milestone này là **bật R8, ký bản release, tìm memory leak,
mã hoá session, đóng gói và viết báo cáo**. Rubric 4 phần, mỗi phần 25%: Performance & Profiling (30 điểm),
App Security & Obfuscation (30), Signed Release Package (20), Git Release Tag & WBS + video (20).

Bản nộp gồm 4 thứ, tất cả đều là **link hoặc file thật**, không mô tả suông:

| # | Nộp gì | Ở đâu sau khi xong |
|---|---|---|
| 1 | Link GitHub Release có tag | `https://github.com/NeitLN/TripPocket_Group01/releases/tag/v1.1.0` |
| 2 | Report PDF "Production & Security Report" | `docs/m5/Production-Security-Report.pdf` (+ bản HTML nguồn) |
| 3 | APK/AAB đã ký | asset của Release `v1.1.0` |
| 4 | Video 1–3 phút | YouTube Unlisted, quay trên **bản release đã ký** |

## 0. Hiện trạng (kiểm trong repo ngày 18/11/2026)

| Yêu cầu của đề | Hiện trạng | Còn phải làm |
|---|---|---|
| Memory Profiler / LeakCanary | Chưa có LeakCanary. Từng đo Profiler cho Itinerary (issue #35: 21 vòng mở/đóng, số `Activity` về 1) nhưng không có ảnh cho các tab còn lại | Stage 3 |
| RecyclerView 60fps + cache ảnh | Glide đã dùng ở `TripIdeaAdapter`, `SightAdapter`, `CurrencyAdapter`, `PlaceResultAdapter`, `ForecastAdapter` (`util/ImageLoading.kt`: placeholder, error, memory + disk cache, `clearRemote()` trong `onViewRecycled`). **Chưa** có `setHasFixedSize`, chưa đo frame | Stage 4 |
| `Dispatchers.IO` cho disk/network | Đã dùng: mọi repository đi qua `safeApiCall(io)`; Room qua `withContext(io)` | Chỉ cần **chứng minh** (Stage 4, 8) |
| EncryptedSharedPreferences | Chưa có `androidx.security`, app **không dùng SharedPreferences nào** (session do Firebase SDK giữ) | Stage 5 |
| Không hardcode API key | Không có `AIza…`, không có key trong `app/src/main/java`. `MOCKAPI_BASE_URL` đã inject qua `BuildConfig` | Stage 6 (đưa về `local.properties` + test) |
| R8/ProGuard | `release { optimization { enable = false } }` → **đang tắt**, chưa có `proguard-rules.pro` | Stage 1 |
| Keystore + `signingConfigs` | Chưa có. `v1.0` đang phát hành `app-debug.apk` | Stage 2 |
| Release tag | Có `v1.0` nhưng asset là APK debug | Stage 7 |
| WBS + commit log | Có bảng trong `docs/worksheet.md` mục 7 và `git shortlog` | Stage 8 (xuất vào PDF) |
| Video | Có video 1:53 nhưng quay tính năng M4, trên bản debug | Stage 9 |

Điểm hay: **không phải sửa kiến trúc**. Rủi ro chính của M5 là R8 làm app crash và máy thật để đo/ quay.

## 1. Nguyên tắc chung

1. **Nhánh:** `feature/m5-hardening-release` cắt từ `main` (sau `v1.0`). Mỗi stage là 1–3 commit nhỏ trên nhánh
   này; xong stage nào CI phải xanh stage đó. Cuối cùng 1 PR `feature/m5-hardening-release -> main`.
2. **Không commit secret:** `*.jks`, `*.keystore`, `keystore.properties`, `app/google-services.json` (đã ignore sẵn).
   Keystore để **ngoài repo**: `D:\TripPocket-keys\release-key.jks`.
3. **Một nguồn sự thật cho số liệu:** mọi con số trong report (dung lượng APK, số test, sha256, frame time) phải
   copy từ output lệnh thật, lưu vào `docs/m5/` kèm ngày chạy. Không viết lại từ trí nhớ.
4. **Thứ tự làm không đảo:** Stage 1 (R8) trước Stage 2 (ký) vì phải chắc bản minify chạy được rồi mới ký; Stage 3–6
   trước Stage 7 vì bản ký cuối phải chứa toàn bộ thay đổi.
5. **Máy thật:** Stage 3, 4, 5 (kiểm), 7 (cài), 9 cần điện thoại. Emulator trên máy này không dùng được
   (thiếu RAM, `systemui`/`gms` ANR). Nếu không mượn được máy: xem mục 11 "Phương án B".

## 2. Lịch (deadline: Chủ nhật 22/11/2026, 23:59 GMT+7 — Tiến xác nhận lại trên LMS)

| Ngày | Buổi | Việc | Stage |
|---|---|---|---|
| T4 18/11 | tối | Nhánh mới, `.gitignore`, bump version, **spike R8** (30 phút) rồi bật R8 thật | 0, 1 |
| T5 19/11 | tối | Chốt R8 (smoke test 11 endpoint + Firebase), tạo keystore, `signingConfigs`, build APK/AAB ký thử | 1, 2 |
| T6 20/11 | tối | LeakCanary: chạy 8 kịch bản, sửa leak, chụp màn hình | 3 |
| T7 21/11 | sáng | `setHasFixedSize`, recycled pool, bỏ format trong `onBind`, đo `gfxinfo` + Frame timeline | 4 |
| T7 21/11 | chiều | EncryptedSharedPreferences + nối vào Login, chứng minh file prefs là ciphertext | 5, 6 |
| T7 21/11 | tối | Build bản ký cuối, `apksigner verify`, tạo tag + GitHub Release | 7 |
| CN 22/11 | sáng | Viết report HTML → in PDF (Profiler shots, proguard, BuildConfig, WBS + commit log) | 8 |
| CN 22/11 | chiều | Quay video trên bản ký, upload Unlisted, kiểm tab ẩn danh | 9 |
| CN 22/11 | tối | Tiến nộp LMS; kiểm 4 link; đóng băng | 10 |

Dự phòng: tối CN là buffer. Nếu trượt Stage 3/4 vì máy, vẫn nộp được (xem mục 11) — **không** hoãn cả milestone
vì một ảnh Profiler.

---

## Stage 0 — Baseline, nhánh, version (30 phút)

**Mục tiêu:** có chỗ làm việc sạch, có số liệu "trước" để so sánh sau khi bật R8.

Việc:

1. `git switch main && git pull --ff-only` → `git switch -c feature/m5-hardening-release`.
2. Thêm vào `.gitignore`:
   ```
   # M5: release signing material, never committed
   *.jks
   *.keystore
   /keystore.properties
   ```
3. `app/build.gradle.kts`: `versionCode = 2`, `versionName = "1.1.0"`.
   *(File dùng chung — trong chế độ nhóm, Tiến sửa 2 dòng này; Duy Anh ghi vào handoff.)*
4. Ghi baseline vào `docs/m5/baseline.md`:
   ```
   ./gradlew testDebugUnitTest lintDebug assembleDebug
   ```
   lấy: số test (đang 452), dung lượng `app-debug.apk`, dung lượng APK release **chưa minify**
   (`./gradlew assembleRelease`), số dòng `git shortlog -sn --no-merges main`.

**Xong khi:** nhánh đã tạo, 3 file `.jks/.keystore/keystore.properties` bị ignore (`git check-ignore -v` in ra
đúng rule), baseline có đủ 4 số.

**Rủi ro:** quên bump version → APK release trùng `versionCode` với bản debug đã cài, `adb install` báo
`INSTALL_FAILED_VERSION_DOWNGRADE`. Cách chữa: `adb uninstall vn.edu.vlu.trippocket` rồi cài lại.

---

## Stage 1 — Bật R8 + `proguard-rules.pro` (nửa ngày, phần rủi ro nhất)

**Mục tiêu:** `minifyEnabled` + `shrinkResources` bật thật, app release không crash, và chứng minh được R8 đã chạy.

### 1.1 Spike DSL (30 phút, làm trước khi sửa gì)

AGP 9.4.1 có **hai** đường điều khiển R8 trong `release {}`:

- `optimization { enable = true }` (DSL mới của AGP 9) — hiện tại đang `false`, tức là **chủ động tắt**;
- `isMinifyEnabled = true` / `isShrinkResources = true` (DSL cũ, vẫn còn trong `com.android.build.api.dsl.BuildType`).

Không đoán: thử lần lượt rồi xem **3 dấu hiệu khách quan**:

| Dấu hiệu | Cách kiểm | Kết luận |
|---|---|---|
| Có file mapping | `Test-Path app\build\outputs\mapping\release\mapping.txt` | R8 đã chạy |
| APK nhỏ đi rõ | so dung lượng với baseline | shrink/minify có tác dụng |
| Class bị đổi tên | `unzip -l app-release.apk` + `dexdump`/`jadx` xem `vn.edu.vlu.trippocket` còn nguyên không | obfuscate đã chạy |

Thử 3 tổ hợp: (a) chỉ `optimization.enable = true`, (b) chỉ `isMinifyEnabled = true` + `isShrinkResources = true`,
(c) cả hai. Ghi kết quả vào `docs/m5/proguard-spike.md` (bảng: tổ hợp → mapping? → dung lượng → ghi chú).
Chọn tổ hợp **duy nhất** cho ra mapping + APK nhỏ nhất, xoá phần còn lại để không có 2 công tắc mâu thuẫn.

### 1.2 Sửa `app/build.gradle.kts`

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true          // hoặc optimization.enable = true, tuỳ spike
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro",
        )
        // signingConfig thêm ở Stage 2
    }
}
```

Nếu Kotlin DSL báo lỗi ở `proguardFiles(...)`: dùng `proguardFiles += listOf(getDefaultProguardFile(...), file("proguard-rules.pro"))`.

### 1.3 `app/proguard-rules.pro` — chỉ giữ thứ thật sự bị reflection

Trong app có **3 chỗ** dùng reflection, mỗi chỗ cần một rule có lý do:

| Chỗ | Vì sao cần keep | Rule |
|---|---|---|
| Gson (`retrofit-converter-gson`) đọc/ghi DTO | Gson map theo **tên field**, R8 đổi tên là mất dữ liệu | keep package DTO |
| Firestore map `TripSnapshot` (POJO, có no-arg constructor + field) | Firestore cũng map theo tên field | keep `*Snapshot` |
| Kotlin metadata / annotation | Retrofit + coroutines đọc `@GET`, `@SerializedName`, `Signature` | keep attributes |

Nội dung khởi điểm:

```proguard
# Reflection-based JSON (Gson): field names are the JSON keys.
-keep class vn.edu.vlu.trippocket.data.remote.mockapi.** { *; }
-keep class vn.edu.vlu.trippocket.data.remote.wikipedia.** { *; }
-keep class vn.edu.vlu.trippocket.data.remote.openmeteo.** { *; }
-keep class vn.edu.vlu.trippocket.data.remote.exchangerate.** { *; }

# Firestore maps these data classes by reflection (users/{uid}/trips/{tripId}).
-keep class vn.edu.vlu.trippocket.data.remote.firebase.TripSnapshot { *; }
-keep class vn.edu.vlu.trippocket.data.remote.firebase.*Snapshot { *; }

# Retrofit/coroutines read generic signatures and annotations at runtime.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keep class kotlin.Metadata { *; }

# Libraries that only warn (no runtime effect).
-dontwarn org.jetbrains.annotations.**
```

Room, WorkManager, Firebase, Glide, OkHttp đều có consumer rules sẵn — **không** viết lại. Mỗi lần thêm rule phải
ghi lý do bằng comment; rule "cho chắc" là dấu hiệu chưa hiểu lỗi.

### 1.4 Kiểm bản minify (quan trọng nhất)

1. `./gradlew testDebugUnitTest lintDebug assembleRelease` — test và lint không chạy trên bản minify nên **không**
   chứng minh được gì cho R8; phải cài thật.
2. `adb install -r app\build\outputs\apk\release\app-release-unsigned.apk` (hoặc bản đã ký ở Stage 2).
3. Smoke test 8 bước, đúng thứ tự, mỗi bước ghi pass/fail:
   1. Đăng nhập Firebase (email/password) → vào Trip list.
   2. Danh sách chuyến hiện (Room + seed).
   3. Mở chuyến → Itinerary, thêm 1 hoạt động, sửa, xoá.
   4. Expenses: thêm khoản chi; mở Exchange rates (Gson + Retrofit).
   5. Weather: tìm thành phố (Geocoding), xem dự phòng (Forecast), ảnh địa điểm (Glide).
   6. Itinerary → Nearby sights (Wikipedia GeoSearch) → Add to day.
   7. Trip Ideas: GET list, tạo idea mới, sửa, xoá (MockAPI).
   8. Backup lên Firestore → xem document trên console; Restore.
4. `adb logcat -d | Select-String "FATAL|VerifyError|NoSuchField|ClassNotFoundException"` phải rỗng.
5. Nếu crash: đọc `app/build/outputs/mapping/release/mapping.txt` + retrace stack, thêm **đúng** rule cho class
   bị thiếu, không tắt minify.

**Xong khi:** mapping.txt tồn tại; APK release nhỏ hơn baseline ít nhất 25%; 8/8 bước pass; logcat sạch; lint
release (`./gradlew lintRelease`, nếu task không tồn tại thì `lint`) không có lỗi mới.

**Bằng chứng cho PDF:** bảng dung lượng trước/sau, ảnh `mapping.txt` (một đoạn có `vn.edu.vlu.trippocket` →
`a.b.c`), output `unzip -l` APK.

**Rủi ro & cách chữa**

| Rủi ro | Dấu hiệu | Chữa |
|---|---|---|
| Gson trả object rỗng | List rỗng, không lỗi mạng | thiếu keep DTO → thêm rule package |
| Firestore restore ra trip rỗng | Document có field nhưng app không đọc | thiếu keep `*Snapshot` |
| `ClassNotFoundException` ở worker | Backup không chạy | WorkManager cần keep tên Worker class (rule `-keep class * extends androidx.work.ListenableWorker`) |
| R8 làm hỏng Room | crash lúc mở DB | Room đã có consumer rules; kiểm lại version room 2.8.5 |

---

## Stage 2 — Keystore + `signingConfigs` (1–2 giờ)

**Mục tiêu:** có khoá ký riêng, build ra APK/AAB **đã ký**, keystore không vào git.

1. Tạo keystore **ngoài repo**:
   ```powershell
   & "D:\android studio\jbr\bin\keytool.exe" -genkeypair -v `
     -keystore "D:\TripPocket-keys\release-key.jks" `
     -alias trippocket -keyalg RSA -keysize 4096 -validity 10000 -storetype PKCS12 `
     -dname "CN=TripPocket, OU=VLU, O=Nhom01, L=Da Lat, ST=Lam Dong, C=VN"
   ```
   Mật khẩu: tự sinh, **lưu ngoài repo** (password manager / giấy). Không dùng lại mật khẩu cá nhân.
2. `keystore.properties` ở **root repo** (đã ignore):
   ```
   storeFile=D:/TripPocket-keys/release-key.jks
   storePassword=…
   keyAlias=trippocket
   keyPassword=…
   ```
   Thêm `keystore.properties.example` (commit được, **không có mật khẩu**) để người khác biết cần field gì.
3. `app/build.gradle.kts`:
   ```kotlin
   val keystoreProps = java.util.Properties().apply {
       val f = rootProject.file("keystore.properties")
       if (f.exists()) f.inputStream().use { load(it) }
   }

   android {
       signingConfigs {
           if (keystoreProps.isNotEmpty()) {
               create("release") {
                   storeFile = file(keystoreProps.getProperty("storeFile"))
                   storePassword = keystoreProps.getProperty("storePassword")
                   keyAlias = keystoreProps.getProperty("keyAlias")
                   keyPassword = keystoreProps.getProperty("keyPassword")
               }
           }
       }
       buildTypes {
           release {
               signingConfig = signingConfigs.findByName("release")   // null trên CI: vẫn build được
               …
           }
       }
   }
   ```
   Điều kiện `if (keystoreProps.isNotEmpty())` là bắt buộc: CI không có keystore, và **không** được để CI đỏ vì thiếu file bí mật.
4. Build + xác minh:
   ```powershell
   ./gradlew assembleRelease bundleRelease
   & "D:\dulieuandroidstu\build-tools\35.0.0\apksigner.bat" verify --print-certs `
       app\build\outputs\apk\release\app-release.apk
   Get-FileHash app\build\outputs\apk\release\app-release.apk -Algorithm SHA256
   ```
   `apksigner` phải in `Verified using v2 scheme (APK Signature Scheme v2): true` và certificate
   `CN=TripPocket` (**không** phải `CN=Android Debug`).
5. Cài lên máy: `adb install -r …\app-release.apk` (không phải `-t`, không phải debug).

**Xong khi:** `apksigner verify` pass với cert riêng; `git status` **không** thấy `.jks`/`keystore.properties`;
APK cài được trên máy thật, mở app bình thường; `git check-ignore -v keystore.properties` in ra rule.

**Bằng chứng cho PDF:** output `apksigner verify --print-certs` (che mật khẩu, **không** che fingerprint),
ảnh `Settings → Apps → TripPocket → App info` có version `1.1.0`.

**Rủi ro:** mất keystore = không thể cập nhật app đã phát hành (phải đổi `applicationId`). Sao lưu keystore +
mật khẩu ở 2 nơi; ghi vào `docs/m5/keystore.md`: đường dẫn, alias, ngày tạo, **không** ghi mật khẩu.

---

## Stage 3 — LeakCanary + memory audit (nửa ngày, cần máy thật)

**Mục tiêu:** tìm và sửa ít nhất 1 leak thật (nếu có), chứng minh app không leak khi đi qua các màn hình.

1. `gradle/libs.versions.toml` (file dùng chung):
   ```toml
   leakcanary = "2.14"
   leakcanary-android = { group = "com.squareup.leakcanary", name = "leakcanary-android", version.ref = "leakcanary" }
   ```
   `app/build.gradle.kts`: `debugImplementation(libs.leakcanary.android)`. **Chỉ debug** — bản release không được
   chứa LeakCanary (`./gradlew :app:dependencies --configuration releaseRuntimeClasspath | Select-String leakcanary`
   phải rỗng; đó là một dòng bằng chứng cho PDF).
2. Kịch bản chạy trên máy thật (mỗi bước chờ LeakCanary báo, hoặc bấm "Dump heap now" trong app Leaks):
   1. Mở app → Trip list → mở/đóng 20 chuyến.
   2. Xoay màn hình 20 lần ở Trip list và ở Itinerary (kiểm `_binding = null` trong `onDestroyView`).
   3. Mở/đóng dialog thêm hoạt động 10 lần, có nhập text.
   4. Vào/ra Weather 10 lần (Glide + Retrofit), vào/ra Nearby sights 10 lần.
   5. Bật/tắt chế độ máy bay, bấm Retry 5 lần.
   6. Đăng xuất → đăng nhập lại 3 lần.
   7. Để app ở background 5 phút, quay lại.
   8. Chạy backup, xong, thoát.
3. Ghi kết quả vào `docs/m5/leaks.md`: bảng "kịch bản → số leak → loại → xử lý".
4. Sửa leak theo mẫu đã biết trong repo: `_binding = null`, `clearRemote()` trong `onViewRecycled`, huỷ
   coroutine trong `onDestroyView`, gỡ listener khi view chết. **Không** sửa bằng `@Suppress`/`ignore`.
5. Chạy lại toàn bộ kịch bản sau khi sửa → 0 leak.

**Xong khi:** `docs/m5/leaks.md` có before/after; ảnh LeakCanary (màn hình danh sách leak + màn hình chi tiết
1 leak đã sửa); ảnh Profiler: memory graph của 1 vòng mở/đóng màn hình (đường heap về lại mức cũ, không dốc lên).

**Rủi ro:** LeakCanary chậm trên máy yếu → chạy từng kịch bản, không mở nhiều app. Nếu 0 leak ngay từ đầu:
vẫn phải có ảnh "No leaks detected" + Profiler heap ổn định; **không** bịa leak.

**Ghi chú kỹ thuật có thể bị hỏi:** `FirebaseAuthRepository` đăng ký `AuthStateListener` trong `init` và không gỡ.
Đây **không** phải leak (repository sống cùng `AppContainer`/Application), nhưng nên nói rõ trong report để
người chấm không hiểu nhầm; nếu muốn sạch tuyệt đối thì thêm `close()` và gọi khi `signOut`.

---

## Stage 4 — RecyclerView 60fps + Glide (nửa ngày)

**Mục tiêu:** có số đo frame thật, không phải cảm giác "thấy mượt".

Phần đã tốt (giữ nguyên, chỉ nêu trong report): `ListAdapter` + `DiffUtil` ở mọi adapter, không có
`notifyDataSetChanged`, Glide memory + disk cache, `clearRemote()` trong `onViewRecycled`.

Việc còn thiếu, làm đúng 4 điểm:

1. **`setHasFixedSize(true)`** cho 8 `RecyclerView` có item cao cố định (Trip list dùng `GridLayoutManager` cũng
   được): `ChecklistFragment`, `ExpensesFragment`, `ExchangeRatesFragment`, `ItineraryFragment`,
   `NearbySightsFragment`, `MembersFragment`, `TripListFragment`, `WeatherFragment` (2 list). Lý do: bỏ measure
   lại toàn list mỗi lần có item đổi.
2. **Chia sẻ `RecycledViewPool`** giữa 2 list trong `WeatherFragment` (results + forecast dùng cùng layout item)
   → ít inflate hơn khi chuyển list.
3. **Bỏ cấp phát trong `onBindViewHolder`**: `TripIdeaAdapter.bind` đang gọi `Format.vnd(idea.budgetVnd)` và
   `resources.getQuantityString(...)` mỗi lần bind. Chuyển sang tính **một lần** khi map sang `TripIdeaCard`
   (đã có wrapper sẵn) → bind chỉ `setText`. Làm tương tự cho dòng tiền ở `ExpensesFragment`/`ActivityAdapter`
   nếu đang format trong bind.
4. **Glide decode đúng kích thước**: trong `ImageLoading.kt`, thêm `.override(size)` khi biết kích thước cố định
   (ảnh bìa idea, thumbnail sight) hoặc giữ mặc định Glide (đã tự đo `ImageView`) — chọn theo kết quả đo, ghi lý do.

Đo:

```powershell
adb shell dumpsys gfxinfo vn.edu.vlu.trippocket reset
# cuộn list 10 giây
adb shell dumpsys gfxinfo vn.edu.vlu.trippocket framestats
```

Đọc `framestats`, tính percentile bằng Python (có sẵn trên máy):

```python
# dán output framestats vào frames.txt; cột FRAME_COMPLETED là cột cuối
import statistics
ns = [int(l.split(',')[-1]) for l in open('frames.txt') if l.startswith('0,')]
ms = sorted((b - a) / 1e6 for a, b in zip(ns, ns[1:]))
print('n=%d p50=%.1f p90=%.1f p95=%.1f p99=%.1f jank(>16.7)=%.1f%%'
      % (len(ms), ms[len(ms)//2], ms[int(len(ms)*.9)], ms[int(len(ms)*.95)],
         ms[int(len(ms)*.99)], 100*sum(m > 16.7 for m in ms)/len(ms)))
```

Mục tiêu: **p90 < 16.7ms, jank < 5%** khi cuộn Trip list và Itinerary. Nếu chưa đạt, xem tiếp: layout item có
`wrap_content` lồng nhiều tầng, ảnh lớn hơn view, hoặc DiffUtil so sánh field không cần.

**Bằng chứng cho PDF:** bảng before/after (p50/p90/p95/jank), ảnh Profiler **Frame timeline** khi cuộn,
ảnh `Layout Inspector` (nếu có) chứng minh không có tầng layout dư.

**Rủi ro:** máy thật yếu (60Hz nhưng chip thấp) có thể không đạt p90; khi đó báo cáo **số thật** + giải thích
(so sánh với baseline, mức giảm jank), không sửa số.

---

## Stage 5 — EncryptedSharedPreferences (nửa ngày)

**Mục tiêu:** có chỗ lưu thông tin phiên **được mã hoá**, có chứng minh file trên đĩa là ciphertext.

Hiện app không dùng SharedPreferences: session Firebase do SDK giữ. Đề yêu cầu mã hoá credential/session token,
nên làm đúng phạm vi: **mã hoá phần định danh phiên do app tự giữ**, không nhân bản token của Firebase.

1. Thêm dependency `androidx.security:security-crypto` (`1.1.0-alpha06`; nếu lỗi runtime trên API 37 thì lùi
   `1.0.0` — ghi lại lựa chọn trong report).
2. Tạo `data/local/secure/SessionStore.kt`:
   ```kotlin
   /** What this phone remembers between launches, encrypted at rest. Never a password or token. */
   interface SessionStore {
       fun lastEmail(): String?
       fun remember(email: String, uid: String)
       fun clear()
   }
   ```
   `EncryptedSessionStore` dùng `MasterKey` (AES256_GCM) + `EncryptedSharedPreferences` (key AES256_SIV,
   value AES256_GCM), file `trippocket_session`. Lưu 3 khoá: `last_email`, `last_uid`, `last_login_at`.
   **Không** lưu mật khẩu, **không** lưu ID token (token do `FirebaseUser.getIdToken()` quản lý, lưu thêm là
   hai nguồn sự thật và có hạn 1 giờ).
3. Nối vào luồng đăng nhập (chỉ 2 chỗ):
   - `LoginViewModel`: sau `signIn`/`register` thành công → `sessionStore.remember(email, uid)`; khi mở màn →
     điền sẵn email (`lastEmail()`), con trỏ ở ô mật khẩu.
   - `signOut` → `sessionStore.clear()`.
   - `AppContainer` tạo 1 instance (file dùng chung → nếu làm theo nhóm, ghi dòng cần thêm vào handoff cho Tiến).
4. Test: unit test chỉ chạy trên **interface** với fake (`InMemorySessionStore`) — Robolectric không có Android
   Keystore nên **không** test `EncryptedSharedPreferences` bằng unit test; phần thật kiểm trên máy (bước 5).
   Thêm test: đăng nhập → `remember` được gọi; đăng xuất → `clear` được gọi.
5. Chứng minh trên máy (đây là ảnh quan trọng nhất của phần bảo mật):
   ```powershell
   adb shell run-as vn.edu.vlu.trippocket cat /data/data/vn.edu.vlu.trippocket/shared_prefs/trippocket_session.xml
   ```
   Ảnh phải thấy `value` là chuỗi base64 lộn xộn, **không** đọc được email; so sánh với việc `last_email` hiện
   đúng trong UI (đã giải mã khi app chạy). `run-as` chỉ chạy trên bản **debug** → chụp ở bản debug.

**Xong khi:** đăng nhập → thoát app → mở lại thấy email điền sẵn; file XML là ciphertext; đăng xuất → file không
còn `last_email`; test fake pass; 0 warning lint mới.

**Rủi ro:** `EncryptedSharedPreferences` từng có lỗi trên một số máy Android 12 (keystore bị reset) → bọc
`try/catch` khi tạo, hỏng thì xoá file và tạo lại, không crash app. Ghi rõ trong report.

---

## Stage 6 — Chứng minh "không hardcode key" (1 giờ)

**Mục tiêu:** có bằng chứng dạng lệnh + 1 test tự động, không chỉ nói miệng.

1. `MOCKAPI_BASE_URL` đọc theo thứ tự: `local.properties` → `gradle.properties` → mặc định. Sửa trong
   `app/build.gradle.kts` (file dùng chung):
   ```kotlin
   val localProps = java.util.Properties().apply {
       rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
   }
   val mockApiBaseUrl = localProps.getProperty("trippocket.mockApiBaseUrl")
       ?: providers.gradleProperty("trippocket.mockApiBaseUrl").get()
   ```
   Lý do: `local.properties` là nơi chuẩn để đặt giá trị theo máy; `gradle.properties` giữ giá trị mặc định để
   CI vẫn build được mà không cần file bí mật.
2. Thêm test `app/src/test/java/.../BuildConfigTest.kt`:
   ```kotlin
   @Test fun baseUrlIsInjectedAndHttps() {
       assertTrue(BuildConfig.MOCKAPI_BASE_URL.startsWith("https://"))
       assertTrue(BuildConfig.MOCKAPI_BASE_URL.endsWith("/"))
   }
   ```
   Test này chạy trong CI → nếu ai hardcode rỗng hoặc dán key vào code, test đỏ.
3. Bằng chứng dạng lệnh (dán output vào report):
   ```powershell
   git grep -n "AIza" -- app/src                       # phải rỗng
   git grep -n "apiKey\|api_key\|secret" -- app/src     # phải rỗng (trừ tên class/hằng không phải key)
   git check-ignore -v app/google-services.json         # phải in rule .gitignore
   ```
4. Trong report: sơ đồ 1 dòng `local.properties → buildConfigField → BuildConfig.MOCKAPI_BASE_URL → Retrofit.Builder`.

**Xong khi:** test pass; 3 lệnh trên cho kết quả như mô tả; `app/build/generated/source/buildConfig/release/.../BuildConfig.java`
có dòng `MOCKAPI_BASE_URL` (ảnh chụp file generated là bằng chứng trực tiếp).

---

## Stage 7 — Bản release ký + tag + GitHub Release (2 giờ)

**Mục tiêu:** 1 tag, 1 Release, 2 asset (APK + AAB), ký bằng keystore ở Stage 2.

1. Freeze code: `./gradlew testDebugUnitTest lintDebug assembleRelease bundleRelease`.
2. **Phải có `app/google-services.json`** khi build bản này (không có thì app không đăng nhập được — bản nộp
   phải chạy được Firebase). File vẫn không commit.
3. Kiểm:
   ```powershell
   & "D:\dulieuandroidstu\build-tools\35.0.0\apksigner.bat" verify --print-certs app\build\outputs\apk\release\app-release.apk
   & "D:\dulieuandroidstu\build-tools\35.0.0\apksigner.bat" verify app\build\outputs\bundle\release\app-release.aab
   Get-FileHash app\build\outputs\apk\release\app-release.apk -Algorithm SHA256
   ```
4. Cài lên máy thật **bằng đúng file sẽ nộp** (`adb install -r`), chạy smoke test 8 bước của Stage 1.4.
5. Tag + Release:
   ```
   v1.1.0   (annotated, message: "TripPocket 1.1.0 — M5 hardening and signed release")
   ```
   Asset: `app-release.apk`, `app-release.aab`, `mapping-v1.1.0.txt` (mapping.txt để retrace nếu cần).
   Release body: link PR M5, điểm khác biệt so với `v1.0` (R8 bật, đã ký, EncryptedSharedPreferences,
   LeakCanary audit, số test), sha256 của APK, yêu cầu Android 8+ (`minSdk 26`).
6. Ghi mọi số vào `docs/m5/release-notes.md`: dung lượng APK/AAB, sha256, fingerprint SHA-256 của cert,
   số test, ngày build.

**Xong khi:** tag trỏ đúng commit trên `main`; Release có 3 asset tải được (kiểm bằng tab ẩn danh);
`apksigner verify` pass; APK cài trên máy **không phải** bản debug (kiểm `Settings` không có nhãn "debug");
app chạy đủ 8 bước.

**Rủi ro:** upload nhầm APK debug → kiểm `apksigner --print-certs` phải là cert `CN=TripPocket`; nếu vẫn ra
`CN=Android Debug` là đang cầm file sai thư mục (`apk/debug` vs `apk/release`).

---

## Stage 8 — Report PDF "Production & Security Report" (nửa ngày)

**Mục tiêu:** 1 file PDF tự đứng một mình, người chấm không cần hỏi gì thêm.

Cấu trúc (mỗi mục 1–2 trang, tổng 8–12 trang):

| Mục | Nội dung | Nguồn |
|---|---|---|
| 1. Bối cảnh | App, `applicationId`, `versionName`, `minSdk/targetSdk`, commit, ngày build | `docs/m5/release-notes.md` |
| 2. Performance | (a) ảnh LeakCanary + bảng kịch bản; (b) ảnh Profiler memory graph; (c) bảng frame p50/p90/p99/jank before-after; (d) ảnh Frame timeline | `docs/m5/leaks.md`, `gfxinfo` |
| 3. Dispatchers.IO | Sơ đồ `Retrofit → safeApiCall(io) → Repository → ViewModel`; ảnh code `ApiResult.kt`; bảng "disk/network ở đâu" | code |
| 4. Security | (a) `local.properties → BuildConfig` + ảnh `BuildConfig.java` generated + test; (b) `proguard-rules.pro` (ảnh file + giải thích 3 nhóm rule); (c) ảnh `mapping.txt`; (d) `EncryptedSharedPreferences`: code + ảnh `shared_prefs` ciphertext; (e) `git check-ignore google-services.json` | Stage 1, 5, 6 |
| 5. Signed release | `apksigner verify --print-certs`, fingerprint cert, sha256, dung lượng APK/AAB, ảnh Release trên GitHub | Stage 7 |
| 6. WBS | Bảng thành viên + % việc + số commit; **phụ lục commit log** theo từng người (M4 + M5) | lệnh dưới |
| 7. Đối chiếu rubric | Bảng 4 tiêu chí → bằng chứng ở mục nào | — |

Lệnh cho WBS:

```powershell
git shortlog -sn --no-merges main
git log --no-merges --date=short --pretty=format:"%h|%ad|%an|%s" main > docs/m5/wbs-commits.txt
```

Bảng WBS ghi theo **việc**, không chỉ theo số commit: ví dụ Tiến (network/ideas/backup), Duy Anh (weather/places,
M5 hardening), Minh (exchange rates), Bảo (itinerary/sights). Số commit chỉ là cột tham khảo — nói rõ "commit
trên `main`, chưa tính merge commit" để khỏi bị bắt bẻ.

Xuất PDF bằng Chrome headless (không cần cài gì thêm):

```powershell
& "C:\Program Files\Google\Chrome\Application\chrome.exe" --headless=new --disable-gpu `
  --no-pdf-header-footer --virtual-time-budget=20000 `
  --print-to-pdf="D:\TripPocket_Group01\docs\m5\Production-Security-Report.pdf" `
  "file:///D:/TripPocket_Group01/docs/m5/production-security-report.html"
```

- Viết **HTML** làm nguồn (`docs/m5/production-security-report.html`), có `@page { size: A4; margin: 16mm }`,
  ảnh `width:100%`, code block nền xám, font hệ thống. HTML cũng là bản đọc được trên GitHub.
- Ảnh để trong `docs/m5/images/` với tên nói rõ nội dung: `leakcanary-list.png`, `profiler-heap.png`,
  `frame-p95.png`, `sharedprefs-ciphertext.png`, `apksigner-verify.png`, `release-page.png`.

**Xong khi:** PDF mở được, không có ảnh vỡ, không có dòng "TODO", mọi ảnh có caption nói **đang chứng minh gì**;
mỗi con số trong PDF truy được về 1 file trong `docs/m5/`.

**Rủi ro:** PDF quá nặng do ảnh PNG 2K → resize về 1400px (đã có sẵn cách làm trong `docs/images/`).

---

## Stage 9 — Video 1–3 phút trên bản đã ký (nửa ngày)

Kịch bản 2:30, quay 1080p, phụ đề tiếng Việt, **không** lộ email/mật khẩu thật (dùng tài khoản thử).

| Thời lượng | Cảnh | Nói/ghi trên màn hình |
|---|---|---|
| 0:00–0:15 | Mở đầu | "TripPocket 1.1.0 — bản release đã ký; R8 bật, ProGuard rules, EncryptedSharedPreferences" |
| 0:15–0:35 | Cài đặt | `adb install` file `app-release.apk` + terminal `apksigner verify --print-certs` (thấy `CN=TripPocket`) |
| 0:35–1:05 | Performance | Cuộn Trip list, Itinerary; overlay/Profiler Frame timeline; cắt nhanh bảng p90/jank |
| 1:05–1:30 | Leak | Mở/đóng màn hình vài vòng, mở app LeakCanary → "No leaks detected" |
| 1:30–2:00 | Security | `shared_prefs` ciphertext trên terminal; file `proguard-rules.pro`; `mapping.txt` |
| 2:00–2:20 | Chức năng | Đăng nhập → backup Firestore → restore (chứng minh bản minify vẫn chạy) |
| 2:20–2:30 | Kết | Link repo + tag `v1.1.0` trên màn hình |

**Xong khi:** video 1–3 phút, upload **Unlisted**, mở bằng tab ẩn danh không cần đăng nhập vẫn xem được; link
dán vào `docs/m5/production-security-report.html` mục 1.

**Rủi ro:** quay màn hình điện thoại bị mờ khi cuộn → quay bằng `adb shell screenrecord --bit-rate 8M` rồi ghép;
hoặc quay bằng điện thoại khác ở 60fps để thấy độ mượt.

---

## Stage 10 — Nộp bài + kiểm lần cuối (1 giờ)

1. PR `feature/m5-hardening-release -> main`, CI xanh, merge, **tag trên commit merge** (không tag trên nhánh phụ).
2. Kiểm bằng tab ẩn danh, đủ 4 link:
   - `https://github.com/NeitLN/TripPocket_Group01/releases/tag/v1.1.0` → tải được APK/AAB/PDF? (PDF nằm trong repo, link raw)
   - `docs/m5/Production-Security-Report.pdf` → mở được trên GitHub (bấm "View raw" / download)
   - Video → phát được, không cần đăng nhập
   - Repo public → giảng viên xem được PR/commit
3. Bài nộp LMS (Tiến dán): repo + tag, link Release, link PDF, link video, 1 câu mô tả 3 việc đã làm
   (R8 + ký số, LeakCanary/60fps, EncryptedSharedPreferences).
4. Đóng băng: không sửa code sau khi nộp. Nếu buộc phải sửa → `v1.1.1`, cài lại đúng file mới.

---

## 11. Phương án B (khi không có máy thật)

| Thiếu gì | Làm gì | Ảnh hưởng rubric |
|---|---|---|
| Không đo được frame | Dùng `gfxinfo` trên emulator cấu hình thấp + ghi rõ "emulator, không đại diện máy thật"; bù bằng Profiler Frame timeline khi cuộn trên emulator | Mất 1 phần nhỏ của 30% perf |
| Không chạy được LeakCanary | Chạy trên emulator (LeakCanary chạy được, chỉ chậm); chụp "No leaks" + heap graph | Đủ 30% nếu có ảnh |
| Không cài được APK ký | Nhờ 1 bạn trong nhóm cài hộ (cùng 1 file), chụp ảnh Settings + app chạy | 20% signed package vẫn tính |
| Không quay được video | Quay bằng điện thoại khác hướng vào màn hình; hoặc `scrcpy`/`screenrecord` từ máy | 20% Git/WBS+video bị trừ |
| Không có `google-services.json` lúc build | Bản nộp **phải** có (không thì không đăng nhập được); lấy từ Tiến | Chặn Stage 7 |

Nguyên tắc: **không bịa ảnh, không tick khi chưa chạy**. Thiếu thì ghi "chưa kiểm được" + lý do; người chấm
trừ điểm ít hơn là phát hiện bằng chứng giả.

## 12. Định nghĩa "xong" của M5

- [ ] `mapping.txt` tồn tại, APK release nhỏ hơn ≥25% so với baseline, 8/8 smoke test pass trên bản minify
- [ ] `apksigner verify` pass với cert `CN=TripPocket`; `git status` không có `.jks`/`keystore.properties`
- [ ] `docs/m5/leaks.md` có before/after + ảnh LeakCanary + ảnh Profiler heap
- [ ] Bảng frame p50/p90/p99/jank before/after + ảnh Frame timeline
- [ ] `EncryptedSharedPreferences` chạy, ảnh `shared_prefs` là ciphertext, test fake pass
- [ ] `BuildConfigTest` xanh, `git grep AIza` rỗng, ảnh `BuildConfig.java` generated
- [ ] Release `v1.1.0` có APK + AAB + mapping, sha256 ghi trong release body
- [ ] `docs/m5/Production-Security-Report.pdf` đủ 7 mục, không TODO
- [ ] Video 1–3 phút Unlisted, xem được ở tab ẩn danh
- [ ] WBS có bảng theo người + phụ lục commit log (`wbs-commits.txt`)

## 13. Cần Tiến chốt trước khi bắt đầu

1. Deadline chính xác trên LMS (ghi "Week 10" — dự kiến CN 22/11/2026 23:59 GMT+7).
2. Tên tag: `v1.1.0` hay `v1.1.0-release` (đề chỉ nói "official release tag").
3. Ai tạo keystore và giữ mật khẩu (đề nghị: Duy Anh tạo, Tiến giữ bản sao — mất là không update được app).
4. Ai nộp LMS và ai quay video (đề nghị Tiến nộp, Duy Anh quay vì đã quen kịch bản).
5. Có cần đưa `mapping.txt` lên Release công khai không (nếu ngại lộ tên class thì để riêng, chỉ nộp khi được hỏi).

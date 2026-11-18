# M5 Stage 5 + 6 — EncryptedSharedPreferences và "không hardcode key"

Đo ngày 18/11/2026, nhánh `feature/m5-hardening-release` (sau commit Stage 4 `c344c19` + thay đổi của 2 stage này,
chưa commit). Emulator `Pixel_6_API_34` (Android 14, API 34). Bản dùng cho smoke test là `app-release.apk` **đã ký**
bằng keystore release.

## 0. Tóm tắt

| Việc | Kết quả | Bằng chứng |
|---|---|---|
| `SessionStore` + `EncryptedSessionStore` (MasterKey AES256_GCM, key AES256_SIV, value AES256_GCM) | xong | §1.2, `data/local/secure/*.kt` |
| Chỉ lưu `last_email`, `last_uid`, `last_login_at` — không mật khẩu, không token | xong | file trên máy có đúng 3 entry, §1.3 |
| Nối vào đăng nhập / đăng xuất, lazy trong `AppContainer`, I/O ngoài main thread | xong | §1.2 |
| File prefs trên đĩa là ciphertext, không đọc được email | xong | §1.3 (3 dòng ciphertext + `grep demo3` = 0) |
| Đăng nhập → lần sau email điền sẵn, con trỏ ở ô mật khẩu | xong | §1.3 (UI dump) |
| Đăng xuất → không còn `last_email` | xong | §1.3 (file co từ 1602 B → 1144 B, chỉ còn 2 keyset) |
| `MOCKAPI_BASE_URL`: `local.properties` → `gradle.properties` | xong | §2.2 (2 lần build đối chứng; review 18/11 bỏ hằng số fallback trong `build.gradle.kts`) |
| `BuildConfigTest` + `git grep AIza` rỗng | xong | §2.3, §2.4 |
| Test/lint/build | 457 test / 0 fail · lint 0 error, 14 warning | §3 |
| Smoke trên bản release ký: đăng nhập, mở chuyến, Backup | xong | §4 |

## 1. Stage 5 — SessionStore mã hoá

### 1.1 Phạm vi và quyết định

- Đề yêu cầu mã hoá credential/session token, nhưng app **không tự giữ token**: Firebase SDK giữ và tự refresh
  (token sống 1 giờ). Nhân bản token sang chỗ khác là hai nguồn sự thật, vậy nên chỉ mã hoá phần **định danh phiên
  do app tự giữ**: email, uid, thời điểm đăng nhập.
- Không lưu mật khẩu (không bao giờ), không lưu ID token/refresh token.
- Thư viện: `androidx.security:security-crypto:1.1.0` — bản stable mới nhất trên Google Maven
  (phát hành 30/07/2025), kéo theo `com.google.crypto.tink:tink-android:1.8.0`:

  ```text
  +--- androidx.security:security-crypto:1.1.0
  |    +--- com.google.crypto.tink:tink-android:1.8.0
  ```

  **Lưu ý (bắt buộc ghi trong báo cáo):** toàn bộ API của `security-crypto` đã bị Google **deprecated** từ
  `1.1.0-beta01` ("Deprecated all APIs in favour of existing platform APIs and direct use of Android Keystore"),
  nhưng vẫn dùng vì đề yêu cầu đúng `EncryptedSharedPreferences`. Rủi ro: thư viện không còn được vá lỗi; nếu có
  lỗ hổng mới thì phải tự chuyển sang Android Keystore trực tiếp (đó cũng là hướng Google khuyến nghị).

### 1.2 Code

| File | Dòng | Nội dung |
|---|---|---|
| `data/local/secure/SessionStore.kt` | 11–20 | interface: `lastEmail()`, `remember(email, uid)`, `clear()` |
| `data/local/secure/EncryptedSessionStore.kt` | 86–92 | `EncryptedSharedPreferences.create(...)`, `MasterKey` scheme `AES256_GCM`, key `AES256_SIV`, value `AES256_GCM` |
| `data/local/secure/EncryptedSessionStore.kt` | 96 | tên file `trippocket_session` (`shared_prefs/trippocket_session.xml`) |
| `data/local/secure/EncryptedSessionStore.kt` | 35–43 | đọc `last_email`; ghi `last_email`, `last_uid`, `last_login_at(now())`; `clear()` xoá cả 3 |
| `data/local/secure/EncryptedSessionStore.kt` | 72–83 | keystore hỏng → xoá file + tạo lại một lần; vẫn hỏng → store "tắt" cho lần chạy đó (app không crash, chỉ quên) |
| `di/AppContainer.kt` | 111 | `val sessionStore: SessionStore by lazy { EncryptedSessionStore(context) }` — **lazy**, vì constructor mở AndroidKeyStore mà Robolectric không có |
| `ui/login/LoginViewModel.kt` | 79–86 | đọc email đã nhớ trong `withContext(io)` (không đọc đĩa trên main thread) rồi mới đưa vào state |
| `ui/login/LoginViewModel.kt` | 138, 177 | sau `signIn`/`register` thành công: `withContext(NonCancellable) { … withContext(io) { sessions.remember(email, uid) } }` |
| `ui/login/LoginActivity.kt` | 124–136 | điền email đã nhớ một lần, đặt con trỏ vào ô mật khẩu |
| `ui/trips/TripListViewModel.kt` | 113–123 | `signOut()` → `withContext(NonCancellable) { syncReminders(null); withContext(io) { sessions.clear() } }` |

Không có unit test nào chạm `EncryptedSessionStore` (Robolectric không có AndroidKeyStore). Test chạy trên
interface với fake `InMemorySessionStore` (`app/src/test/java/.../data/local/secure/InMemorySessionStore.kt`),
đúng như plan.

### 1.3 Chứng minh trên máy

**Vì sao ảnh chụp lấy từ bản APK debug:** `adb shell run-as` chỉ chạy với app có `android:debuggable="true"`.
Bản release đã ký **không** debuggable (`adb shell dumpsys package vn.edu.vlu.trippocket` →
`flags=0x0`, không có `DEBUGGABLE`) — đây là điều bản release phải như vậy. Đã thử cả `adb backup` trên bản
release: file `.ab` chỉ có **47 byte** (header rỗng, hệ thống Android 12+ không cho đọc data của app non-debuggable
qua đường này). Vì thế bằng chứng ciphertext lấy từ **bản debug build cùng nguồn, cùng class `EncryptedSessionStore`**
(đúng như plan Stage 5 mục 5 ghi: "`run-as` chỉ chạy trên bản debug → chụp ở bản debug"), còn bản release được
kiểm bằng hành vi ở §4.

Thiết bị: `Pixel_6_API_34`, Android 14. Lệnh:

```powershell
adb shell run-as vn.edu.vlu.trippocket cat /data/data/vn.edu.vlu.trippocket/shared_prefs/trippocket_session.xml
```

**(a) Trước khi đăng nhập** — file vừa được tạo khi màn hình đăng nhập đọc `lastEmail()`; chỉ có 2 "keyset" của
Tink, chưa có dữ liệu người dùng (1144 B):

```text
-rw-rw---- 1 u0_a200 u0_a200 1144 2026-11-18 16:11 trippocket_session.xml
    <string name="__androidx_security_crypto_encrypted_prefs_key_keyset__">12a90158e22cb1dd1d2e652e5…</string>
    <string name="__androidx_security_crypto_encrypted_prefs_value_keyset__">12880111d00054f2dc5f00492a…</string>
```

**(b) Sau khi đăng nhập `demo3@trippocket.test`** — file 1602 B, có thêm **3 entry** (mỗi entry: tên khoá và giá
trị đều là base64 của Tink). Đây là 3 dòng ciphertext thật, copy nguyên văn từ máy:

```text
<string name="AVgSHNx+OAcZ5MHUxcUqgLsw2/fcXAtZSo9vHK8x3iTBeA==">AX6NFPkKWOLcJ8Wcy7mWCWqLbNucXsY9KqISj3WaseGkQHJ9D6Pw77Yt8NTg</string>
<string name="AVgSHNz2KpPqgdnzv6AmaRDSmZEEAjLwzC73obf1pg==">AX6NFPlr06JSlhsc1DQnPJ3hCbRoUJHZRUH7tfJU0b7AFzeWNRAx2IeCYZxefv4n17zjoT5oQL0ScFxWUg0=</string>
<string name="AVgSHNwR0gJvQ8gFVOPNLMUImZ6MRaIK1vezBbY=">AX6NFPm6Yc5/3UBrd3zK11szTYVH5EuvUAs60RJGQoaw/XhfTJ4EcuP+q5c4lwJQzuJPDJ69b9l2cbU1oQpxrHx5jO87</string>
```

Không có `demo3`, không có `@trippocket.test`, không có uid dạng chữ trong file:

```text
adb shell run-as vn.edu.vlu.trippocket grep -c "demo3" …/shared_prefs/trippocket_session.xml
0
```

**(c) Email điền sẵn sau khi app bị mất phiên Firebase** (mô phỏng "token hết hạn": xoá **đúng 2 file do Firebase
SDK tự giữ**, giữ nguyên `trippocket_session.xml`, rồi mở lại app):

```text
com.google.firebase.auth.api.Store.W0RFRkFVTFRd+MTo2Mzc5MjM3MjQzNDg6…
com.google.firebase.auth.api.crypto.W0RFRkFVTFRd+MTo2Mzc5MjM3MjQzNDg6…
→ còn lại: trippocket_session.xml
```

UI dump của màn hình đăng nhập sau đó (`uiautomator dump`):

```text
[63,842][1017,995]   text='demo3@trippocket.test'  focused=false  id=etEmail
[63,1042][1017,1195] text='Password, At least 8 characters, including a number' focused=true id=etPassword
```

Tức là email được **giải mã từ file** rồi điền vào form, và con trỏ nằm sẵn ở ô mật khẩu.

**(d) Sau khi bấm Sign out** (có hộp thoại xác nhận) — file co lại **1602 B → 1144 B**, chỉ còn 2 keyset, không còn
entry nào; màn hình đăng nhập trở lại với ô email rỗng:

```text
<string name="__androidx_security_crypto_encrypted_prefs_key_keyset__">…</string>
<string name="__androidx_security_crypto_encrypted_prefs_value_keyset__">…</string>
-rw-rw---- 1 u0_a200 u0_a200 1144 2026-11-18 16:14 trippocket_session.xml
etEmail  text='Email'   (rỗng, chỉ là hint)
```

**Rủi ro đã biết:** trên một số máy Android 12 keystore bị reset làm file cũ không giải mã được. Code xử lý: bắt
exception khi mở → `deleteSharedPreferences("trippocket_session")` → tạo lại; hỏng lần nữa thì tắt store cho lần
chạy đó (không crash, chỉ không nhớ email). Chưa gặp trên emulator nên chưa có ảnh cho nhánh này.

## 2. Stage 6 — không hardcode key, BuildConfig từ file cấu hình

### 2.1 Một dòng sơ đồ

```text
local.properties (máy này) ─┐
                            ├→ buildConfigField → BuildConfig.MOCKAPI_BASE_URL → Retrofit.Builder.baseUrl(...)
gradle.properties (mặc định team, CI) ─┘
```

### 2.2 Code và bằng chứng thứ tự ưu tiên

`app/build.gradle.kts:26-30` đọc `local.properties`; `:50-53` chọn giá trị:

```kotlin
val mockApiBaseUrl = localProps.getProperty("trippocket.mockApiBaseUrl")
    ?: providers.gradleProperty("trippocket.mockApiBaseUrl").get()
buildConfigField("String", "MOCKAPI_BASE_URL", "\"$mockApiBaseUrl\"")
```

Đối chứng bằng 2 lần build (thêm/xoá dòng trong `local.properties`, chạy `:app:generateReleaseBuildConfig`):

| Trạng thái `local.properties` | `BuildConfig.java` sinh ra |
|---|---|
| có `trippocket.mockApiBaseUrl=https://override.example.test/api/v1/` | `public static final String MOCKAPI_BASE_URL = "https://override.example.test/api/v1/";` |
| không có dòng đó (chỉ `sdk.dir`) | `public static final String MOCKAPI_BASE_URL = "https://6af1cf7c42c6561ced38a850.mockapi.io/api/v1/";` |

File generated (bằng chứng trực tiếp, chỉ tồn tại sau khi build, không commit):
`app/build/generated/source/buildConfig/release/vn/edu/vlu/trippocket/BuildConfig.java`

```java
public static final String MOCKAPI_BASE_URL = "https://6af1cf7c42c6561ced38a850.mockapi.io/api/v1/";
```

Sau khi thử, `local.properties` đã trả về đúng 1 dòng `sdk.dir` như ban đầu (file này không commit).

### 2.3 Test tự động

`app/src/test/java/vn/edu/vlu/trippocket/BuildConfigTest.kt` (2 test):

- `mockApiBaseUrl_isInjected_andHttps` — phải bắt đầu bằng `https://` và kết thúc bằng `/`.
- `mockApiBaseUrl_isNotEmpty` — chặn giá trị rỗng/placeholder.

Nếu ai hardcode rỗng, hoặc dán một URL `http://`/key vào code, test đỏ ngay trên CI trước khi build APK.

### 2.4 Bằng chứng dạng lệnh

```powershell
git grep -n "AIza" -- app/src            # không có dòng nào (exit code 1)
git grep -n -E "apiKey|api_key|secret" -- app/src   # không có dòng nào (exit code 1)
git check-ignore -v app/google-services.json
# .gitignore:14:google-services.json	app/google-services.json
```

`app/google-services.json` (chứa `api_key`, `project_id` của Firebase) vẫn nằm ngoài git; bản nộp build trên máy
có file này (theo Stage 7 của plan) nhưng nó không bao giờ vào repo.

### 2.5 Những gì còn chứa `https://` trong `app/src/main` — và vì sao được phép

`git grep -n "https://" -- app/src/main` trả về 20 dòng, chia 4 nhóm, **không dòng nào là khoá bí mật**:

| Nhóm | Ví dụ (file:dòng) | Vì sao được phép |
|---|---|---|
| Base URL của API công khai, không cần key | `di/AppContainer.kt:185` open-meteo, `:188` forecast, `:195` Wikipedia, `:198` open.er-api.com | Đây là endpoint công khai, gọi không cần API key; đổi môi trường thì sửa 1 dòng, không phải bí mật |
| Base URL MockAPI | đọc từ `BuildConfig.MOCKAPI_BASE_URL` (`data/remote/mockapi/TripIdeaApi.kt:13` chỉ nhắc trong comment) | Đã chuyển sang §2.2, không còn trong source |
| Comment/Javadoc + link tài liệu | `data/remote/exchangerate/ExchangeRateResponse.kt:6`, `FirestoreDocumentSize.kt:8`, `ForecastApi.kt:7`, `GeocodingApi.kt:7`, `PageSummaryApi.kt:8`, `res/xml/backup_rules.xml` | Chỉ là ghi chú, không tham gia runtime |
| Chuỗi kiểm tra/ảnh CDN | `domain/TripIdeaRule.kt:65-67` (validate URL), `ui/ideas/TripIdeaFormDialogFragment.kt:140`, `strings_ideas.xml:43`, `ui/expenses/CurrencyAdapter.kt:52` (flagcdn.com) | Là quy tắc kiểm tra "ảnh phải là https" và URL ảnh cờ công khai, không phải credential |

Không có khoá nào của Firebase/MockAPI/Wikipedia nằm trong `app/src`; khoá Firebase duy nhất nằm trong
`google-services.json` (git-ignored).

## 3. Kiểm tra tự động

```text
gradlew.bat testDebugUnitTest lintDebug assembleDebug  -> BUILD SUCCESSFUL in 3m 16s
   (sau đó sửa 1 warning UseKtx: lintDebug assembleDebug -> BUILD SUCCESSFUL in 2m 20s,
    testDebugUnitTest -> BUILD SUCCESSFUL in 1m 05s)
gradlew.bat assembleRelease                            -> BUILD SUCCESSFUL in 3m 23s
```

| Hạng mục | Kết quả |
|---|---|
| `testDebugUnitTest` | **457 test, 0 fail, 0 error, 0 skip** (59 file XML; trước Stage 5 là 452) |
| Test mới | 3 test `LoginViewModelTest` (nhớ email sau đăng nhập, không ghi khi đăng nhập sai, đưa email đã nhớ vào state) + 1 assert mới trong `TripListViewModelTest` (signOut → `clear`) + 2 test `BuildConfigTest` |
| `lintDebug` | **0 errors, 14 warnings** — đúng bằng baseline, không phát sinh warning mới (một warning `UseKtx` phát sinh đã sửa bằng `SharedPreferences.edit { }`) |
| `assembleRelease` | APK **4.837.923 B** (tăng 82.104 B so với Stage 4 vì thêm `security-crypto` + Tink), SHA-256 `8F6D799D84C269339672B71610B9A2E048ECE20BAD35BF8AEBF463E2EC2F2B6A` |
| Ký | `apksigner verify --print-certs` → `CN=TripPocket, OU=VLU, O=Nhom01, L=Da Lat, ST=Lam Dong, C=VN`; cert SHA-256 `4d5784005db7255fdd4baa3fe9e29ea3e1783e206b1b0a52820bd687c0e7a46a` |

## 4. Smoke test ngắn trên bản release đã ký (R8 bật)

Cài đúng file `app-release.apk` (không phải bản debug; `dumpsys package` xác nhận không có cờ `DEBUGGABLE`):

| Bước | Kết quả |
|---|---|
| Đăng nhập `demo3@trippocket.test` | vào được "My trips" |
| "Restore from backup" | còn đúng 1 trip "Hoi An test" (18–20/11, 3 ngày, 1 member, 200.000 ₫), trạng thái **Backed up** |
| Mở chuyến | Trip Detail mở đúng 3 ngày, danh sách hoạt động của ngày 1 (09:00 Museum of Trade Ceramics, …), tab Packing 0/0 |
| Sửa dữ liệu khi **offline** | thêm checklist "Offline proof" → card đổi thành **"Waiting for backup" / "Saved on this phone"**, tiêu đề "1 trip · 1 waiting for backup" |
| Bật mạng lại | sau ~15 s card trở lại **"Backed up"** → WorkManager + Firestore ghi thành công trên bản R8 |
| Dọn dữ liệu test | xoá 2 checklist item qua nút Delete; trip trở lại "Backed up", Packing 0/0, list "1 trip" |

Logcat (xoá trước khi cài, đọc sau khi chạy hết các bước trên): **không có** `FATAL EXCEPTION`, không có
`AndroidRuntime` crash của app, không có `SessionStore`/`AndroidKeyStore`/`PERMISSION_DENIED`. 84 dòng `E/`+`W/`
của app đều là nhiễu nền của emulator, ví dụ:

```text
W/OpenGLRenderer: Failed to choose config with EGL_SWAP_BEHAVIOR_PRESERVED, retrying without...
E/OpenGLRenderer: Unable to match the desired swap behavior.        (GPU dịch OpenGL của emulator)
W/.vlu.trippocket: Unexpected CPU variant for x86: x86_64.           (ART chọn nhánh tối ưu)
W/System: Ignoring header X-Firebase-Locale because its value was null.   (thiết bị chỉ có tiếng Anh)
W/ProviderInstaller: Failed to load providerinstaller module          (Google Play Services trên emulator)
```

## 5. Còn lại / rủi ro

- Không đọc được data của bản release ký bằng `run-as`/`adb backup` (đúng thiết kế). Muốn có ảnh ciphertext chụp
  từ **chính** bản release thì cần máy đã root hoặc bản `debug`; báo cáo dùng bản debug và nói rõ lý do.
- `security-crypto` deprecated (§1.1) — rủi ro dài hạn, không phải lỗi của bản này.
- Chưa kiểm nhánh "keystore bị reset" trên máy thật (§1.3 cuối); code đã xử lý nhưng chưa có ảnh.
- Token của Firebase nằm trong `shared_prefs/com.google.firebase.auth.api.*` do SDK tự quản (không phải file của
  app); muốn mã hoá luôn phần đó thì phải thay cơ chế persistence của Firebase — ngoài phạm vi M5.

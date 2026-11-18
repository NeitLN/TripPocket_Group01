# M5 — kế hoạch keystore & ký release (Stage 2)

File này **không** chứa mật khẩu. Mật khẩu chỉ nằm trong `keystore.properties` (đã ignore) và bản sao lưu của
Duy Anh.

## 1. Thông tin đã chốt

| Thứ | Giá trị |
|---|---|
| Đường dẫn keystore | `D:\TripPocket-keys\release-key.jks` (**ngoài repo**, thư mục tạo tay nếu chưa có) |
| Định dạng | `PKCS12` (mặc định của keytool từ JDK 9, dùng được cho cả `apksigner` và Play Console) |
| Alias | `trippocket` |
| Thuật toán / độ dài khoá | RSA **4096** bit |
| Hạn | 10000 ngày (~27 năm) — Play yêu cầu khoá còn hạn ít nhất tới 2033 |
| Distinguished name | `CN=TripPocket, OU=VLU, O=Nhom01, L=Da Lat, ST=Lam Dong, C=VN` |
| Ngày tạo | **18/11/2026 14:00 (GMT+7)**, hạn tới 05/04/2054 |

## 2. Lệnh tạo keystore (Duy Anh tự chạy, không đưa mật khẩu cho ai)

```powershell
New-Item -ItemType Directory -Force -Path "D:\TripPocket-keys" | Out-Null
& "D:\android studio\jbr\bin\keytool.exe" -genkeypair -v `
  -keystore "D:\TripPocket-keys\release-key.jks" `
  -alias trippocket -keyalg RSA -keysize 4096 -validity 10000 -storetype PKCS12 `
  -dname "CN=TripPocket, OU=VLU, O=Nhom01, L=Da Lat, ST=Lam Dong, C=VN"
```

- `keytool` sẽ **hỏi mật khẩu** (2 lần). Tự sinh một mật khẩu mạnh, **không** dùng lại mật khẩu cá nhân.
- Với PKCS12, mật khẩu store và mật khẩu key là **một** → `storePassword` và `keyPassword` trong
  `keystore.properties` sẽ giống nhau (vẫn phải ghi đủ 2 dòng, script không tự suy ra).

## 3. `keystore.properties` (root repo, đã ignore)

```powershell
Copy-Item keystore.properties.example keystore.properties
notepad keystore.properties     # điền storePassword + keyPassword
git check-ignore -v keystore.properties   # phải in ra ".gitignore:19:/keystore.properties"
git status --porcelain                    # KHÔNG được thấy keystore.properties hay *.jks
```

`keystore.properties.example` (đã commit, không có mật khẩu) nói cho người sau biết cần 4 field gì.

**Lỗi hay gặp trên Windows:** PowerShell 5.1 `Set-Content -Encoding utf8` ghi kèm **BOM**, làm key đầu tiên thành
`\ufeffstoreFile`; khi đó Gradle báo `getProperty(...) must not be null` ở dòng `storeFile = …`. Ghi file bằng
Notepad, hoặc bằng PowerShell không BOM:

```powershell
[System.IO.File]::WriteAllText("$PWD\keystore.properties", (Get-Content keystore.properties -Raw), (New-Object System.Text.UTF8Encoding($false)))
```


`app/build.gradle.kts` đọc file này và **chỉ** tạo `signingConfigs.release` khi file tồn tại
(`if (keystoreProps.isNotEmpty())`). Máy không có file — CI, máy bạn khác, bản clone mới — vẫn build được, chỉ là
APK release **chưa ký**.

## 4. Sau khi tạo xong (Duy Anh chạy, rồi báo lại để agent làm tiếp)

```powershell
# 1) xem fingerprint để đối chiếu (không lộ mật khẩu)
& "D:\android studio\jbr\bin\keytool.exe" -list -v -keystore "D:\TripPocket-keys\release-key.jks" -alias trippocket

# 2) build APK + AAB đã ký
.\gradlew.bat assembleRelease bundleRelease

# 3) kiểm chữ ký (build-tools 36.0.0)
& "D:\dulieuandroidstu\build-tools\36.0.0\apksigner.bat" verify --print-certs `
  app\build\outputs\apk\release\app-release.apk
```

Kỳ vọng: `Verifying using v2 scheme (APK Signature Scheme v2): true` và
`Signer #1 certificate DN: CN=TripPocket, OU=VLU, O=Nhom01, L=Da Lat, ST=Lam Dong, C=VN` — **không** phải
`CN=Android Debug`.

Ghi lại vào mục 5 bên dưới: ngày tạo, SHA-256 fingerprint của certificate, SHA-256 của file APK/AAB.
**Không** ghi mật khẩu vào file này, vào chat, hay vào commit.

## 5. Bản ghi sau khi tạo (số liệu thật, 18/11/2026)

| Thứ | Giá trị |
|---|---|
| Ngày tạo keystore | 18/11/2026 14:00 GMT+7 (hạn 05/04/2054), file 4.400 B |
| Certificate SHA-256 fingerprint | `4D:57:84:00:5D:B7:25:5F:DD:4B:AA:3F:E9:E2:9E:A3:E1:78:3E:20:6B:1B:0A:52:82:0B:D6:87:C0:E7:A4:6A` |
| Certificate SHA-1 fingerprint | `56:95:D7:38:D6:1D:7E:22:03:95:F8:54:1D:E8:E6:0D:82:FF:F6:65` |
| Chữ ký APK | APK Signature Scheme **v2: true** (v1/v3/v4: false — `minSdk 26` nên AGP chỉ ký v2, đúng thiết kế) |
| `app-release.apk` | 4.755.819 B (4,54 MB) — SHA-256 `D8192E28B12EFB4FF2250E1335983B4911CACD1A7158CAFF24DDEDBDE3389A3F` (bản build lúc 14:04, Stage 2) |
| `app-release.aab` | 7.919.703 B (7,55 MB) — SHA-256 `F0B6BEFE3EF2AA550AF2E1F5F4E0393BC7D9DB99BA3F1F1DCD2E0A3D8B196AFD` (bản build lúc 14:04, Stage 2) |
| `mapping.txt` (R8) | 47.147.568 B — **chỉ để local**, không đưa vào repo/báo cáo (báo cáo chỉ dán đoạn trích) |
| Mật khẩu | chỉ nằm ở `D:\TripPocket-keys\password.txt` + `keystore.properties` (cả hai **ngoài git**) — không có trong file này, không có trong commit |
| Nơi sao lưu 1 | _chưa sao lưu_ |
| Nơi sao lưu 2 | _chưa sao lưu_ |

> **Lưu ý về SHA-256 file:** R8/zip không tất định theo byte. Bản `assembleRelease` chạy lại ngày 18/11 lúc 14:54
> (sau Stage 3) ra **cùng dung lượng 4.755.819 B** nhưng SHA-256 khác: `ECABCE8F4F1D6850B23E03745CB73C943A001B8ADE6FF507E6AD893A314771F6`.
> Vì vậy **SHA-256 của asset phát hành phải đo lại ở Stage 7** trên đúng file upload lên GitHub Release; bảng trên chỉ
> là bản Stage 2.

## 6. Sao lưu (bắt buộc, 2 nơi)

Mất keystore + mật khẩu = **không thể** phát hành bản cập nhật cho `vn.edu.vlu.trippocket` nữa (Play chỉ nhận
cùng khoá ký, muốn tiếp tục phải đổi `applicationId`). Sao lưu cả `release-key.jks` **và** mật khẩu, ở 2 nơi
khác nhau (ví dụ: password manager + USB ngoài), và ghi ngày sao lưu vào bảng mục 5.

## 7. Kiểm bản **đã ký** trên emulator (18/11/2026)

| Việc | Kết quả |
|---|---|
| `adb uninstall` bản ký debug cũ rồi `adb install app-release.apk` | `Success` (không dùng `-t`, không dùng bản debug) |
| `dumpsys package` | `versionName=1.1.0`, `versionCode=2`, `signatures=… version:2` |
| App info (Settings → Apps → TripPocket) | hiện `version 1.1.0` — ảnh chụp dùng cho PDF |
| `am start -W` lần đầu | COLD, `TotalTime 665 ms` |
| Đăng nhập `demo3@trippocket.test` trên bản đã ký | vào `MainActivity`, danh sách **"No trips yet"** (máy sạch sau khi gỡ app) |
| Bước 8 — Restore from backup | snackbar "Restoring your trips…" → **"1 trip restored"**; thẻ chuyến "1 member · 200,000 ₫ spent"; mở chuyến: itinerary còn "09:00 Museum of Trade Ceramics", Expenses còn "Lunch" 200.000 ₫ (Food), Members (1) |
| `adb logcat -d` lọc `FATAL|VerifyError|NoSuchField|NoSuchMethod|ClassNotFoundException|NoClassDefFoundError|E/AndroidRuntime` | **0 hit** (3.003 dòng log của phiên cài + đăng nhập + restore) |

Kết luận: bản release **ký bằng khoá thật** vẫn qua được luồng Firestore backup/restore dưới R8, không có lỗi
reflection nào trong logcat.


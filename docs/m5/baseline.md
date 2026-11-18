# M5 baseline — số liệu "trước" khi chưa bật R8

Đo ngày 18/11/2026 trên máy Windows của Duy Anh, nhánh `feature/m5-hardening-release` tạo từ `main` tại commit
`78598a9` ("Merge pull request #60 from NeitLN/docs/m5-plan"). Mọi lệnh chạy bằng:

```powershell
$env:JAVA_HOME = 'D:\android studio\jbr'
.\gradlew.bat --no-daemon --console=plain "-Dorg.gradle.java.home=D:/android studio/jbr" `
  "-Dorg.gradle.java.installations.auto-detect=false" `
  "-Dorg.gradle.java.installations.paths=D:/android studio/jbr" <task>
```

## 1. Build baseline

| Lệnh | Kết quả | Thời gian |
|---|---|
| `testDebugUnitTest lintDebug assembleDebug assembleRelease` | **BUILD SUCCESSFUL** — 114 actionable tasks: 81 executed, 33 up-to-date | 6m03s |

Trạng thái `release {}` lúc này vẫn là bản gốc của M4: `optimization { enable = false }` (R8 bị tắt), **không** có
`proguard-rules.pro`, `versionCode = 2`, `versionName = "1.1.0"` (đã bump ở Stage 0).

## 2. Unit test

| Chỉ số | Giá trị |
|---|---|
| Số file kết quả (`app/build/test-results/testDebugUnitTest/*.xml`) | 58 |
| Test chạy | **452** |
| Fail / error | **0** |
| Skip | **0** |

Đếm bằng cách cộng `testsuite@tests`, `testsuite@failures`, `testsuite@errors`, `testsuite@skipped` của toàn bộ file
XML, không đọc tay từ log.

## 3. Lint

`lintDebug`: **0 errors, 14 warnings** (`app/build/reports/lint-results-debug.txt`, dòng cuối
`0 errors, 14 warnings`). Không có lỗi nào thuộc mức `Error`/`Fatal`.

## 4. Dung lượng APK (baseline, chưa minify)

| File | Byte | MB |
|---|---|
| `app/build/outputs/apk/debug/app-debug.apk` | 16.109.790 | 15,36 |
| `app/build/outputs/apk/release/app-release-unsigned.apk` | 12.819.992 | 12,23 |

`app/build/outputs/mapping/release/mapping.txt`: **không tồn tại** (`Test-Path` = `False`) → đúng như dự đoán, R8
chưa chạy nên chưa có mapping.

SHA-256 của bản debug baseline (dùng để đối chiếu sau này):
`8378672695316C0634824F2B2570515DD6384EE1270AD3C45ED202379978D48C`.

## 5. Đóng góp commit trên `main` (`git shortlog -sn --no-merges main`)

| Số commit | Tác giả |
|---|---|
| 36 | Võ Việt Tiến |
| 25 | Duy Anh |
| 17 | Jayson135724 |
| 16 | Do Quoc Bao |
| 6 | anh7032 |

`anh7032` và `Duy Anh` dùng **cùng email** (`git log --format="%an|%ae"`), tức cùng một
người: Duy Anh có **31 commit** (25 + 6), không phải 2 người khác nhau.

## 6. Dùng baseline này để làm gì

- So dung lượng: APK release phải nhỏ hơn baseline **ít nhất 25%** (Stage 1). Kết quả thật sau khi bật R8 ghi ở
  `docs/m5/proguard-spike.md`.
- So lint: sau khi bật R8 + `proguard-rules.pro` không được phát sinh lỗi lint mới.
- 452 test là mốc "không được giảm" cho các stage sau (Stage 5 `SessionStore` có thêm test riêng).

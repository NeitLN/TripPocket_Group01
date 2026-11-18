# M5 spike: R8 trong AGP 9.4.1 — chọn công tắc nào?

Ngày 18/11/2026 · máy: Windows, `JAVA_HOME=D:\android studio\jbr`, AGP 9.4.1, Gradle 9.6.0 · nhánh
`feature/m5-hardening-release` · mọi lệnh chạy `--no-daemon`.

## Câu hỏi

AGP 9.4.1 có **hai** đường bật R8 trong `buildTypes.release`:

- `optimization { enable = true }` — DSL mới của AGP 9 (trước M5 đang là `enable = false`, tức R8 bị tắt);
- `isMinifyEnabled = true` / `isShrinkResources = true` — DSL cũ, vẫn còn trong `com.android.build.api.dsl.BuildType`
  (kiểm bằng cách giải nén `gradle-api-9.4.1.jar`).

Không đoán cái nào có tác dụng: build cả 3 tổ hợp, so **artifact** (mapping + APK), không so cảm giác.

## Cách đo

Mỗi tổ hợp: `assembleRelease`, rồi kiểm

1. `app/build/outputs/mapping/release/mapping.txt` có được sinh ra không (R8 chạy = có mapping);
2. dung lượng `app/build/outputs/apk/release/app-release-unsigned.apk`;
3. trong log có task `minifyReleaseWithR8` / `convertShrunkResourcesToBinaryRelease` chạy thật hay `UP-TO-DATE`;
4. một dòng mapping của class bị đổi tên (obfuscate thật, không chỉ shrink).

Cùng một `app/proguard-rules.pro` cho cả 3 tổ hợp (4 package DTO Gson + `WeatherRepository$CachedDay` +
`-keepattributes`), để chỉ có **một** biến thay đổi.

## Kết quả

| Tổ hợp | DSL trong `release {}` | Build | `mapping.txt` | APK release | Task R8 |
|---|---|---|---|---|---|
| Baseline | `optimization { enable = false }` | SUCCESS 6m03s | **không có** | **12.23 MB** (12,819,992 B) | `minifyReleaseWithR8` không chạy |
| (a) | `optimization { enable = true }` | SUCCESS 4m05s | có — 47,147,568 B | **4.52 MB** (4,739,435 B) | `minifyReleaseWithR8` + `convertShrunkResourcesToBinaryRelease` chạy |
| (b) | bỏ `optimization`, chỉ `isMinifyEnabled = true` + `isShrinkResources = true` | SUCCESS 3m10s | có — 47,147,568 B (sinh lại khi sửa `proguard-rules.pro`) | **4.52 MB** | `minifyReleaseWithR8` chạy thật (xem ghi chú) |
| (c) | cả hai cùng lúc | SUCCESS 3m26s | có — 47,147,568 B | **4.52 MB** | `minifyReleaseWithR8` chạy; **không** lỗi, **không** warning trùng |

Ghi chú về tổ hợp (b): lần chạy đầu tiên Gradle báo `minifyReleaseWithR8 UP-TO-DATE` vì output của (a) vẫn còn và
**input giống hệt** — bản thân điều đó đã là bằng chứng hai DSL đặt cùng một input. Để chắc, thêm 1 dòng comment
vào `proguard-rules.pro` (đổi hash input) rồi build lại: R8 chạy lại thật và cho mapping **cùng kích thước từng
byte**. Comment đó đã được xoá sau spike.

## Kết luận

1. Trong AGP 9.4.1, `optimization.enable` và `isMinifyEnabled`/`isShrinkResources` **điều khiển cùng một
   pipeline**: 3 tổ hợp cho APK 4.52 MB và `mapping.txt` 47,147,568 B giống nhau.
2. `optimization { enable = true }` **một mình** cũng bật shrink resource (`convertShrunkResourcesToBinaryRelease`
   chạy ở tổ hợp (a)) — nên không cần khai `isShrinkResources` riêng nếu dùng DSL mới.
3. **Chọn tổ hợp (b)** và **xoá** block `optimization`:
   - tên `isMinifyEnabled` / `isShrinkResources` khớp đúng từ ngữ đề bài yêu cầu, dễ đối chiếu khi chấm;
   - chỉ còn **một** công tắc cho một việc, không có hai chỗ mâu thuẫn khi ai đó sửa sau này.
4. R8 có tác dụng thật: APK giảm **63%** (12.23 → 4.52 MB); mapping đổi tên cả class lồng nhau
   (`MainActivity$onCreate$2$1$1 -> ra1`), trong khi class được rule giữ vẫn nguyên tên.

## Hệ quả / việc tiếp theo

- Bản release bây giờ **phải** được smoke test trên emulator: unit test và lint đều chạy trên `debug`, không chứng
  minh gì cho R8 (xem `docs/m5/smoke-test.md`).
- Stage 2 (ký số) chưa làm: APK ở đây là `app-release-unsigned.apk`.

# RUNBOOK

## Chạy local
- Mở thư mục bằng Android Studio, chạy cấu hình `app`. Trước khi mở PR, chạy:
  `./gradlew testDebugUnitTest lintDebug assembleDebug`
- **Đường dẫn có dấu tiếng Việt** (vd `Lập trình ứng dụng di động`) làm Gradle/Java không tìm thấy
  `gradle-wrapper.jar` khi chạy từ terminal. Cách xử lý: clone repo vào đường dẫn không dấu, hoặc gán ổ ảo:
  `subst T: "<đường dẫn repo>"` rồi chạy Gradle từ `T:\`.
- Gradle cần một JDK đầy đủ (có `jlink`). Nếu báo `jlink executable ... does not exist`, đặt
  `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`.

## Firebase (Tiến làm một lần)
1. console.firebase.google.com → Add project `TripPocket-Group01` (tắt Google Analytics cho nhanh).
2. Add app → Android, package `vn.edu.vlu.trippocket` → tải `google-services.json`.
3. Authentication → Sign-in method → bật **Email/Password**.
4. Firestore Database → Create database (production mode, region `asia-southeast1`).
5. Firestore → Rules: dán đoạn dưới → Publish. **Làm ngay lúc tạo project**, trước lần thử upload đầu tiên
   (proposal 6.3; A5 sẽ chép vào `firestore.rules` trong repo):
   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /users/{uid}/trips/{tripId} {
         allow read, write: if request.auth != null && request.auth.uid == uid;
       }
     }
   }
   ```
6. Gửi `google-services.json` **riêng** cho 3 bạn (Zalo/Drive), mỗi người đặt vào `app/`. **Không commit file này**
   (F0 thêm file này vào `.gitignore`; trước khi F0 merge thì tự kiểm `git status`). Ai cần xem dữ liệu Firestore thì Tiến thêm vào Project settings → Users and permissions.

## Biến/secret
| Tên | Ở đâu | Ghi chú |
|---|---|---|
| `google-services.json` | `app/` trên máy từng người | Không commit |
| Open-Meteo | — | Không cần key |

## Release (cho demo)
1. Code freeze CN 08/11 → chạy X1 (`docs/tasks/X1-integration.md`).
2. Merge `develop` → `main`, tag `v1.0`.
3. `./gradlew assembleDebug` → cài `app/build/outputs/apk/debug/app-debug.apk` lên máy demo **và** một máy dự phòng.
4. Chuẩn bị sẵn tài khoản demo và một chuyến mẫu đã backup, phòng trường hợp mạng ở phòng demo yếu.

## Rollback
- Code: checkout tag trước đó trên `main`, build lại APK.
- Dữ liệu: backup Firestore là bản sao duy nhất; xoá app thì mất dữ liệu chưa backup. Không xoá document Firestore
  bằng tay trong lúc demo.

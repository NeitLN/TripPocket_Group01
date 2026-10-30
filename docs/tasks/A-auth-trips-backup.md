# A — Auth, Trips, Backup & Restore
Feature: A1–A5 · Chủ module: Võ Việt Tiến · Nhánh: `feature/tien-<feature>`
Phụ thuộc: F0 (chặn: bắt đầu) · Người tích hợp: Tiến

> **Xoay vòng (xem ROADMAP "Xoay vòng"):** mỗi tính năng dưới đây ghi rõ **người làm**. Người làm tự commit trên
> nhánh `feature/<tên>-<ID>` bằng tài khoản của mình; chủ module review lượt đầu, Tiến duyệt cuối. Làm ở module
> người khác thì hỏi chủ module về contract trước khi code; được sửa file cần cho tính năng đó.

Đọc trước: proposal 3.1, 4.1, 4.2, 6.2, 6.3; `docs/ARCHITECTURE.md`, mục "Tài khoản, phiên và backup".

## Sở hữu
`ui/login/*`, `ui/trips/*`, `ui/detail/*`, `data/remote/firebase/*`, `data/repository/{Auth,Trip,Backup}Repository.kt`,
`data/local/dao/{TripDao,BackupDao}.kt`, `work/*`, `domain/LoginValidator.kt`, `firestore.rules`,
`values/strings.xml`, cùng các file dùng chung trong ARCHITECTURE.

## A1 — Login/Register (T3 03/11) — **người làm: Duy Anh**
- Register dùng lại `LoginValidator`; `AuthRepository`: signIn, register, signOut, `currentUid`; lỗi Firebase đổi
  thành thông báo dễ hiểu.
- Đã đăng nhập thì vào thẳng MainActivity. Đăng xuất: huỷ phiên TripDetail, gọi `ReminderReconciler` (Bảo) để huỷ
  alarm, **giữ** dữ liệu local. Đăng nhập: enqueue lại backup nếu còn PENDING, gọi reconciler.
- Xoá dữ liệu chưa backup chỉ khi người dùng chọn rõ và xác nhận (proposal 6.2).
- [ ] Đăng ký → đăng nhập → mở lại app vẫn đăng nhập; sai mật khẩu hoặc mất mạng → hiện lỗi, không kẹt ở Loading

## A2 — Trip CRUD (T4 04/11) — **người làm: Bảo**
- Form: name, destination (≤ 80 ký tự), startDate ≤ endDate. Đổi ngày chuyến → gọi reconciler.
- Danh sách lọc theo `ownerUid`; thẻ chuyến lấy số thành viên và tổng chi qua `ExpenseRepository` (Minh).
- [ ] CRUD offline; tài khoản B không mở được chuyến của A (NotFound)

## A3 — Backup (upload thật T3 03/11, đầy đủ T4 04/11, test tài khoản T6 06/11) — **người làm: Tiến**
Làm đúng giao thức trong ARCHITECTURE: `trip-backup:<uid>` + `APPEND_OR_REPLACE`, xử lý hết hàng đợi, cập nhật có
điều kiện revision, FAILED khi ≥ 1 MiB và nút Retry, uid không khớp thì thoát và giữ PENDING, xoá remote trước rồi
mới xoá local, mutex dùng chung với restore, enqueue lại khi mở app và khi đăng nhập.
- [ ] Sửa offline → bật mạng → Firestore cập nhật, SYNCED
- [ ] Sửa **ngay trước khi** worker kết thúc → cuối cùng vẫn đồng bộ bản mới
- [ ] A → B → A, có dữ liệu PENDING và một chuyến đã xoá → không mất, không lẫn tài khoản

## A4 — Restore (tối thiểu T4 04/11, đầy đủ T7 07/11) — **người làm: Tiến**
- Kiểm **toàn bộ** snapshot (giới hạn, tham chiếu thành viên) trước khi ghi; thay chuyến cùng các bảng con trong
  **một** transaction; giữ nguyên UUID; dùng mutex chung với upload; kiểm lại revision local trước khi thay bản đã chọn.
- Trùng chuyến (kể cả chuyến đã xoá mềm) → hỏi giữ bản nào. Restore xong gọi reconciler (alarm) và tải lại forecast.
- [ ] Cài lại app → Restore → đủ dữ liệu mọi module
- [ ] Snapshot hỏng hoặc vượt giới hạn → bị từ chối, local giữ nguyên

## A5 — Rules + 1 MiB (T7 07/11) — **người làm: Minh (Tiến đưa mapper backup)**
- `firestore.rules` theo proposal 6.3 (đã deploy từ T6 30/10, xem RUNBOOK); thử với 2 tài khoản.
- Minh làm fixture chuyến đầy giới hạn (text tiếng Việt dài nhất); Tiến chạy qua mapper thật, tính kích thước theo
  quy tắc Firestore (tên field + UTF-8), không tính bằng độ dài JSON.

## Việc tích hợp (tính vào thời gian)
Review 60–90 phút mỗi ngày; merge T7 31/10, CN 01/11, T4 04/11, T6 06/11, T7 07/11, CN 08/11; chạy kiểm trên bản đã
ghép; cập nhật ROADMAP/STATUS.

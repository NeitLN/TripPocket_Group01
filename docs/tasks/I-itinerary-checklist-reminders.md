# I — Itinerary, Checklist & Reminders
Feature: I1–I5 · Giao cho: Đỗ Quốc Bảo · Nhánh: `feature/bao-<feature>`
Phụ thuộc: F0 (chặn: bắt đầu phần Room); W3 (chặn: tích hợp phần thời tiết từng ngày); A1 (chặn: tích hợp I5)
Người tích hợp: Tiến · Review lượt đầu: Minh

Đọc trước: proposal 3.2, 4.2, 6.1, 6.2; `docs/ARCHITECTURE.md` (thời gian, khuôn ghi, thứ tự hoạt động, tài khoản).

## Sở hữu
`ui/itinerary/*`, `ui/checklist/*`, `reminder/*`, `data/repository/ItineraryRepository.kt`,
`data/local/dao/{ActivityDao,ChecklistDao}.kt`, `data/model/ItineraryModels.kt`, `nav_itinerary.xml`,
`nav_checklist.xml`, `values/strings_itinerary.xml`, test của module. Cần sửa Manifest thì gửi đúng đoạn cần sửa cho Tiến.

## Có sẵn từ F0 (dùng luôn, đừng tạo lại)
- Entity của bạn trong `data/local/entity/`, DAO khung (`observeByTrip`, `countByTrip`, insert/update/delete) trong
  `data/local/dao/`: thêm query bạn cần vào DAO của mình.
- Ghi dữ liệu: `container.tripWriter.write(tripId) { ... }` (xem `TripWriter.kt` và `TripWriterTest.kt`).
- Giới hạn: `domain/Limits.kt` (`Limits.check(count, Limits.MAX_...)`).
- ViewModel trong màn chi tiết chuyến: `by tripViewModels { XViewModel.Factory }`; factory lấy repository qua
  `appContainer()` (xem `TripListViewModel.Factory`). Repository mới của bạn: gửi Tiến dòng cần thêm vào `AppContainer`.
- Màn của bạn hiện vẫn đọc `SampleTripRepository` (MockData). Chuyển sang repository Room của bạn rồi bỏ dùng nó.
- Test DAO/Room: theo mẫu `data/local/TestDatabase.kt` (Robolectric, in-memory) và `ui/trips/TripListViewModelTest.kt`.
- File của bạn: `data/model/<Module>Models.kt`, `values/strings_<module>.xml`, `navigation/nav_<module>.xml`,
  test trong `app/src/test/.../ui/<module>/`.
- Tab Checklist đã có màn tạm `ui/checklist/ChecklistFragment` + `fragment_checklist.xml`: thay bằng màn thật ở I2.
- `reminder/ReminderReceiver`, `reminder/BootReceiver` đã khai báo trong Manifest (boot, đổi múi giờ, đổi giờ):
  viết nội dung ở I4/I5. `ActivityEntity.startTime` là phút trong ngày (Int).

## Làm ngay T6–CN
- `domain/ReminderTime.kt`: `triggerAtMillis(startDateEpochDay, dayIndex, startMinuteOfDay, minutesBefore, zoneId)`;
  test cả ca qua nửa đêm và ca đổi múi giờ.
- `domain/ItineraryOrdering.kt`: quy tắc thứ tự trong ARCHITECTURE (sắp ổn định theo giờ khi tạo/đổi giờ, kéo thả
  ghi đè); test với các hoạt động **khác giờ**.
- Phác chữ ký `ItineraryRepository` (gồm `estimatedTotal(tripId)` cho Minh); layout form, checklist.
- CN: viết query `ActivityDao`/`ChecklistDao`; gửi đoạn manifest cho receiver.

## I1 — Lịch trình (T3 03/11)
- Đọc Flow, ghi theo khuôn `withTransaction` + `touch`. Form: title, place, time, cost, note, nhắc trước (bỏ trống được).
- Thời tiết từng ngày lấy qua contract của Duy Anh (`WeatherRepository`).
- Đủ 200 → nút Add tắt, hiện "200/200 activities"; repository kiểm lại trong transaction.
- [ ] CRUD offline; hoạt động thứ 201 không được ghi và revision giữ nguyên

## I2 — Checklist (T4 04/11) · I3 — Kéo thả (T4 04/11)
- [ ] Checklist: thêm/tick/sửa/xoá, tối đa 100, mở lại app vẫn đúng
- [ ] Kéo thả: lưu trong một transaction, tăng revision một lần; đổi giờ thì sắp lại theo giờ

## I4 — Reminder (alarm thật T3 03/11, xong T5 05/11)
- Có quyền exact (`canScheduleExactAlarms()`) → `setExactAndAllowWhileIdle`; không có → `setAndAllowWhileIdle` và
  báo có thể trễ. Trên Android 14+, cài mới thì mặc định **không** có quyền exact, nên inexact là đường thường gặp.
- `POST_NOTIFICATIONS`: nếu bị từ chối thì hiện trạng thái bị chặn. PendingIntent immutable, request code ổn định
  theo `activityId`.
- Trước khi hiện thông báo, kiểm uid hiện tại có phải chủ chuyến không.
- [ ] Có quyền exact: nhắc gần đúng giờ. Không có quyền: nhắc trong khung inexact (≤ khoảng 1 giờ)
- [ ] Sửa/xoá hoạt động → đổi/huỷ nhắc

## I5 — Reconciler (T6 06/11)
- `ReminderReconciler.reconcile(uid?)`: huỷ alarm cũ, chỉ đặt alarm **sắp tới** của uid đang đăng nhập, tính từ múi
  giờ hiện tại. Receiver cho `BOOT_COMPLETED`, `TIMEZONE_CHANGED`, `TIME_SET`, dùng `goAsync` và giới hạn thời gian
  chạy. A gọi reconciler khi đăng nhập, đăng xuất, restore, đổi ngày chuyến.
- [ ] Khởi động lại → nhắc vẫn đến; đăng xuất → không còn nhắc; đổi múi giờ → nhắc theo giờ địa phương mới

## Thiết bị test
Một emulator API 36 + một máy thật. Không cam kết đã kiểm trên API 31/33.

## Khi xong mỗi feature
PR vào `develop`, kèm bằng chứng (lệnh test + kết quả, clip màn hình). Không sửa ROADMAP/STATUS.

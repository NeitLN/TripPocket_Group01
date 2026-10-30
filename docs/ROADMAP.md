# ROADMAP — TripPocket

Tiến độ đo bằng **feature có bằng chứng**, không đo bằng "đã làm xong tuần". Trạng thái: `đề xuất` → `đang làm` →
`đã kiểm` (PR xanh, có bằng chứng) → `đã tích hợp` (Tiến merge vào `develop` và kiểm lại trên bản đã ghép).
Chỉ Tiến sửa file này; người làm ghi bằng chứng vào PR.

## Mốc
| Mốc | Người dùng thấy được gì | Cổng ra | Ngày | Trạng thái |
|---|---|---|---|---|
| M0 | App cũ vẫn chạy; danh sách chuyến đọc từ Room thật; đủ 4 tab trên Navigation | Gate F0 (xem phiếu) PASS | CN 01/11 | chưa |
| M1 | Mọi module CRUD offline trên Room; đăng nhập Firebase thật; tìm địa điểm Open-Meteo thật; upload một chuyến lên Firestore; một alarm thật | Checkpoint kỹ thuật T3 03/11 + checkpoint phạm vi T5 05/11 | T5 05/11 | chưa |
| M2 | Đủ proposal | Mọi feature dưới đây `đã tích hợp` | T7 07/11 (mục tiêu), CN 08/11 23:59 (freeze) | chưa |
| M3 | Bản demo | X1 PASS; `main` được tag `v1.0`; APK giống hệt nhau trên máy demo và máy dự phòng | T3 10/11 | chưa |

## Feature
Mức rủi ro dùng cho `spec-driven-dev`: Mini / Standard / Full.

| ID | Người | Mô tả (mục proposal) | Phụ thuộc | Kiểm chấp nhận | Rủi ro | Hạn | Trạng thái |
|---|---|---|---|---|---|---|---|
| F0 | Tiến | Nền dùng chung (phiếu `F0-foundation.md`) | — | Gate F0 | Full | T7 31/10 contract, CN 01/11 gate | đang làm: gate PASS trên nhánh, chờ chốt contract + merge |
| A1 | Duy Anh | Login/Register Firebase, đăng xuất có huỷ phiên (3.1, 6.2) | F0 | Đăng ký → đăng nhập → mở lại app vẫn đăng nhập; A → B → A thấy đúng dữ liệu | Standard | T3 03/11 | đề xuất |
| A2 | Bảo | Trip CRUD, lọc `ownerUid`, xoá mềm, cổng NotFound (3.1) | F0, A1 | Tạo/sửa/xoá offline; B không mở được chuyến của A | Standard | T4 04/11 | đề xuất |
| A3 | Tiến | Backup theo giao thức trong ARCHITECTURE (6.2) | A2, rules | Lần upload thật đầu tiên T3 03/11; đầy đủ: sửa ngay trước khi worker kết thúc → vẫn đồng bộ; A→B→A với dữ liệu PENDING và chuyến đã xoá | Full | T4 04/11, test tài khoản T6 06/11 | đề xuất |
| A4 | Tiến | Restore: kiểm toàn bộ trước khi ghi, thay thế nguyên tử, chọn phiên bản khi trùng, dùng chung mutex với upload (6.2) | A3 | Bản tối thiểu (chuyến không trùng) T4 04/11; cài lại → restore đủ; snapshot hỏng/vượt giới hạn → local giữ nguyên | Full | T6 06/11 – T7 07/11 | đề xuất |
| A5 | Minh (Tiến đưa mapper) | Test rules với 2 tài khoản; test kích thước snapshot đầy giới hạn < 1 MiB bằng mapper thật (Minh làm fixture) (6.3, 6.4) | A3 | Unit test PASS; uid khác bị rules chặn | Standard | T7 07/11 | đề xuất |
| I1 | Bảo | Lịch trình theo ngày, form, quy tắc thứ tự, tổng ngày, giới hạn 200 (3.2) | F0 | CRUD offline; hoạt động thứ 201 không ghi vào Room, revision giữ nguyên | Standard | T3 03/11 | đề xuất |
| I2 | Tiến | Checklist CRUD, giới hạn 100 (3.2) | F0 | CRUD + tick, mở lại app vẫn đúng | Mini | T2 02/11 | đề xuất |
| I3 | Duy Anh | Kéo thả (`orderIndex`) | I1 | Kéo thả xong thứ tự giữ nguyên sau khi mở lại; đổi giờ thì sắp lại theo giờ | Mini | T6 06/11 | đề xuất |
| I4 | Bảo | Reminder: đặt/đổi/huỷ, exact hoặc inexact, quyền thông báo (3.2) | I1 | Có quyền exact → nhắc gần đúng giờ; không có → nhắc trong khung inexact (≤ ~1 giờ); tắt thông báo → hiện trạng thái bị chặn; xoá hoạt động → không còn nhắc | Standard | Alarm thật T3 03/11, xong T5 05/11 | đề xuất |
| I5 | Minh | `ReminderReconciler` + receiver boot/đổi múi giờ/đổi giờ; gọi khi đăng nhập/đăng xuất/restore/đổi ngày chuyến (3.2) | I4, A1 | Khởi động lại → nhắc vẫn đến; đăng xuất → không nhắc | Standard | T6 06/11 | đề xuất |
| W1 | Duy Anh | Tìm địa điểm: flatMapLatest, Empty, Error, Retry có trigger riêng (3.3) | F0 | "da lat" ra kết quả; "xyz" → No places found; mất mạng → lỗi, lần sau vẫn tìm được; Retry cùng query vẫn gọi lại | Standard | T4 04/11 | đề xuất |
| W2 | Minh | Saved places CRUD theo UUID, giới hạn 30 (3.3) | W1 | Hai địa điểm trùng tên khác toạ độ vẫn tách biệt | Mini | T5 05/11 | đề xuất |
| W3 | Duy Anh | Forecast → cache, khớp ngày chuyến, Last updated, pull-to-refresh, ghi nguồn; contract thời tiết cho Itinerary (3.3) | W2 | Tải → chế độ máy bay → vẫn hiện; forecast rỗng/thiếu không crash | Standard | T5 05/11 | đề xuất |
| W4 | Bảo | `WeatherViewModelTest` đủ proposal 6.4 + các ca mới | W1, W3 | PASS | Standard | T7 07/11 | đề xuất |
| W5 | Tiến | Thời tiết từng ngày hiện trong Itinerary (đọc cache của W3 qua `WeatherRepository`); ngày ngoài 16 ngày hiện "Forecast not available yet" (3.3) | W3, I1 | Ngày có forecast hiện nhiệt độ + mưa; ngày xa hiện đúng câu; offline vẫn hiện | Standard | T6 06/11 | đề xuất |
| E1 | Duy Anh | Members CRUD, chặn xoá người đang là payer hoặc participant, giới hạn 15 (3.4) | F0 | Xoá người đang được dùng → bị chặn kèm lý do | Standard | T2 02/11 | đề xuất |
| E2 | Minh | Expense + shares trong một transaction, chia đều/tuỳ chỉnh, giới hạn 300 (3.4) | E1 | 100.001 chia 3 → 33.334/33.334/33.333; tuỳ chỉnh lệch tổng → không lưu | Full | T4 04/11 | đề xuất |
| E3 | Bảo | Balances + settlement từ Room (3.4, 6.4) | E2 | Kịch bản demo: B, C mỗi người nợ A 100.000 | Full | T6 06/11 | đề xuất |
| E4 | Tiến | Budget dự tính so với thực chi, theo danh mục, share text (3.4) | E2, I1 | Số liệu đúng; share sheet đúng nội dung | Standard | T5 05/11 | đề xuất |
| X1 | Cả nhóm | Kiểm tích hợp từng đợt (phiếu `X1-integration.md`) | — | Theo phiếu | — | T2 02 → T3 10/11 | đề xuất |

## Xoay vòng: ai cũng code ở mọi module
Giảng viên kiểm qua commit trên GitHub, nên mỗi người phải có **commit của chính mình ở cả 4 module**. Proposal mục 5
giữ nguyên **chủ module** (chịu trách nhiệm chính, giữ phần khó nhất); 3 tính năng còn lại của mỗi module giao cho
3 người kia.

| Người | Auth/Trips/Backup | Itinerary | Weather | Expenses |
|---|---|---|---|---|
| Tiến (chủ Auth/Trips/Backup) | A3, A4 | I2 | W5 | E4 |
| Bảo (chủ Itinerary) | A2 | I1, I4 | W4 | E3 |
| Duy Anh (chủ Weather) | A1 | I3 | W1, W3 | E1 |
| Minh (chủ Expenses) | A5 | I5 | W2 | E2 |

Quy tắc:
- Người làm tính năng tự commit bằng tài khoản GitHub của mình, một nhánh mỗi tính năng: `feature/<tên>-<ID>`
  (vd `feature/duyanh-E1`). Không commit hộ người khác.
- Làm tính năng ở module người khác thì được sửa file cần cho tính năng đó; trước khi code, hỏi chủ module 10 phút
  về contract (chữ ký repository, DAO) để khỏi làm lệch.
- Review lượt đầu = **chủ module** của tính năng đó (chủ module nắm được code người khác viết vào module mình);
  Tiến duyệt cuối. Tính năng của chính Tiến trong module Auth/Trips/Backup: Minh review lượt đầu.
- T2 09/11: mỗi người kiểm chéo và tập giải thích một module không phải của mình.

## Quyết định đắt nếu đổi
| Quyết định | Chốt trước | Hạn | Trạng thái |
|---|---|---|---|
| Thời gian: epoch day + phút trong ngày, giờ trôi theo máy | Entity F0 | T7 31/10 | đã chốt 30/10 |
| Khuôn ghi bảng con + `TripChangeTracker` + enqueue sau commit | F0 | T7 31/10 | đã chốt 30/10 |
| Navigation: graph con + `TripDetailViewModel` giữ `trip_id` | F0 | T7 31/10 | đã chốt 30/10 |
| Migration: tăng version, destructive chỉ khi phát triển, đóng băng trước khi có dữ liệu demo | F0 | T7 31/10 | đã chốt 30/10 |
| Thứ tự hoạt động (sắp theo giờ khi tạo/đổi giờ, kéo thả ghi đè) | I1 | T7 31/10 | đã chốt 30/10 |
| Giao thức backup (`trip-backup:<uid>`, `APPEND_OR_REPLACE`, cập nhật có điều kiện, mutex) | A3 | T2 02/11 | đã chốt 30/10 |
| KSP2 + Room với AGP 9.4 (Kotlin tích hợp sẵn), không thêm plugin `kotlin.android`/kapt | F0 | T7 31/10 | **spike** |
| Robolectric `sdk = 35` cho DAO test (dự phòng: instrumented) | F0 | T7 31/10 | **spike** |

## Lịch theo ngày
Cả 4 người rảnh cả tuần, kể cả cuối tuần (leader xác nhận 30/10). Ngày demo chưa biết nên lịch tính theo ngày sớm nhất là 11/11. Tiến dành riêng
60–90 phút mỗi ngày cho review và merge. **M** = ngày có merge vào `develop`.

| Ngày | Tiến | Bảo | Duy Anh | Minh |
|---|---|---|---|---|
| T6 30/10 | Nhánh `develop`; spike KSP/Room + Robolectric; tạo Firebase + deploy rules | Phác chữ ký `ItineraryRepository`; `ReminderTime` + test | Phác DTO + chữ ký `WeatherRepository`; tách kết quả tìm kiếm khỏi địa điểm đã lưu | Test chia tuỳ chỉnh; phác chữ ký expense/member; fixture giới hạn |
| T7 31/10 **M** | **Chốt contract:** entity, accessor, FK, Limits, chữ ký repository/factory/session, ví dụ `TripDao`/`TripRepository` dạng Flow + test transaction/rollback, data class snapshot | Thống nhất quy tắc thứ tự + `estimatedTotal`; layout form/checklist | Thống nhất contract cache và thời tiết theo ngày cho Itinerary | Thống nhất transaction expense/share và quy tắc bảo vệ thành viên |
| CN 01/11 **M** | **Gate F0:** AppContainer, NavHost 4 tab, cổng trip, tách string/model/test, manifest; build + lint + DAO test | Graph con itinerary/checklist; viết DAO query của mình; gửi đoạn manifest cho receiver | Viết DAO query; Retrofit chạy thử; kiểm build khi không có json | Viết DAO query + test rollback của expense; xong fixture giới hạn |
| T2 02/11 **M** | I2 Checklist | I1 bắt đầu | E1 Members | E2 bắt đầu (theo contract với E1) |
| T3 03/11 **checkpoint kỹ thuật** | A3: mapper snapshot + **lần upload thật đầu tiên** | Xong I1; **một alarm thật** (exact/inexact) | A1 Login Firebase | E2 |
| T4 04/11 **M** | A3 đầy đủ; restore tối thiểu | A2 Trip CRUD | W1 tìm địa điểm | Xong E2; chạy fixture A5 qua mapper thật |
| T5 05/11 **checkpoint phạm vi** | E4 Budget + share; kiểm toàn bộ M1 | I4 nối vào create/edit/delete | W3 forecast + cache | W2 saved places |
| T6 06/11 **M** | W5 thời tiết trong Itinerary; A4 chọn phiên bản + thay thế nguyên tử | E3 Balances + settlement | I3 kéo thả | I5 reconciler + receiver |
| T7 07/11 **M, mục tiêu đủ feature** | Xong A4; test A3 khi đổi tài khoản; review | W4 test WeatherViewModel; test nhắc trên máy thật | Giới hạn + lỗi API + cache offline của Weather | A5 rules 2 tài khoản + test 1 MiB |
| CN 08/11 **M, freeze 23:59** | Diễn tập toàn bộ trên bản đã ghép; sửa lỗi tích hợp | Sửa lỗi reminder/xoay màn hình | Sửa lỗi weather | Sửa lỗi expense; kiểm settlement/share |
| T2 09/11 | Cài sạch + restore, xoá đang PENDING, A→B→A | Kiểm chéo Weather; theo dõi nhắc inexact | Kiểm chéo Expenses; kịch bản demo trên máy thật | Kiểm chéo Itinerary; Profiler |
| T3 10/11 **M → main** | Hồi quy cuối; tag `v1.0`; cài APK lên máy demo + máy dự phòng; diễn tập | Kiểm cài đặt quyền nhắc | Chuẩn bị forecast đã cache cho ngày demo | Kiểm số liệu demo; gom bằng chứng/tài liệu nộp |
| T4 11/11 | Demo bằng APK đã đóng băng | Demo itinerary/checklist/reminder | Demo search/forecast/offline | Demo expenses/settlement/budget |

## Checkpoint
- **T7 31/10:** spike KSP/Robolectric hoặc test transaction không đạt → xử lý ngay, các bạn tiếp tục phần không cần Room.
- **T3 03/11:** chưa upload thật được hoặc chưa có alarm thật → dồn sức gỡ ngay, không chờ tới T5.
- **T5 05/11:** module nào chưa đạt M1 → Tiến quyết: dồn người hỗ trợ, hoặc cắt theo thứ tự dưới. Chỉ cắt phần
  **chưa làm** và thật sự giải phóng sức cho đường đang trễ. Đây chưa phải quyết định cắt.
  1. I3 kéo thả (giữ sắp theo giờ)
  2. A4 chọn phiên bản → restore bỏ qua chuyến trùng và báo lại (không mất dữ liệu; vẫn là lệch proposal, cần Tiến duyệt)
  3. I5 khôi phục nhắc sau khi khởi động lại
  4. E4 chi theo danh mục (tiết kiệm ít vì code hiện đã có phần lớn)
  5. A5 test 1 MiB (gần như không tiết kiệm được gì; giữ nếu còn có thể)

## Rủi ro còn lại lớn nhất
Tiến vừa là đường găng (backup/restore) vừa là người duyệt mọi PR. Nếu lần upload/restore thật đầu tiên trễ quá
T4 04/11, thời gian review và sửa lỗi tích hợp sẽ ăn vào thời gian làm feature trước 11/11. Cách giảm: giữ cố định
khung review mỗi ngày, review lượt đầu chéo, Minh làm fixture A5, Bảo giữ toàn bộ phần receiver.

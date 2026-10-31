# TripPocket — PROJECT

Nguồn gốc: `Group1_TripPocket_Proposal.pdf` (proposal đã nộp). File này chỉ tóm tắt điều cần để lập kế hoạch;
chi tiết hành vi xem proposal theo số mục (vd "3.4").

## Cho ai
- Sinh viên đi du lịch theo nhóm: cần chia tiền chung bằng VND cho công bằng.
- Người đi một mình: cần lịch trình và dự báo thời tiết ngay cả khi mất sóng.

## Hành trình quan trọng nhất (= kịch bản demo, proposal 3.4)
Đăng nhập → tạo chuyến Đà Lạt → thêm hoạt động → lưu một địa điểm và tải dự báo → **tắt mạng**: vẫn sửa được
lịch trình, checklist, chi phí; dự báo đã cache vẫn hiện kèm "Last updated" → A trả 300.000 VND cho bữa ăn 3 người,
app hiện B và C mỗi người nợ A 100.000 VND.

## Phạm vi: làm đủ 100% proposal (leader chốt 30/10)
4 module, mỗi người một lát dọc (proposal mục 5):
| Module | Người phụ trách |
|---|---|
| Base setup, Auth, Trips, Backup & Restore | Võ Việt Tiến (leader) |
| Itinerary, Checklist, Reminders | Đỗ Quốc Bảo |
| Saved Places & Weather (Open-Meteo) | Bùi Duy Anh |
| Members, Expenses, Split, Budget | Võ Hoàng Minh |

Ba yêu cầu bắt buộc của môn: **CRUD**, **gọi Remote API**, **cache dữ liệu offline**. Stack bắt buộc: Kotlin, MVVM,
Room, Retrofit, Firebase.

## Ngoài phạm vi (theo proposal 1.4)
Nhiều loại tiền tệ, đặt phòng/vé, thanh toán trong app, bản đồ offline, cộng tác real-time, thành viên đăng nhập.

## Deadline cứng
| Hạn | Ngày | Phải có |
|---|---|---|
| Chốt contract F0 | T7 31/10 | Entity, chữ ký repository, khuôn ghi + test transaction |
| Gate F0 merge vào `develop` | CN 01/11 | Room + Navigation + dependency chạy được; 3 bạn bắt đầu code trên đó |
| Checkpoint kỹ thuật | T3 03/11 | Upload thật lên Firestore; một alarm thật |
| Checkpoint phạm vi | T5 05/11 | Mỗi module có CRUD trên Room; leader quyết có cắt gì không |
| Đủ feature (mục tiêu) / code freeze | T7 07/11 / CN 08/11 23:59 | Mọi feature đã merge vào `develop` |
| Demo/nộp | **11–13/11 (chưa chốt ngày)** | Kịch bản demo chạy ở chế độ máy bay trên máy thật |

## Ràng buộc
- 4 người, còn 3 tuần tính cả tuần này (W1 = 30/10–01/11, W2 = 02–08/11, W3 = 09–13/11).
- Firebase gói free (Spark). Open-Meteo miễn phí, không cần API key; phải ghi "Weather data by Open-Meteo.com".
- `google-services.json` **không commit** (proposal 6.3); leader gửi riêng cho từng người.

## Quyết định nghiệp vụ đã chốt
| Câu hỏi | Quyết định | Nguồn |
|---|---|---|
| Phạm vi | Đủ 100% proposal, không cắt trước | Leader, 30/10 |
| Demo | Giữa tuần 3 (11–13/11) | Leader, 30/10 |
| Firebase project | Leader tự tạo | Leader, 30/10 |
| Giờ làm | Cả 4 người rảnh cả tuần, kể cả cuối tuần | Leader, 30/10 |
| 6 quyết định kỹ thuật trong ARCHITECTURE/ROADMAP | Duyệt nguyên như đề xuất | Leader, 30/10 |
| Git | Commit docs lên `main`, tạo `develop`, thêm CI GitHub Actions | Leader, 30/10 |
| Báo cáo, slide, tài liệu nộp | Leader tự lo, ngoài kế hoạch này | Leader, 30/10 |
| Phân việc | Chủ module + xoay vòng: mỗi người có commit ở cả 4 module (giảng viên kiểm qua GitHub) | Leader, 31/10 |
| Mọi quy tắc nghiệp vụ khác | Theo proposal (chia tiền dư, giới hạn, backup, restore...) | Proposal |

## Câu hỏi đang chờ
- [ ] Ngày demo chính xác (11, 12 hay 13/11)? Kế hoạch đang tính trường hợp sớm nhất là T4 11/11.

# E — Members, Expenses, Split & Budget
Feature: E1–E4 (+ fixture A5) · Giao cho: Võ Hoàng Minh · Nhánh: `feature/minh-<feature>`
Phụ thuộc: F0 (chặn: bắt đầu phần Room); I1 (chặn: tích hợp E4) · Người tích hợp: Tiến · Review lượt đầu: Duy Anh

Đọc trước: proposal 3.4, 4.2, 6.4; `docs/ARCHITECTURE.md`. `domain/ExpenseCalculator.kt` đã có chia đều, balances
và settlement; giữ nguyên các test hiện có.

## Sở hữu
`ui/expenses/*`, `ui/members/*`, `domain/ExpenseCalculator.kt`, `domain/ExpenseSummaryText.kt`,
`data/repository/ExpenseRepository.kt`, `data/local/dao/{TripMemberDao,ExpenseDao}.kt`, `data/model/ExpenseModels.kt`,
`nav_expenses.xml`, `values/strings_expenses.xml`, test của module, fixture A5.

## Làm ngay T6–CN
- `validateCustomSplit(amount, shares)` + test; test proposal 6.4: chia tuỳ chỉnh lệch tổng bị từ chối, settlement
  đưa mọi balance về 0 (1 người, nhiều người, số lẻ).
- `ExpenseSummaryText` + test. Layout members, form chi phí, budget.
- Phác chữ ký `ExpenseRepository`, gồm số thành viên và tổng chi cho thẻ chuyến của Tiến.
- CN: viết query DAO; test rollback khi ghi expense + shares.
- **Fixture A5:** một chuyến đầy mọi giới hạn (15 thành viên, 200 hoạt động, 100 checklist, 30 địa điểm, 300 chi phí),
  mọi field điền text tiếng Việt dài tối đa.

## E1 — Members (T2 02/11)
- Thêm/đổi tên/xoá; `orderIndex` cố định; tối đa 15; tên ≤ 80.
- Người đang là payer **hoặc** participant thì không xoá được, phải hiện lý do.
- [ ] Xoá người đang được dùng → bị chặn; xoá người chưa dùng → thành công

## E2 — Expense + split (T4 04/11)
- Expense + toàn bộ shares ghi trong **một** transaction, tăng revision một lần; sửa thì thay toàn bộ shares cũ.
  Mọi thành viên được dùng phải thuộc đúng chuyến. Tối đa 300.
- [ ] 100.001 chia 3 → 33.334 / 33.334 / 33.333; tuỳ chỉnh lệch tổng → không lưu, revision giữ nguyên

## E3 — Balances + settlement (T5 05/11)
- [ ] Kịch bản demo: A trả 300.000 cho 3 người → B, C mỗi người nợ A 100.000

## E4 — Budget + share (T6 06/11)
- Dự tính lấy từ `ItineraryRepository.estimatedTotal` (Bảo), so với thực chi, cộng chi theo danh mục; share text.
- [ ] Số liệu khớp với dữ liệu đã nhập; share sheet có nội dung đúng

## Khi xong mỗi feature
PR vào `develop`, kèm bằng chứng. Không sửa ROADMAP/STATUS.

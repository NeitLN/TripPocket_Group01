# Cách làm việc trên repo

Quy trình nhóm 01 đang dùng. Cách chạy và cấu hình máy xem [`docs/RUNBOOK.md`](docs/RUNBOOK.md); kiến trúc xem
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Ai làm, ai duyệt

- Giai đoạn xây dựng (Milestone 1–4): mỗi người giữ một module, xem bảng ở [`docs/PROJECT.md`](docs/PROJECT.md).
- Từ 20/11 (sửa lỗi, nâng cấp sau báo cáo): Tiến viết code, Minh review. `.github/CODEOWNERS` tự gửi yêu cầu review
  cho người còn lại khi mở PR.
- Không ai tự merge PR của mình khi chưa có approve.

## Nhánh

- Tạo từ `main` mới nhất, đặt tên `<loại>/<tên>-<chủ đề>`, ví dụ `fix/tien-form-drafts`,
  `feature/minh-members-layout`.
- Loại: `feature` (tính năng), `fix` (sửa lỗi), `docs` (tài liệu), `chore` (CI, build, cấu hình), `release`
  (tăng version, phát hành).
- Một PR làm một việc. Việc lớn thì tách nhiều PR nhỏ để dễ review.

## Commit

- Dòng đầu nói thay đổi gì, bằng tiếng Anh, không quá ~70 ký tự, ví dụ
  `Expense form: refuse to save while the Paid in conversion is too large`.
- Thân commit giải thích vì sao và đã kiểm tra thế nào. Commit sửa theo review thì ghi số PR, ví dụ `(#68 review)`.
- Không commit `google-services.json`, `keystore.properties`, file keystore, `.env` (đã có trong `.gitignore`).
- File đang là CRLF (vd `docs/worksheet.md`) thì giữ CRLF; kiểm tra bằng `git diff --stat` trước khi commit,
  nếu cả file bị đổi thì là do xuống dòng.

## Trước khi mở PR

1. `./gradlew testDebugUnitTest lintDebug assembleDebug` phải qua.
2. Hành vi mới hoặc lỗi vừa sửa có test. Sửa lỗi thì viết test tái hiện trước, thấy test fail rồi mới sửa.
3. Thay đổi giao diện hoặc vòng đời (xoay màn hình, app bị Android đóng) thì thử trên điện thoại hoặc emulator.
4. Đổi schema Room thì tăng version, viết migration và test migration; không dùng destructive migration.
5. Cập nhật README / tài liệu nếu thay đổi làm chúng sai đi (số test, cách chạy, kiến trúc).

Mẫu mô tả PR có sẵn trong `.github/pull_request_template.md`, GitHub tự điền khi mở PR.

## Review và merge

- Người review đọc diff, đối chiếu với mục tiêu của PR, chạy test nếu cần, rồi chọn approve hoặc request changes
  kèm lý do cụ thể.
- Request changes: tác giả sửa bằng commit mới trên cùng nhánh (không force-push), trả lời comment, rồi nhờ review lại.
- Merge khi có approve trên commit mới nhất **và** CI xanh (job `build`: test + coverage, lint, build debug và release;
  job `firestore-rules`). Dùng "Create a merge commit" và xoá nhánh sau khi merge.

## Phát hành

Tăng `versionCode` / `versionName` trong một PR `release/...`, ký APK/AAB bằng keystore ngoài repo
([`docs/m5/keystore.md`](docs/m5/keystore.md)), thử bản đã ký trên máy, rồi tạo tag `vX.Y.Z` và GitHub Release.

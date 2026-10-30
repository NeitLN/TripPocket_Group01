# TripPocket brand

Bộ nhận diện "sticker": mỗi chuyến đi là một con sticker cắt viền trắng, như sticker bạn bè thả trong group chat của
chuyến đi. Quy tắc đầy đủ cho giao diện ở `DESIGN.md` (gốc repo); file này chỉ nói về logo và tài sản ảnh.

## File

| File | Dùng cho | Nguồn gốc |
|---|---|---|
| `trippocket-symbol.svg` | Biểu tượng trên nền lime (bản gốc của icon app) | Vẽ tay bằng path, cùng path với `ic_launcher_foreground.xml` |
| `trippocket-symbol-mono.svg` | Biểu tượng một màu (in ấn, watermark) | Cùng path, bỏ viền trắng |
| `trippocket-icon-1024.png` | Icon cho slide, báo cáo | Render từ `trippocket-symbol.svg` bằng Edge headless, 1024×1024 |
| `trippocket-lockup-light.png` | Logo + chữ trên nền sáng `#F7F5FF` | Render HTML: symbol SVG + chữ Be Vietnam Pro Black, màu `#573AD6`, 1600×600 |
| `trippocket-lockup-dark.png` | Logo + chữ trên nền tối `#141023` | Như trên, màu `#CBBEFF` |
| `OFL-BeVietnamPro.txt` | Giấy phép font Be Vietnam Pro (SIL OFL 1.1) | Tải từ google/fonts |

Trong app: `res/drawable/ic_logo_pocket.xml` (biểu tượng), wordmark là TextView style
`TextAppearance.TripPocket.Wordmark`. Không dùng PNG trong app.

## Logo

- Biểu tượng: cái túi violet có khe mở, viền trắng như sticker, nghiêng −6°. Chữ: "TripPocket" (đúng hoa/thường),
  Be Vietnam Pro Black.
- Màu: violet `#573AD6` trên nền sáng; lavender `#CBBEFF` trên nền tối; icon app trên nền lime `#C6F432`.
- Chừa khoảng trống quanh logo ít nhất bằng chiều cao khe mở của túi.
- Không: kéo giãn, đổi font chữ, thêm bóng đổ, đổi màu túi sang màu khác ngoài bảng màu, xoay thêm.

## Màu chính

| Vai trò | Sáng | Tối |
|---|---|---|
| Nền | `#F7F5FF` | `#141023` |
| Violet (nút, link) | `#573AD6` | `#CBBEFF` |
| Lime (nút New trip, chữ được tô nổi) | `#C6F432` | `#C6F432` |
| Màu sticker | violet `#7C5CFF`, lime `#C6F432`, hồng `#FF5FA2`, xanh trời `#83D9FF`, mơ `#FFC38A` | như bên trái |

Màu sticker chỉ để nhận diện chuyến đi, không bao giờ mang nghĩa trạng thái, tiền hay cảnh báo.

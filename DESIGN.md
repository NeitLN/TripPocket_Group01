---
name: TripPocket
description: Mỗi chuyến đi là một con sticker cắt viền trắng trên nền lilac; violet cho mọi thao tác, lime chỉ cho một điểm nhấn.
colors:
  primary-violet: "#573AD6"
  on-primary: "#FFFFFF"
  primary-container: "#E6DEFF"
  on-primary-container: "#21105D"
  secondary-dusk: "#62577C"
  secondary-container: "#E9DDFB"
  tertiary-olive: "#4F6500"
  brand-lime: "#C6F432"
  on-brand-lime: "#1B1B1F"
  error-raspberry: "#AD1743"
  error-container: "#FFD9E1"
  lilac-ground: "#F7F5FF"
  ink: "#1B1B1F"
  on-surface-variant: "#4D465B"
  surface-container-low: "#F2EEFC"
  surface-container: "#ECE7F7"
  surface-container-high: "#E6E0F1"
  outline: "#786D89"
  outline-variant: "#C9BED7"
  trip-card-white: "#FFFFFF"
  sticker-border: "#FFFFFF"
  sticker-violet: "#7C5CFF"
  sticker-lime: "#C6F432"
  sticker-pink: "#FF5FA2"
  sticker-sky: "#83D9FF"
  sticker-apricot: "#FFC38A"
  balance-positive: "#1B6D2F"
  balance-negative: "#AD1743"
  night-primary-lavender: "#CBBEFF"
  night-ground: "#141023"
  night-on-surface: "#EEE8FA"
  night-on-surface-variant: "#CCC2DA"
  night-trip-card: "#1D182D"
  night-error: "#FFB1C2"
  night-balance-positive: "#8BD88E"
typography:
  screen-title:
    fontFamily: "Be Vietnam Pro Black"
    fontSize: "34sp"
    fontWeight: 900
    lineHeight: "44sp"
    letterSpacing: "0"
  display-small:
    fontFamily: "Be Vietnam Pro Black"
    fontSize: "36sp"
    fontWeight: 900
    lineHeight: "46sp"
    letterSpacing: "0"
  headline-medium:
    fontFamily: "Be Vietnam Pro ExtraBold"
    fontSize: "28sp"
    fontWeight: 800
    lineHeight: "38sp"
    letterSpacing: "0"
  headline-small:
    fontFamily: "Be Vietnam Pro ExtraBold"
    fontSize: "24sp"
    fontWeight: 800
    lineHeight: "34sp"
    letterSpacing: "0"
  title-large:
    fontFamily: "Be Vietnam Pro SemiBold"
    fontSize: "22sp"
    fontWeight: 600
    lineHeight: "30sp"
    letterSpacing: "0"
  card-title:
    fontFamily: "Be Vietnam Pro ExtraBold"
    fontSize: "16sp"
    fontWeight: 800
    lineHeight: "24sp"
    letterSpacing: "0"
  title-medium:
    fontFamily: "Be Vietnam Pro SemiBold"
    fontSize: "16sp"
    fontWeight: 600
    lineHeight: "24sp"
    letterSpacing: "0"
  money:
    fontFamily: "Be Vietnam Pro SemiBold"
    fontSize: "16sp"
    fontWeight: 600
    lineHeight: "24sp"
    letterSpacing: "0"
    fontFeature: "tnum"
  body-large:
    fontFamily: "Be Vietnam Pro Regular"
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: "24sp"
    letterSpacing: "0"
  body-medium:
    fontFamily: "Be Vietnam Pro Regular"
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: "22sp"
    letterSpacing: "0"
  body-small:
    fontFamily: "Be Vietnam Pro Regular"
    fontSize: "12sp"
    fontWeight: 400
    lineHeight: "18sp"
    letterSpacing: "0"
  label-large:
    fontFamily: "Be Vietnam Pro SemiBold"
    fontSize: "14sp"
    fontWeight: 600
    lineHeight: "20sp"
    letterSpacing: "0"
  label-large-heavy:
    fontFamily: "Be Vietnam Pro ExtraBold"
    fontSize: "14sp"
    fontWeight: 800
    lineHeight: "20sp"
    letterSpacing: "0"
  sticker-code:
    fontFamily: "Be Vietnam Pro Black"
    fontSize: "20sp"
    fontWeight: 900
    lineHeight: "28sp"
    letterSpacing: "0"
  wordmark:
    fontFamily: "Be Vietnam Pro Black"
    fontSize: "24sp"
    fontWeight: 900
    lineHeight: "34sp"
    letterSpacing: "0"
rounded:
  chip: "8dp"
  field: "12dp"
  title-highlight: "10dp"
  sticker: "18dp"
  card: "20dp"
  sheet: "28dp"
  pill: "50%"
spacing:
  xxs: "2dp"
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  lg: "16dp"
  xl: "24dp"
  xxl: "32dp"
  screen-margin: "16dp"
  title-inset: "20dp"
  login-padding: "24dp"
components:
  button-filled:
    backgroundColor: "{colors.primary-violet}"
    textColor: "{colors.on-primary}"
    typography: "{typography.label-large}"
    rounded: "{rounded.pill}"
    height: "56dp"
  button-text:
    textColor: "{colors.primary-violet}"
    typography: "{typography.label-large}"
    rounded: "{rounded.pill}"
    height: "48dp"
  fab-new-trip:
    backgroundColor: "{colors.brand-lime}"
    textColor: "{colors.on-brand-lime}"
    typography: "{typography.label-large-heavy}"
    rounded: "{rounded.pill}"
  trip-card:
    backgroundColor: "{colors.trip-card-white}"
    rounded: "{rounded.card}"
    padding: "12dp 16dp 12dp 8dp"
  trip-sticker:
    backgroundColor: "{colors.sticker-violet}"
    textColor: "{colors.ink}"
    typography: "{typography.sticker-code}"
    rounded: "{rounded.sticker}"
    size: "64dp"
  title-highlight:
    backgroundColor: "{colors.brand-lime}"
    textColor: "{colors.on-brand-lime}"
    rounded: "{rounded.title-highlight}"
    padding: "0 8dp"
  text-field:
    textColor: "{colors.ink}"
    typography: "{typography.body-large}"
    rounded: "{rounded.field}"
  group-label:
    textColor: "{colors.ink}"
    typography: "{typography.label-large-heavy}"
    padding: "16dp 12dp 4dp 12dp"
---

# Design System: TripPocket

## Overview

**Creative North Star: "Túi sticker của chuyến đi"**

Mỗi chuyến đi trong TripPocket là một con sticker cắt viền trắng, giống sticker bạn bè thả vào group chat của chuyến đi. Nền app là màu lilac nhạt (`md_background`), thẻ chuyến đi màu trắng bo góc 20dp, mọi thứ bấm được đều là violet (`?attr/colorPrimary`). Lime chỉ xuất hiện ở hai chỗ: nút "New trip" và một chữ được dán sticker trong tiêu đề màn hình. Sticker chỉ dùng để nhận ra chuyến đi; trạng thái, tiền và nút bấm thì luôn trình bày thẳng, không trang trí.

Mật độ vừa phải: mỗi thẻ chuyến đi là một cột chữ có thể dãn ra (tên, điểm đến, ngày + số ngày, thành viên + đã chi, dòng trạng thái sao lưu) đặt cạnh một ô sticker 80dp. Không có chiều cao cố định, nên cỡ chữ hệ thống 200% vẫn đọc được. Cả app dùng một họ chữ duy nhất là Be Vietnam Pro (đóng gói sẵn trong `res/font`, chạy offline), đọc tốt tiếng Việt có dấu.

Nhóm đã chủ động bỏ kiểu mặc định của các app du lịch: app bar trắng, thẻ trắng trên nền trắng và một màu xanh dương làm điểm nhấn. App bar của TripPocket phẳng, cùng màu nền lilac.

**Key Characteristics:**
- Nền lilac `#F7F5FF` (tối: ink-violet `#141023`), thẻ trắng (tối: `#1D182D`), bo góc 20dp, không đổ bóng.
- Violet cho mọi thao tác; lime đúng một lần trên mỗi màn hình cho hành động chính và một lần cho chữ được tô.
- Sticker sinh tự động từ điểm đến: 5 màu, mã 2 ký tự, viền trắng 3dp, nghiêng -3/0/+3 độ.
- Một họ chữ Be Vietnam Pro: 900 cho wordmark, tiêu đề màn hình và mã sticker; 800 cho headline và tên thẻ; 600 cho title, label, tiền; 400 cho body.
- Light và dark đều là giao diện chính thức, không phải bản phụ.

## Colors

Bảng màu tím mát trên nền lilac, một màu lime rất to tiếng được dùng dè sẻn, và năm màu sticker chỉ dùng để nhận diện.

Mọi màu đều khai báo trong `res/values/colors.xml` (sáng) và `res/values-night/colors.xml` (tối), rồi được `Base.Theme.TripPocket` trong `themes.xml` gán vào các thuộc tính Material 3. **Trong layout chỉ dùng `?attr/...`**, không dùng `@color/md_*` và tuyệt đối không viết mã hex.

### Primary
- **Violet thao tác** (`md_primary` → `?attr/colorPrimary`; tối là lavender `#CBBEFF`): nút filled, nút text, link, wordmark, biểu tượng túi. Đây là màu của "bấm được". Màu violet sáng hơn `sticker_violet` (#7C5CFF) chỉ là màu tô sticker, không bao giờ dùng cho nút.
- **Violet container** (`md_primary_container` → `?attr/colorPrimaryContainer`): nền nhạt cho phần tử đang chọn, chip được chọn.

### Secondary
- **Tím xám** (`md_secondary`, `md_secondary_container`): thành phần phụ của Material (chip, tonal button) khi cần một tông dịu hơn violet.

### Tertiary
- **Lime thương hiệu** (`brand_lime`, cũng là `md_tertiary_container`; giữ nguyên `#C6F432` ở cả hai theme): chỉ dành cho FAB "New trip" (`Widget.TripPocket.ExtendedFab.Lime`) và chữ được dán sticker trong tiêu đề (`StickerHighlightSpan`). Chữ trên lime luôn là `on_brand_lime`. `md_tertiary` (olive `#4F6500`, tối là lime) là bản đủ tương phản để làm chữ/biểu tượng trên nền sáng nếu Material component cần.

### Error và tiền
- **Đỏ mâm xôi** (`md_error` → `?attr/colorError`; tối `#FFB1C2`): lỗi form và biểu tượng "Backup failed". Luôn kèm chữ.
- **Tiền dương / âm** (`balance_positive`, `balance_negative`; có bản tối riêng): chỉ cho số dư thành viên được nhận lại / còn nợ ở màn Expenses, luôn đi cùng chữ "gets back" / "owes" (`ExpenseAdapters` gán cặp chữ + màu cùng lúc). Đây là hai màu `@color/` hợp lệ duy nhất trong layout ngoài thương hiệu, vì Material không có vai trò tương ứng.

### Neutral
- **Nền lilac** (`md_background` / `md_surface` → `?android:attr/colorBackground`, `?attr/colorSurface`): nền màn hình và app bar.
- **Thẻ trắng** (`trip_card_surface`; tối `#1D182D`): nền `MaterialCardView` qua `Widget.TripPocket.Card`; không cần set lại trong layout.
- **Mực** (`md_on_surface` → `?attr/colorOnSurface`): chữ chính, tên chuyến, nhãn nhóm, nhãn trạng thái.
- **Mực phụ** (`md_on_surface_variant` → `?attr/colorOnSurfaceVariant`): điểm đến, ngày, ghi chú "Saved on this phone", helper text, biểu tượng trạng thái bình thường.
- **Các tầng container** (`md_surface_container_low` … `_highest` → `?attr/colorSurfaceContainer*`): sheet, menu, ô nhập, vùng nhóm nội dung.
- **Viền** (`md_outline`, `md_outline_variant` → `?attr/colorOutline`, `?attr/colorOutlineVariant`): viền ô nhập, đường chia.

### Sticker
- **Năm màu sticker** (`sticker_violet`, `sticker_lime`, `sticker_pink`, `sticker_sky`, `sticker_apricot`, mỗi màu có `on_sticker_*` cho chữ, viền `sticker_border` trắng): giống hệt nhau ở hai theme. Chỉ `TripStickerView` được dùng các màu này.

### Named Rules
**The One Loud Lime Rule.** Mỗi màn hình có tối đa một hành động lime (FAB "New trip" ở Trip List) và tối đa một chữ được dán sticker lime trong tiêu đề. Màn hình của module khác không thêm nút lime, chip lime hay nền lime; hành động chính của các bạn dùng nút filled violet hoặc FAB mặc định của theme.

**The Sticker Is Not a Signal Rule.** Màu sticker không bao giờ mang nghĩa trạng thái, tiền, cảnh báo hay danh mục. Trạng thái dùng `?attr/colorError` / `?attr/colorOnSurfaceVariant` kèm biểu tượng và chữ; tiền dùng `balance_*` kèm chữ.

**The No-Hex Rule.** Layout và code UI không chứa mã màu. Cần màu thì lấy thuộc tính theme (`?attr/colorPrimary`, `MaterialColors.getColor(view, R.attr.colorError)` trong Kotlin). Thêm màu thương hiệu mới thì thêm vào cả `values/colors.xml` và `values-night/colors.xml`.

## Typography

**Display Font:** Be Vietnam Pro Black (900), `@font/be_vietnam_pro_black`
**Body Font:** Be Vietnam Pro Regular (400), `@font/be_vietnam_pro_regular`
**Label Font:** Be Vietnam Pro SemiBold (600) và ExtraBold (800)

**Character:** Một họ chữ hình học, tròn, đọc tốt dấu tiếng Việt; độ đậm 900 làm "giọng sticker" cho tiêu đề và mã sticker, còn 400/600 giữ phần làm việc yên tĩnh. Mọi vai trò đều có letterSpacing 0.

Font được gán qua các text appearance trong `themes.xml`, không gán `fontFamily` ở cấp theme (sẽ đè lên text appearance). Trong layout dùng `android:textAppearance="?attr/textAppearance..."` hoặc style `TextAppearance.TripPocket.*` bên dưới; không set `android:fontFamily` hay `textStyle="bold"`.

### Hierarchy
- **Screen title** (`TextAppearance.TripPocket.ScreenTitle`, 900): tiêu đề lớn đầu màn hình danh sách, ví dụ "My trips". Đặt `accessibilityHeading="true"`.
- **Display** (`?attr/textAppearanceDisplayLarge/Medium/Small`, 900): cho một con số lớn duy nhất của màn hình (Weather dùng DisplaySmall cho nhiệt độ hiện tại).
- **Headline** (`?attr/textAppearanceHeadline*`, 800): tiêu đề form ("Sign in" dùng HeadlineMedium), tiêu đề trạng thái rỗng (HeadlineSmall).
- **Title** (`?attr/textAppearanceTitle*`, 600): tiêu đề toolbar (TitleLarge, gán sẵn qua `Widget.TripPocket.Toolbar`), tiêu đề mục.
- **Card title** (`TextAppearance.TripPocket.CardTitle`, 800): tên chuyến đi / tên mục trong thẻ, tối đa 2 dòng, `ellipsize="end"`.
- **Body** (`?attr/textAppearanceBody*`, 400): điểm đến, ngày, mô tả, ghi chú. BodyMedium là mặc định của theme. BodySmall cho ghi chú phụ như "Saved on this phone".
- **Label** (`?attr/textAppearanceLabel*`, 600): nút, nhãn trạng thái sao lưu (LabelLarge).
- **Label heavy** (`TextAppearance.TripPocket.LabelLarge.Heavy`, 800): nhãn nhóm "Happening now / Upcoming / Past" và chữ trên FAB lime.
- **Overline** (`TextAppearance.TripPocket.Overline`, 600, màu `?attr/colorOnSurfaceVariant`): nhãn mục nhỏ, viết thường kiểu câu, không in hoa toàn bộ.
- **Money** (`TextAppearance.TripPocket.Money`, 600, `tnum`): mọi con số tiền, để các cột số thẳng hàng.
- **Sticker code** (`TextAppearance.TripPocket.StickerCode`, 900): chỉ `TripStickerView` dùng.
- **Wordmark** (`TextAppearance.TripPocket.Wordmark`, 900, `?attr/colorPrimary`): chữ "TripPocket" cạnh biểu tượng túi.

### Named Rules
**The sp-Only Rule.** Cỡ chữ luôn là `sp` và chiều cao khối chữ luôn `wrap_content`. Kiểm tra mỗi màn ở cỡ chữ hệ thống 200%: chữ được xuống dòng, không bị cắt, không chồng lên nhau.

**The Sentence-Case Rule.** Nhãn mục và nhãn nhóm viết kiểu câu, không `textAllCaps`, không giãn chữ.

## Layout

Lưới 4dp. Khoảng cách lấy từ `res/values/dimens.xml`: `space_xxs` 2dp, `space_xs` 4dp, `space_sm` 8dp, `space_md` 12dp, `space_lg` 16dp, `space_xl` 24dp, `space_xxl` 32dp. Lề màn hình `screen_margin` là 16dp trên điện thoại và 32dp trên máy tính bảng (`values-sw600dp`).

- **Chiều rộng đọc được:** danh sách dừng ở `content_max_width` (720dp), form dừng ở `form_max_width` (480dp), qua `app:layout_constraintWidth_max`.
- **Cột lưới:** Trip List dùng `GridLayoutManager` với `@integer/trip_grid_span`: 1 cột điện thoại dọc, 2 cột khi ngang hoặc máy tính bảng, 3 cột máy tính bảng ngang; nhãn nhóm chiếm hết các cột.
- **Khoảng trống cho FAB:** danh sách có FAB phải đặt `paddingBottom="@dimen/fab_clearance"` (88dp) và `clipToPadding="false"` để thẻ cuối cuộn lên trên FAB.
- **Thẻ trong danh sách:** cách nhau `space_xs` theo chiều dọc, `space_sm` theo chiều ngang; RecyclerView thêm `space_sm` padding ngang.
- **Nhãn nhóm:** nhiều khoảng trên (16dp) hơn khoảng dưới (4dp) để nhãn dính vào nhóm của nó.
- **Login:** một cột cuộn được, padding `login_padding` 24dp; bản ngang (`layout-land`) chia hai cột theo Guideline 40%: lockup + sticker bên trái, form bên phải.
- **Edge-to-edge:** theme bật biểu tượng thanh hệ thống tối trên nền sáng và sáng trên nền tối qua `@bool/light_system_bars`; màn hình tự xử lý inset hệ thống.

### Named Rules
**The 48dp Target Rule.** Mọi vùng chạm tối thiểu 48dp. Nút text nhỏ (như "Retry", "Create account") đặt `android:minHeight="48dp"`; nút filled chính trên form dùng 56dp.

## Elevation & Depth

Hệ thống phẳng, độ sâu đến từ tầng màu chứ không từ bóng. Thẻ (`card_elevation` 0dp) nổi lên nhờ màu trắng trên nền lilac (tối: `#1D182D` trên `#141023`). App bar phẳng, cùng màu nền. Sheet và menu dùng các tầng `?attr/colorSurfaceContainer*`.

Ngoại lệ duy nhất là sticker: ở theme sáng `TripStickerView` có elevation 2dp (`sticker_elevation`) cho một bóng ngắn, mềm, giống sticker dán trên giấy; ở theme tối elevation về 0 và viền trắng 3dp tự tách sticker khỏi nền. FAB lime giữ elevation mặc định của Material.

### Named Rules
**The Flat Card Rule.** Thẻ không đổ bóng và không có viền; tách khỏi nền bằng màu `trip_card_surface`.

## Shapes

Góc bo tròn, mềm, tăng theo kích thước bề mặt, được gán vào thang shape của Material trong theme:

- **8dp** (`shapeAppearanceCornerSmall`): chip.
- **12dp** (`shapeAppearanceCornerExtraSmall`): ô nhập, menu.
- **20dp** (`shapeAppearanceCornerMedium` / `Large`, `card_corner_radius`): thẻ.
- **28dp** (`shapeAppearanceCornerExtraLarge`): bottom sheet, dialog.
- **Pill 50%** (`ShapeAppearance.TripPocket.Pill`): mọi nút (filled, outlined, text) và FAB.
- **18dp** (`sticker_corner_radius`) cho sticker và **10dp** (`title_highlight_radius`) cho chữ được dán sticker: chỉ hai component thương hiệu dùng.

Hình "die-cut" gồm ba thứ: góc tròn, viền trắng 3dp (`sticker_border_width`, `sticker_border`), và độ nghiêng nhỏ. Chỉ sticker, chữ tô lime và biểu tượng túi mang hình này.

## Components

### Buttons
Tròn như viên thuốc, rõ ràng, không trang trí.
- **Shape:** pill (50%), gán sẵn qua `materialButtonStyle`, `materialButtonOutlinedStyle`, `borderlessButtonStyle`.
- **Filled:** `MaterialButton` không cần style: nền `?attr/colorPrimary`, chữ `?attr/colorOnPrimary`, LabelLarge. Nút chính của form: `minHeight="56dp"`, rộng hết cột form.
- **Text:** `style="@style/Widget.TripPocket.Button.Text"`, chữ violet, `minHeight="48dp"`.
- **Outlined:** `style="@style/Widget.TripPocket.Button.Outlined"` cho hành động phụ có viền.
- **Trạng thái:** ripple và focus mặc định của Material 3; không tự vẽ thêm.

### FAB "New trip"
- `ExtendedFloatingActionButton` với `style="@style/Widget.TripPocket.ExtendedFab.Lime"`: nền `brand_lime`, chữ + icon `on_brand_lime`, LabelLarge.Heavy, pill.
- Đặt góc dưới phải, margin `screen_margin`. Thu về chỉ còn icon khi cuộn xuống, mở lại khi cuộn lên (`shrink()` / `extend()`).
- Chỉ Trip List có FAB lime. FAB ở màn khác (nếu có) dùng style FAB mặc định của theme.

### Cards / Containers
- **Corner Style:** 20dp.
- **Background:** `trip_card_surface` qua `Widget.TripPocket.Card` (là `materialCardViewStyle` mặc định nên `MaterialCardView` tự nhận).
- **Shadow Strategy:** phẳng, `cardElevation="@dimen/card_elevation"` (0dp).
- **Border:** không.
- **Internal Padding:** 12dp trên/dưới, 16dp phải, 8dp trái (bên trái là ô sticker 80dp đã có khoảng trống riêng).
- Toàn thẻ bấm được (`clickable`, `focusable`); nút bên trong (Retry) là một vùng chạm riêng 48dp.

### Inputs / Fields
- **Style:** `Widget.Material3.TextInputLayout.OutlinedBox` (là `textInputStyle` mặc định), bo 12dp, có icon đầu dòng khi giúp nhận ra ô (email, khoá).
- **Focus:** viền violet mặc định của Material.
- **Error:** dùng `TextInputLayout.error` (màu `?attr/colorError`), có chữ giải thích; helper text cho yêu cầu đã biết trước (ví dụ độ dài mật khẩu).
- Đặt `autofillHints`, `inputType`, `imeOptions` đúng cho từng ô.

### Navigation
- **App bar:** `MaterialToolbar` phẳng cùng màu nền, tiêu đề TitleLarge qua `Widget.TripPocket.Toolbar`. Màn chính mang lockup (biểu tượng túi 32dp + wordmark), cả lockup được đọc là một "TripPocket" cho TalkBack.
- **Nhãn nhóm danh sách:** `TextAppearance.TripPocket.LabelLarge.Heavy`, `?attr/colorOnSurface`, `accessibilityHeading="true"`.

### Trạng thái sao lưu
Mọi chỗ hiển thị trạng thái sao lưu của chuyến đi dùng đúng bộ từ trong `SyncStatus`:
- **Backed up** (`sync_synced`, `ic_cloud_done`)
- **Waiting for backup** (`sync_pending`, `ic_cloud_upload`)
- **Backup failed** (`sync_failed`, `ic_cloud_alert`)

Cấu trúc: icon 16dp + nhãn LabelLarge `?attr/colorOnSurface`; khi chưa sao lưu xong thì thêm dòng BodySmall "Saved on this phone" (`sync_saved_on_phone`) màu `?attr/colorOnSurfaceVariant` ngay dưới nhãn. Icon dùng `?attr/colorError` khi thất bại, còn lại `?attr/colorOnSurfaceVariant`. Nút "Retry" (`sync_retry`) là nút text 48dp riêng, chỉ hiện khi thất bại. Dòng tóm tắt đầu danh sách ghép các số đếm bằng " · " và bỏ qua số 0 ("3 trips · 1 waiting for backup · 1 backup failed").

### Tiền
- Luôn định dạng bằng `Format.vnd()` (dạng `2,150,000 ₫`), `Format.amount()` hoặc `Format.signedVnd()`; không tự nối chuỗi.
- Luôn dùng `TextAppearance.TripPocket.Money` (600 + `tnum`) để số thẳng cột.
- Màu tiền mặc định là `?attr/colorOnSurface`. Chỉ số dư thành viên mới dùng `balance_positive` / `balance_negative`, và luôn kèm chữ "gets back" / "owes"; không bao giờ chỉ dựa vào màu hoặc dấu +/-.

### Trip sticker (`TripStickerView`)
Thành phần đặc trưng của TripPocket: một ô vuông bo 18dp, tô một trong năm màu, viền trắng 3dp, mã điểm đến bằng chữ 900.
- **Cách dùng:** đặt `vn.edu.vlu.trippocket.ui.common.TripStickerView` 64dp (`@dimen/sticker_size`) ở giữa một `FrameLayout` 80dp (`@dimen/sticker_slot`) có `clipChildren="false"`, rồi gọi `bind(destination, tripId)`. Ô 80dp giữ cho góc nghiêng và bóng không chạm vào chữ.
- **Quy tắc sinh (`TripSticker`):** màu từ tên địa điểm trước dấu phẩy ("Đà Lạt, Lâm Đồng" và "Đà Lạt" cùng màu); mã là chữ cái đầu của hai từ đầu, hoặc hai chữ cái đầu nếu chỉ có một từ, viết hoa và giữ dấu ("ĐL", "HU"); độ nghiêng -3, 0 hoặc +3 độ theo `tripId`. Cùng chuyến thì sticker giống hệt nhau ở mọi màn.
- **Cỡ chữ lớn:** nếu mã không vừa, view bỏ chữ và hiện biểu tượng túi (`ic_sticker_pocket`) thay vì thu nhỏ chữ.
- **Trợ năng:** sticker là trang trí (`IMPORTANT_FOR_ACCESSIBILITY_NO`); tên và ngày chuyến đi phải luôn nằm ngay cạnh.
- Màn của module khác (Itinerary, Weather, Expenses) có thể đặt sticker của chuyến đang mở ở phần đầu màn hình để nhận diện, nhưng không dùng sticker làm nút, làm biểu tượng danh mục hay làm chỉ báo trạng thái.

### Chữ dán sticker (`StickerHighlightSpan`)
- Đặt một từ của tiêu đề lên nền lime có viền trắng ("My [trips]"); chữ gốc không đổi nên TalkBack đọc tiêu đề bình thường.
- Dùng với `TextAppearance.TripPocket.ScreenTitle`, màu `brand_lime` / `sticker_border` / `on_brand_lime`, bo `title_highlight_radius`, đệm `title_highlight_pad`, viền `sticker_border_width` (xem `TripListFragment.highlightTitle()`).
- Tối đa một từ trên một màn hình, và vẫn tính vào quy tắc một lime.

### Logo
- Lockup = `@drawable/ic_logo_pocket` + TextView `TextAppearance.TripPocket.Wordmark` ("TripPocket", đúng hoa/thường). Biểu tượng túi violet nghiêng -6°, viền trắng, đổi sang lavender ở theme tối vì dùng `md_primary`.
- Icon app: túi trên nền lime cố định (`ic_launcher_background`), có bản đơn sắc `ic_launcher_monochrome` cho icon theo theme.
- Không dùng PNG logo trong app; không kéo giãn, đổi font, thêm bóng hay xoay thêm. Chi tiết tài sản ảnh ở `docs/brand/README.md`.

## Do's and Don'ts

### Do:
- **Do** lấy màu bằng thuộc tính theme (`?attr/colorPrimary`, `?attr/colorOnSurface`, `?attr/colorOnSurfaceVariant`, `?attr/colorError`, `?attr/colorSurfaceContainer*`).
- **Do** dùng text appearance theo vai trò (`?attr/textAppearanceBodyMedium`, `TextAppearance.TripPocket.Money`, `TextAppearance.TripPocket.CardTitle`…) thay vì set cỡ chữ và font riêng.
- **Do** dùng `MaterialCardView` / `MaterialButton` không kèm màu hay bo góc riêng; theme đã lo 20dp cho thẻ và pill cho nút.
- **Do** lấy khoảng cách từ `@dimen/space_*`, `screen_margin`, `content_max_width`, `form_max_width`.
- **Do** để mọi vùng chạm từ 48dp trở lên và mọi khối chữ `wrap_content`.
- **Do** ghi trạng thái sao lưu bằng icon + nhãn + "Saved on this phone", và viết tiền bằng `Format.vnd()` + style Money.
- **Do** kiểm tra mỗi màn ở theme sáng, theme tối và cỡ chữ hệ thống 200%.
- **Do** đánh dấu tiêu đề màn và tiêu đề mục bằng `accessibilityHeading="true"`, và đặt `contentDescription="@null"` cho hình trang trí.

### Don't:
- **Don't** viết mã hex hay `@color/md_*` trong layout; ngoại lệ duy nhất là `balance_positive` / `balance_negative` cho số dư.
- **Don't** thêm lime vào màn của module khác: không nút lime, chip lime, nền lime, chữ tô lime thứ hai.
- **Don't** dùng màu sticker cho trạng thái, danh mục chi tiêu, thời tiết hay tiền.
- **Don't** dùng sticker làm nút bấm, và không đặt sticker mà không có tên chuyến đi bên cạnh.
- **Don't** dùng `sticker_violet` (#7C5CFF) cho nút hay link; màu thao tác là `?attr/colorPrimary`.
- **Don't** thêm bóng đổ hay viền cho thẻ, và không làm app bar trắng tách khỏi nền.
- **Don't** viết nhãn mục kiểu IN HOA giãn chữ phía trên tiêu đề; nhãn mục viết kiểu câu bằng `TextAppearance.TripPocket.Overline`.
- **Don't** chỉ dùng màu để báo trạng thái hay nợ/có; luôn có chữ đi kèm.
- **Don't** đặt chiều cao cố định cho hàng có chữ, và không dùng `dp` cho cỡ chữ.
- **Don't** set `android:fontFamily` trong layout hay thêm font khác ngoài Be Vietnam Pro.

## Checklist trước khi mở PR

- [ ] Không có mã hex hay `@color/md_*` trong layout/code UI mới (`grep -n "#[0-9A-Fa-f]\{6\}" res/layout`).
- [ ] Chữ dùng `?attr/textAppearance*` hoặc `TextAppearance.TripPocket.*`; không có `fontFamily`, `textAllCaps`, cỡ chữ `dp`.
- [ ] Không có màu lime mới; FAB lime và chữ tô lime chỉ có ở Trip List.
- [ ] Sticker (nếu dùng) là `TripStickerView` 64dp trong ô 80dp, gọi `bind(destination, tripId)`, có tên chuyến đi bên cạnh.
- [ ] Trạng thái sao lưu dùng đúng chữ trong `SyncStatus` + icon + "Saved on this phone".
- [ ] Số tiền dùng `Format.vnd()` / `signedVnd()` + `TextAppearance.TripPocket.Money`; màu số dư luôn kèm chữ "owes" / "gets back".
- [ ] Mọi nút và hàng bấm được cao tối thiểu 48dp.
- [ ] Đã xem màn hình ở theme sáng, theme tối, cỡ chữ 200% và xoay ngang; không chữ nào bị cắt hay chồng.
- [ ] Danh sách có FAB đã chừa `fab_clearance` và `clipToPadding="false"`.
- [ ] Hình trang trí có `contentDescription="@null"`; tiêu đề có `accessibilityHeading="true"`.

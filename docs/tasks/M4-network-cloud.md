# M4: Network layer, RESTful API & Cloud services (tuần 08 đến 09)

Phiếu việc cho Milestone 4. Rubric chấm 4 phần: Retrofit + Coroutines (30%), Firebase Auth + Glide (30%),
MVVM đúng luồng `API -> Repository -> ViewModel -> StateFlow -> ViewBinding` (20%), Git + WBS (20%).
Phần Git chấm theo **commit của từng người**, nên mỗi người tự code và tự commit lát của mình bằng tài khoản GitHub
của mình. Không commit hộ.

## Quy trình Git

```
develop
 └── feature/network-cloud-integration        (nhánh bắt buộc của milestone, Tiến quản lý)
      ├── feature/tien-M4-trip-ideas           PR -> feature/network-cloud-integration
      ├── feature/bao-M4-nearby-sights         PR -> feature/network-cloud-integration
      ├── feature/duyanh-M4-place-photos       PR -> feature/network-cloud-integration
      └── feature/minh-M4-exchange-rates       PR -> feature/network-cloud-integration
```

1. `git fetch origin`
2. `git checkout -b feature/<tên>-M4-<việc> origin/feature/network-cloud-integration`
3. Code, commit nhỏ theo từng bước (data -> repository -> ViewModel -> UI -> test), tiền tố `M4 <Tên>:`.
4. Trước khi mở PR: `./gradlew testDebugUnitTest lintDebug assembleDebug` PASS.
5. Mở PR **vào `feature/network-cloud-integration`** (không phải `develop`). Chủ module review lượt đầu, Tiến merge.
6. Khi đủ 4 lát, Tiến mở một PR `feature/network-cloud-integration -> develop`. Link PR đã merge đó là link nộp bài.

## Đồ dùng chung đã có trên nhánh tích hợp (đừng viết lại)

| Thứ | Ở đâu | Dùng thế nào |
|---|---|---|
| `safeApiCall` + `ApiResult` | `data/remote/ApiResult.kt` | `suspend fun x() = safeApiCall(io) { api.x() }` chạy trên `Dispatchers.IO`, trả `ApiResult.Success(data)` hoặc `ApiResult.Failure(ApiError)`; không ném lỗi ra ViewModel. |
| `ApiError` | `domain/ApiError.kt` | OFFLINE (máy bay, mất Wi-Fi), TIMEOUT, BAD_REQUEST (400), NOT_FOUND (404), RATE_LIMITED (429), SERVER (5xx), BAD_RESPONSE (JSON lạ). `error.messageRes` là câu báo lỗi đã viết sẵn. |
| Glide | `util/ImageLoading.kt` | Trong `onBindViewHolder`: `imageView.loadRemote(url, placeholder = R.drawable.ic_image, error = R.drawable.ic_broken_image)`; trong `onViewRecycled`: `imageView.clearRemote()`. Đã có placeholder, ảnh lỗi, cache bộ nhớ + đĩa. |
| Banner lỗi | `res/layout/view_network_error.xml` | `<include android:id="@+id/networkError" layout="@layout/view_network_error" />`, rồi `binding.networkError.tvNetworkError.setText(error.messageRes)` và `binding.networkError.btnNetworkRetry.setOnClickListener { viewModel.retry() }`. |
| OkHttp | `di/AppContainer.kt` | Đã có timeout 10/15 giây và User-Agent. Retrofit sẵn có: `wikipediaRetrofit`, `exchangeRateRetrofit`. Bạn chỉ thêm 1 dòng repository của mình vào AppContainer. |
| Mẫu đầy đủ | lát Trip Ideas của Tiến | `data/remote/mockapi/`, `data/repository/TripIdeaRepository.kt`, `ui/ideas/`, `TripIdeaRepositoryTest`, `TripIdeasViewModelTest`. Chép khung, đổi nội dung. |

## Chuẩn "xong" chung cho mỗi lát

1. Interface Retrofit `suspend` riêng trong `data/remote/<nguồn>/`, DTO có field nullable, hàm map sang model bỏ dòng hỏng.
2. Repository gọi API qua `safeApiCall` và trả `ApiResult`. Không Activity/Fragment/Adapter nào gọi API hay tạo Retrofit.
3. ViewModel có `XxxUiState` với đủ 3 trạng thái (Loading, Success, Error), lộ ra bằng `StateFlow`, chạy trong `viewModelScope`; có `retry()`.
4. Màn hình: `ProgressBar`/`CircularProgressIndicator` khi tải, `RecyclerView` qua ViewBinding khi có dữ liệu, banner lỗi + Retry khi lỗi. Bật chế độ máy bay rồi mở màn: hiện lỗi OFFLINE, không crash; tắt máy bay, bấm Retry: có dữ liệu.
5. Ảnh từ URL tải bằng Glide trong `RecyclerView.Adapter` (placeholder, ảnh lỗi, cache).
6. Test: repository trên `MockWebServer` (đúng method + đường dẫn, 404, 500, JSON lạ) và ViewModel (Loading -> Success, Error -> Retry -> Success).
7. Gửi Tiến 3 ảnh chụp màn hình: đang tải, có dữ liệu, lỗi khi bật máy bay (cho worksheet).
8. Mô tả PR ghi: endpoint, method, các mã lỗi đã xử lý, cách tự kiểm.

## Bảo (Itinerary): Nearby sights

Gợi ý điểm tham quan gần chuyến đi, thêm thẳng vào lịch trình.

- Endpoint (GET, không cần key):
  `https://en.wikipedia.org/w/api.php?action=query&generator=geosearch&ggscoord={lat}|{lon}&ggsradius=10000&ggslimit=20&prop=pageimages|description|coordinates&piprop=thumbnail&pithumbsize=400&format=json&formatversion=2`
  Kiểm thử ngày 09/11 với Đà Lạt (11.9404|108.4583): trả về Xuân Hương Lake, Da Lat station, Lycée Yersin... mỗi trang có `title`, `description`, `thumbnail.source`, `coordinates[0].lat/lon`.
- Toạ độ: saved place đầu tiên của chuyến (`SavedPlaceRepository`, W2). Chuyến chưa có địa điểm: trạng thái rỗng "Add a place in Weather first".
- File gợi ý: `data/remote/wikipedia/GeoSearchApi.kt` (tạo từ `wikipediaRetrofit`), `GeoSearchResponse.kt` (DTO + `toSights()`; không có `query` = danh sách rỗng, không phải lỗi), `data/repository/SightRepository.kt`, `ui/itinerary/NearbySightsFragment.kt` + `NearbySightsViewModel.kt` + `SightAdapter.kt` + `item_sight.xml`, mở từ menu của tab Itinerary.
- Mỗi dòng: ảnh thumbnail (Glide), tên, mô tả, khoảng cách tới địa điểm; nút "Add to day" tạo hoạt động qua `ItineraryRepository` đã có (giữ giới hạn 200).
- Test: `SightRepositoryTest` (MockWebServer: đường dẫn có `ggscoord`, JSON rỗng, 500), `NearbySightsViewModelTest`.

## Duy Anh (Weather): ảnh và giới thiệu địa điểm + Weather dùng ApiError chung

- Endpoint (GET): `https://en.wikipedia.org/api/rest_v1/page/summary/{title}` (khoảng trắng đổi thành `_`).
  Có bài: `title`, `description`, `extract`, `thumbnail.source`. Không có bài: HTTP 404 (`{"status":404,...}`).
- Kết quả tìm kiếm (W1, `PlaceResultAdapter`): mỗi dòng có ảnh thumbnail tải bằng Glide. ViewModel gọi summary cho tối đa 10 kết quả đầu (song song, `async` trong `viewModelScope`), 404 nghĩa là "không có ảnh" (hiện ảnh mặc định), không phải lỗi màn hình.
- Địa điểm đang chọn: thẻ "About this place" với ảnh + 2 đến 3 dòng `extract` + "From Wikipedia", có Loading/Error riêng.
- Đưa `WeatherRepository` lên `safeApiCall`/`ApiError` (thay `toWeatherError()`), để màn Weather phân biệt được timeout, 400, 404, 429, 5xx, offline; giữ nguyên cache forecast offline (W3).
- File gợi ý: `data/remote/wikipedia/PageSummaryApi.kt`, `PageSummaryResponse.kt`, `data/repository/PlaceInfoRepository.kt`, sửa `PlaceResultAdapter`, `WeatherViewModel`, `WeatherFragment`.
- Test: `PlaceInfoRepositoryTest` (200, 404 = không có ảnh, 500), cập nhật `WeatherViewModelTest` cho `ApiError`.

### Duy Anh: video demo 1 đến 2 phút (sản phẩm nộp số 3)

Đề bài yêu cầu video cho thấy: app lấy dữ liệu từ API, luồng đăng ký/đăng nhập Firebase, và xử lý lỗi khi bật chế độ máy bay.

- Quay **sau khi** cả 4 lát đã merge vào `feature/network-cloud-integration`, bằng bản build có `app/google-services.json`
  (Firebase thật, không phải DevAuth). Pull nhánh tích hợp mới nhất rồi build lại trước khi quay.
- Dùng tài khoản thử (vd `demo3@trippocket.test`), không dùng email/mật khẩu cá nhân. Đừng để lộ mật khẩu trên hình.
- Kịch bản gợi ý (khoảng 90 đến 110 giây):

| # | Cảnh | Thời lượng | Phải thấy gì |
|---|---|---|---|
| 1 | Đăng ký Firebase | 15 giây | Mở app, Register bằng email mới, vào Trip List. |
| 2 | Giữ phiên | 10 giây | Vuốt tắt hẳn app, mở lại: vào thẳng Trip List, không hỏi đăng nhập. |
| 3 | Trip Ideas (MockAPI) | 25 giây | Icon bóng đèn: vòng tải rồi danh sách có ảnh (Glide). Share một ý tưởng (POST), sửa (PUT), xoá (DELETE). |
| 4 | Các lát còn lại | 20 giây | Lướt nhanh: Nearby sights (Bảo), ảnh địa điểm trong Weather (Duy Anh), Exchange rates có cờ (Minh). |
| 5 | Chế độ máy bay | 20 giây | Bật máy bay, mở lại Trip Ideas hoặc kéo làm mới: banner "You're offline", app không văng. Tắt máy bay, bấm Retry: có dữ liệu lại. |
| 6 | Đăng xuất, đăng nhập | 10 giây | Sign out, Login lại bằng tài khoản vừa tạo. |

- Cách quay: emulator Android Studio (nút "..." > Record and playback > Start recording), hoặc
  `adb shell screenrecord /sdcard/demo.mp4` rồi `adb pull`, hoặc trình quay màn hình của điện thoại thật.
  Có thể lồng tiếng tiếng Việt hoặc chèn chữ ngắn tên từng cảnh; không bắt buộc.
- Giữ trong 1 đến 2 phút. Cắt bớt chỗ chờ mạng nếu dài.
- Đăng YouTube chế độ **Unlisted** (Không công khai) hoặc Google Drive chia sẻ "Anyone with the link".
  Mở link bằng tab ẩn danh để chắc chắn ai cũng xem được, rồi gửi link cho Tiến.

## Minh (Expenses): tỷ giá và quy đổi ngoại tệ

- Endpoint (GET, không cần key): `https://open.er-api.com/v6/latest/{base}` (USD, EUR, THB, JPY, KRW, SGD, ...).
  Trả `result`, `base_code`, `time_last_update_utc`, `rates` (có `VND`). Kiểm ngày 09/11: 1 USD = 25.611 VND.
  Mã tiền sai vẫn trả HTTP 200 với `{"result":"error","error-type":"unsupported-code"}`: coi là BAD_RESPONSE.
- Màn "Exchange rates" (mở từ tab Expenses): danh sách các ngoại tệ hay dùng khi du lịch, mỗi dòng có cờ quốc gia
  tải bằng Glide từ `https://flagcdn.com/w80/{country}.png` (vd `us`, `eu`, `th`, `jp`), tên, giá quy ra VND, dòng
  "Updated {time}" từ API. Đủ Loading/Success/Error + kéo để làm mới.
- Form chi tiêu (E2): ô tuỳ chọn "Paid in" (ngoại tệ + số tiền) quy ra VND rồi điền vào số tiền; tiền lưu trong Room vẫn là VND, quy tắc chia đều giữ nguyên.
- File gợi ý: `data/remote/exchangerate/ExchangeRateApi.kt` (từ `exchangeRateRetrofit`), `ExchangeRateResponse.kt`, `data/repository/ExchangeRateRepository.kt`, `ui/expenses/ExchangeRatesFragment.kt` + ViewModel + `CurrencyAdapter.kt` + `item_currency.xml`.
- Test: `ExchangeRateRepositoryTest` (200, `result: error`, 429, 500), test quy đổi làm tròn đúng đồng.

## Phần chung Tiến lo

- Lớp mạng dùng chung (đã xong), lát Trip Ideas trên MockAPI.io với đủ GET/POST/PUT/DELETE (đã xong).
- Firebase Auth email/password + giữ phiên khi mở lại app (A1, đã có trên `develop`).
- Review + merge các PR, PR cuối vào `develop`, worksheet (bảng endpoint, ảnh Firebase Console, WBS). Video demo do Duy Anh quay.

## Hạn (đề xuất, Tiến chốt theo hạn nộp trên LMS)

| Việc | Hạn |
|---|---|
| Mở PR lát của mình | T4 11/11 |
| Sửa review, merge vào nhánh tích hợp | T5 12/11 |
| PR tích hợp -> `develop`, worksheet (Tiến) | T6 13/11 |
| Video demo, gửi link cho Tiến (Duy Anh) | T6 13/11 |

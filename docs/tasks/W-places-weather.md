# W — Saved Places & Weather (Open-Meteo)
Feature: W1–W4 · Giao cho: Bùi Duy Anh · Nhánh: `feature/duyanh-<feature>`
Phụ thuộc: F0 (chặn: bắt đầu phần Retrofit/Room) · Người tích hợp: Tiến · Review lượt đầu: Bảo

Đọc trước: proposal 3.3, 4.1, 4.2 (SavedPlace, WeatherCache), 6.4; `docs/ARCHITECTURE.md`.
Code hiện tại có hai lỗi cần sửa khi chuyển sang dữ liệu thật: địa điểm đang được nhận diện bằng **tên**
(`WeatherViewModel.kt:120`), và `forecast.last()` sẽ crash khi danh sách rỗng (`WeatherViewModel.kt:57`).

## Sở hữu
`ui/weather/*`, `data/remote/openmeteo/*`, `data/repository/WeatherRepository.kt`,
`data/local/dao/{SavedPlaceDao,WeatherCacheDao}.kt`, `data/model/WeatherModels.kt`, `nav_weather.xml`,
`values/strings_weather.xml`, test của module.

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
- `container.geocodingRetrofit` và `container.forecastRetrofit` đã tạo sẵn (OkHttp + Gson, log ở bản debug): chỉ cần
  `retrofit.create(YourApi::class.java)`.
- `WeatherCacheDao.upsert` ghi thẳng, không qua TripWriter (cache không nằm trong backup).

## Làm ngay T6–CN
- DTO geocoding/forecast + mapping, test với JSON mẫu thật.
- Tách **kết quả tìm kiếm** (chưa có id) khỏi **địa điểm đã lưu** (UUID, note).
- `domain/WeatherCodes.kt` (WMO → `WeatherCondition`) và `domain/ForecastMatcher.kt` (ngày ngoài 16 ngày →
  "not available", không lấy thời tiết hôm nay thay vào; danh sách rỗng hoặc thiếu ngày không crash). Có test.
- Thống nhất với Bảo contract thời tiết theo ngày cho Itinerary. CN: viết query DAO; Retrofit chạy thử 2 base URL.

## W1 — Tìm kiếm (T3 03/11)
- Query → `debounce` → `flatMapLatest`, try-catch bên trong; ném lại `CancellationException`. Retry có trigger
  riêng (gán lại cùng query vào StateFlow sẽ **không** phát lại).
- [ ] "da lat" ra kết quả; "xyz" → No places found; mất mạng → lỗi, lần sau vẫn tìm được; Retry cùng query vẫn gọi lại

## W2 — Saved places (T3 03/11)
- Lưu theo khuôn ghi (withTransaction + touch), đổi tên, ghi chú, xoá; tối đa 30; tên ≤ 80, ghi chú ≤ 300.
- [ ] Hai địa điểm trùng tên khác toạ độ vẫn tách biệt; địa điểm thứ 31 bị chặn

## W3 — Forecast + cache (T5 05/11)
- Lưu địa điểm → gọi forecast theo toạ độ → ghi `WeatherCache`; UI đọc từ Room. Forecast lỗi → giữ địa điểm và
  forecast cũ; Retry/pull-to-refresh chỉ tải lại forecast. Hiện "Last updated" và "Weather data by Open-Meteo.com".
  Sau restore thì tải lại forecast (cache không nằm trong backup).
- [ ] Tải → chế độ máy bay → vẫn hiện kèm giờ cập nhật

## W4 — Test (T6 06/11)
`WeatherViewModelTest` với fake repository: đủ các ca của proposal 6.4, cộng thêm: trùng tên, đổi tên, cache rỗng,
retry cùng query.

## Khi xong mỗi feature
PR vào `develop`, kèm bằng chứng. Không sửa ROADMAP/STATUS.

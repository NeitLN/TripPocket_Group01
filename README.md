# TripPocket

Group 01, Mobile Application Programming (261_72ITSE30603), Văn Lang University.

TripPocket is an offline-first Android trip planner: trips, a day-by-day itinerary with reminders, a packing list,
weather for saved places, and shared expenses with balances and suggested payments. Everything is stored in Room
first and works in Airplane Mode; a signed-in account backs its trips up to Firestore and can restore them on a
new install.

**Release:** [`v1.1.1`](https://github.com/NeitLN/TripPocket_Group01/releases/tag/v1.1.1) — signed release APK and
AAB (`CN=TripPocket, OU=VLU, O=Nhom01`). Demo video: https://youtu.be/4aHB0uAgqk4

## Team and work split

Each member owns one module and also built features in the other three (rotation, proposal section 5); every
feature has its own branch `feature/<name>-<ID>` and pull request.

| | Võ Việt Tiến (leader) | Đỗ Quốc Bảo | Bùi Duy Anh | Võ Hoàng Minh |
|---|---|---|---|---|
| Student ID | 2474802010391 | 2474802015084 | 2474802010525 | 2474802010242 |
| Owns | Auth, Trips, Backup & Restore | Itinerary, Checklist, Reminders | Places & Weather | Members, Expenses & Budget |
| Auth / Trips / Backup | A3 backup (#24), A4 restore (#25) | A2 trip CRUD (#18) | A1 login / register (#8, #23) | A5 rules + size tests (#3, #33, #34) |
| Itinerary | I2 checklist (#22, #49) | I1 itinerary (#12), I4 reminders (#20, #29) | I3 drag to reorder (#14) | I5 reminder reconciler (#5) |
| Weather | W5 weather in the itinerary (#27) | W4 weather tests (#19) | W1 search, W3 forecast cache (#9, #16, #28, #38) | W2 saved places (#4) |
| Expenses | E4 budget + share (#26) | E3 balances (#21) | E1 members (#7) | E2 expense + split (#2, #32), members layout (#6), sample-expense order (#31) |
| Network & cloud (M4) | Trip Ideas on MockAPI (#36), network layer (#46) | Nearby sights (#47, #48, #52) | Wikipedia photos, exchange-rate screen (#41, #43, #44, #45) | Exchange-rate rounding (#53) |
| Release | Release-gate fixes and evidence (#50, #51), final fixes and release v1.1.1 (#63, #64) | — | v1.0 release and M4 worksheet (#55, #56), M5 hardening and signed release v1.1.0 (#60, #61) | — |

## Architecture (MVVM)

```
View (Activity / Fragment + ViewBinding)  ->  ViewModel (StateFlow<UiState>, SavedStateHandle)
  ->  Repository (suspend, Dispatchers.IO)  ->  Room DAO  |  Retrofit API  |  Firebase Auth / Firestore
```

```
vn.edu.vlu.trippocket
├── ui            View + ViewModel, one package per feature: login, trips, detail, itinerary, checklist,
│                 weather, expenses, members, ideas, common
├── data
│   ├── local     Room: AppDatabase (thread-safe singleton), entity/, dao/, TripWriter, secure/
│   ├── remote    Retrofit APIs: openmeteo/, wikipedia/, mockapi/, exchangerate/; firebase/ (auth, backup)
│   ├── repository  what the ViewModels call; the only layer that touches DAOs and APIs
│   └── model     UI-facing models
├── domain        pure Kotlin rules (expense split and settlement, reminder time, ordering, limits)
├── di            AppContainer: manual dependency injection
├── work          WorkManager backup job
└── reminder      AlarmManager reminders, boot / time-zone receivers
```

- ViewModels hold no `Context` or View; Fragments observe with `repeatOnLifecycle(STARTED)` and clear their binding
  in `onDestroyView`.
- Every change to a trip's rows goes through `TripWriter` in one Room transaction, so the trip is marked for backup
  exactly once; caps (15 members, 200 activities, 100 checklist items, 30 places, 300 expenses) are checked inside it.
- Network calls return `ApiResult`; every failure becomes one `ApiError` (offline, timeout, rate limited, server,
  bad response...), shown as Loading / Success / Error / Empty states with Retry.

Details: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Data, APIs and cloud

| Part | What |
|---|---|
| Room | 11 tables: `trip`, `trip_member`, `activity`, `checklist_item`, `saved_place`, `expense`, `expense_share`, plus the API caches `weather_cache`, `trip_idea_cache`, `exchange_rate_cache`, `place_info_cache`; schema version 2 with a tested migration from 1 (`app/schemas`, `Migrations.kt`) |
| REST (Retrofit + OkHttp + Gson) | Open-Meteo geocoding and 16-day forecast, Wikipedia GeoSearch and page summary, MockAPI.io Trip Ideas (GET / POST / PUT / DELETE), open.er-api.com exchange rates |
| Firebase | Auth (email / password, session kept), Firestore backup `users/{uid}/trips/{tripId}` with owner-only rules (`firestore.rules`, tested in the emulator) |
| Images | Glide: Trip Idea covers, place photos, country flags, with placeholders |

Endpoint table: [`docs/worksheet.md`](docs/worksheet.md#3-bảng-endpoint).

## Build

Open the folder in Android Studio and run the `app` configuration, or:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

- Without `app/google-services.json` the app builds and runs on an offline stand-in account with sample trips.
  With it, Firebase sign-in and backup are on. The file is shared privately and never committed.
- A signed release needs `keystore.properties` (see `keystore.properties.example` and
  [`docs/m5/keystore.md`](docs/m5/keystore.md)); the keystore itself stays outside the repo.
- 471 unit tests (Robolectric for Room, MockWebServer for the APIs) and 9 instrumented tests on an emulator
  (`connectedDebugAndroidTest`: 6 Espresso tests for the trip list, new-trip validation, rotation, adding an activity
  and an expense, a double tap on Add; 3 Room migration tests from schema 1 to 2). CI runs both plus the Firestore rules tests on every pull request, builds the release APK (R8 +
  resource shrinking, unsigned) and attaches a JaCoCo coverage report (`./gradlew createDebugUnitTestCoverageReport`,
  about 55 % of instructions; repositories, DAOs and the domain rules are above 90 %).

## Documents

- Proposal tasks and status: [`docs/ROADMAP.md`](docs/ROADMAP.md), [`docs/STATUS.md`](docs/STATUS.md)
- Milestone 4 worksheet (APIs, Firebase, Glide): [`docs/worksheet.md`](docs/worksheet.md)
- Milestone 5 report (performance, security, signed release): [`docs/m5/report.pdf`](docs/m5/report.pdf)
- How we branch, test, review and merge: [`CONTRIBUTING.md`](CONTRIBUTING.md)

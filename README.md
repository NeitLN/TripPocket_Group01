# TripPocket

Group 01, Mobile Application Programming (261_72ITSE30603), Văn Lang University.

| Member | Student ID | Screen |
|---|---|---|
| Võ Việt Tiến (leader) | 2474802010391 | Login, Trip List |
| Đỗ Quốc Bảo | 2474802015084 | Itinerary |
| Bùi Duy Anh | 2474802010525 | Weather & Saved Places |
| Võ Hoàng Minh | 2474802010242 | Expenses & Budget |

TripPocket is an offline-first trip planner: itinerary, packing list, weather for saved places and shared expenses.
Milestone 1 covers the five core screens as flat ConstraintLayout XML layouts. The app still runs on mock
data (`data/local/MockData.kt`) so every layout state can be shown; Room, Retrofit and Firebase come in later
milestones. The code follows Model – View – ViewModel (see [Architecture](#architecture-mvvm)).

## Architecture (MVVM)

```
vn.edu.vlu.trippocket
├── data                      Model
│   ├── model                 Trip, TripMember, ItineraryActivity, Expense, Place, ForecastDay, ...
│   ├── local                 MockData: the only static data source (Room DAOs later)
│   └── repository            TripRepository, WeatherRepository: what the ViewModels call
├── domain                    ExpenseCalculator, LoginValidator: pure Kotlin rules, unit-tested
├── ui                        View + ViewModel, one package per screen
│   ├── login                 LoginActivity, LoginViewModel
│   ├── trips                 MainActivity, TripListFragment, TripAdapter, TripListViewModel
│   ├── detail                TripDetailActivity, TripDetailViewModel, TripArgs
│   ├── itinerary             ItineraryFragment, ActivityAdapter, ItineraryViewModel
│   ├── weather               WeatherFragment, PlaceResultAdapter, ForecastAdapter, WeatherViewModel
│   └── expenses              ExpensesFragment, ExpenseAdapters, ExpensesViewModel
└── util                      Format, Insets
```

| Rule | How it is done |
|---|---|
| No `findViewById` | View Binding in every Activity, Fragment and Adapter, including the inflated weather chip (`ViewPlaceChipBinding`). |
| Static data in the ViewModel layer | Only the repositories read `MockData`. Each ViewModel turns it into a `XxxUiState` data class exposed as a `StateFlow`; adapters receive ready-made rows (`TripCard`, `ExpenseRow`, `MemberBalance`, `SettlementRow`, `ForecastRow`). |
| ViewModels hold no View / Activity / Fragment / Context | Text that needs resources is exposed as `@StringRes` ids or raw values and formatted by the View. The trip id comes from `SavedStateHandle` (intent extras / fragment arguments). `ViewModelTest` runs every ViewModel on the plain JVM. |
| Lifecycle-aware observation | Fragments: `viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED)`. Activities: `repeatOnLifecycle` on the activity's lifecycle. |
| No leaked bindings | Fragments inflate in `onCreateView` and set `_binding = null` in `onDestroyView`; adapters are created per view, not kept in fields. |

UI state that must survive rotation (selected day, Expenses/Balances mode, empty-state preview) is kept in
`SavedStateHandle`. MainActivity and TripListFragment share one `TripListViewModel` (`activityViewModels()`)
so the overflow menu no longer looks the fragment up.

## Screens and layout files

| Screen | Layouts |
|---|---|
| 1. Login | `activity_login.xml`, `layout-land/activity_login.xml` |
| 2. Trip List | `activity_main.xml`, `fragment_trip_list.xml`, `item_trip.xml` |
| 3. Itinerary | `activity_trip_detail.xml`, `fragment_itinerary.xml`, `item_activity.xml` |
| 4. Weather & Saved Places | `fragment_weather.xml`, `item_place_result.xml`, `item_forecast_day.xml`, `view_place_chip.xml` |
| 5. Expenses & Budget | `fragment_expenses.xml`, `layout-land/fragment_expenses.xml`, `item_expense.xml`, `item_balance.xml`, `item_settlement.xml`, `item_section_header.xml` |

Resources: `values/dimens.xml` (spacing scale, max widths, Guideline fractions), `values-sw600dp/dimens.xml`
(tablet overrides), `values/strings.xml`, `values/colors.xml` + `themes.xml` (Material 3 color roles),
`values*/integers.xml` (trip grid span 1 / 2 / 3).

## Layout techniques

- One ConstraintLayout per screen. The only nesting is required by a Material component
  (MaterialCardView child, TextInputLayout + EditText, ChipGroup, MaterialButtonToggleGroup).
- Percentage Guidelines whose fractions come from `@dimen`, so phones and tablets share one file.
- Barriers for columns whose width depends on text (sync chip, time, amounts) and for headers that grow with font size.
- Packed and spread_inside chains instead of wrapper LinearLayouts; Groups to toggle empty states.
- "Flat panels": a View with a rounded background drawn behind sibling texts, sized by a Space under a Barrier.
- `layout-land` variants for Login and Expenses; `layout_constraintWidth_max` keeps forms and lists readable on tablets.

## Try the states

- Login: an invalid email or a password without a digit shows TextInputLayout errors. Any valid pair signs in.
- Trip List: overflow menu > "Preview empty state".
- Itinerary: Day 4 has no activities (empty state).
- Weather: type `da lat` (accents optional) for results, `xyz` for "No places found", `offline` for a network error.
  Picking a new city saves it; its first forecast load fails so the Retry row can be seen.
- Expenses: switch to Balances for member balances and suggested payments; the share icon opens the share sheet.

## Build

Open the folder in Android Studio and run the `app` configuration, or:

```bash
./gradlew assembleDebug testDebugUnitTest lintDebug
```

Unit tests: `ExpenseCalculatorTest` (equal split with leftover dong, the 300,000 VND demo from the proposal,
balances sum to zero), `LoginValidatorTest` and `ViewModelTest` (every screen's ViewModel on the plain JVM).

# AGENTS.md

Guidance for AI agents working in this repository.

## Project shape

Food You is a Compose Multiplatform food tracker. Three Gradle modules, both KMP:

| Module       | Type                                              | Holds                                              |
|--------------|---------------------------------------------------|----------------------------------------------------|
| `:core`      | `com.android.kotlin.multiplatform.library`       | domain, persistence, network, DI definitions       |
| `:app`       | KMP library (`api(projects.core)`)               | UI, navigation, feature/capability DI              |
| `:androidApp`| plain Android application                         | manifest, launcher, app wiring                     |

Both KMP modules declare `iosArm64`/`iosSimulatorArm64`, but **`iosMain` is empty** and several
`expect` declarations (`SoftwareCrypto`, `LanguagePreferenceProvider`, `databaseBuilder`) have no
iOS `actual`. iOS targets therefore do not compile today. `kotlin.native.ignoreDisabledTargets=true`
hides this. Do not treat iOS compile failures as your own regression, and do not try to fix them
unless asked.

### Layering convention

Follow what upstream already does — match an existing feature, do not invent a new shape.

- **`:core`** — `domain/` (entities, services), `infrastructure/` (Ktor, Room, DataStore, crypto),
  `module.kt` Koin wiring. `coreModule.kt` aggregates the sub-modules.
- **`:app`** — `features/` (one directory per user-facing feature, each with its own `module.kt`),
  `capabilities/` (shared cross-feature UI), `shared/ui/` (design-system components).
  `featuresModule.kt` and `capabilitiesModule.kt` aggregate them.

**Domain and infrastructure belong in `:core`, UI in `:app`.** A `ViewModel` or `Composable` in
`:core` is a smell. Logic that can live in `core` should, so it stays unit-testable without Compose.

## Conventions worth knowing

**Strict compilation.** `core` and `app` both set `allWarningsAsErrors = true` plus
`-Xreturn-value-checker=check` and `-Xexpect-actual-classes`. An unused import, an unused
parameter, or a discarded return value **fails the build**. This catches things that compile
elsewhere.

**Formatting.** ktfmt 0.64, kotlinlang style. The `justfile` formats only files changed against
`HEAD`:

```
{ git diff --name-only --diff-filter=ACMR HEAD; git diff --name-only --diff-filter=ACMR --cached; } \
  | sort -u | grep -E '\.kts?$' | xargs java -jar $KTFMT_JAR --kotlinlang-style
```

Set `KTFMT_JAR` to a `ktfmt-0.64-with-dependencies.jar` from Maven Central. Formatting is part of
the build contract here — submit unformatted code and CI will differ from your local result.

**Navigation is Navigation 3**, not Navigation Compose. Routes are `@Serializable` objects or data
classes nested in the sealed interface `FoodYouNavHostRoute`, registered in `FoodYouNavDisplay.kt`
via `entry<T> { }`. **Every nested route must also be imported by name** — they are nested
classifiers and are not resolvable by simple name otherwise. This bites silently: the route compiles
as "unresolved reference" even though the declaration is right there in the same file.

**Persistence is event-sourced.** Writes go through a `Decider` that turns commands into events into
a `UserProduct`-style aggregate, persisted in `EventStore.db`. Read models live in separate
databases and are updated by `EventHandler` synchronizers (e.g.
`UserProductSearchSynchronizer` listens for `UserProductEvent` and projects into `ReadModelDatabase`).

The event store is the **source of truth**. When a read model looks wrong, check the event payload
before assuming the write was wrong — the projection can be at fault, and it can be repaired from
the events.

**Unit/value types are branded.** `Weight`, `Volume`, `Energy`, `AbsoluteQuantity`, `WeightUnit`,
`FoodSnapshotId` all validate in their constructors (`require(value.isFinite())`,
`require(value >= 0.0)`). Do not construct them from unvalidated input. Nutrient completeness is
tracked by `NutrientValue.Complete` / `NutrientValue.Incomplete`, and `NutritionFacts` defaults
every field to `Incomplete(null)`.

**Nutrition is normalized per 100 g.** `MeasuredFoodSnapshot` computes
`nutritionFacts * quantity.absoluteWeight.grams / 100.0`. Anything that produces nutrition data must
return per-100 g values and record portion weight separately in `FoodSnapshotQuantity` /
`AbsoluteQuantity`, or the math silently doubles or thirds.

**Secrets.** `local.properties` is gitignored and is where local secrets belong — `app/build.gradle.kts`
already reads `usda.api.key` from it. Never commit an API key. For credentials persisted by the app
itself, follow `FoodDataCentralSettingsRepositoryImpl`: wrap with `SoftwareEncrypted` (AES-256-GCM
via the Android KeyStore) and store as `byteArrayPreferencesKey`.

**Preferences** go through `UserPreferencesRepository`, not a bespoke DataStore. Adding one means
four edits: the `UserPreferences` entity, a `DataStoreProvider` subclass, registration in the
`UserPreferencesRepositoryImpl(...)` vararg in `preferences/module.kt`, and a string resource.
There is exactly **one** DataStore (`PreferenceDataStoreFactory.createWithPath`, `Module.dataStore()`);
never create another with `preferencesDataStore`.

**UI idioms.** `SegmentedListItem` with `ListItemDefaults.segmentedShapes(index, count)` — if you
add a row, bump `count` on every sibling. Shared food rows use `FoodSearchListItem`; use
`WeightFormatter` / `VolumeFormatter` / `EnergyFormatter` / `QuantityFormatter` rather than
hand-rolling number-plus-unit strings. ViewModels are Koin `viewModelOf` + `koinInject` or
`koinViewModel`, never `remember { }`. Collect with `collectAsStateWithLifecycle`. User-facing text
is always `stringResource(Res.string.*)` — never a literal — with a prefix
(`headline_`, `action_`, `error_`, `description_`, `placeholder_`, `label_`, `supporting_`).

**Ktor requests with a body must set `contentType`.** `ContentNegotiation` only selects a
converter when a `Content-Type` is present. Omit it and the request fails at preparation with
`Fail to prepare request body for sending ... with Content-Type: null` — which surfaces as a
*connectivity* error, not a serialization one, and is very hard to trace. See
`openfoodfacts/.../OpenFoodFactsApiV1SearchDataSource.kt` for the correct shape.

## Testing

`:core` has `commonTest` (runs on `testAndroid`); `:app` has `commonTest` + `androidDeviceTest`.

`:core:testAndroid` is the fast signal — **run it before claiming anything works**. The whole
`:app` host-test task is not usable upstream: `app/src/commonTest` declares `expect` symbols whose
actuals live only in `androidDeviceTest`, so `:app:testAndroid` cannot compile. That is pre-existing.

Tests assert behaviour, not implementation. `core/src/commonTest` has no mocking framework, so
prefer real objects and hand-written fakes over mocks. `ktor-client-mock` is available if you need
to exercise a real HTTP client — see `GeminiApiClientRequestTests`.

**A test that never exercises the failing path is worse than no test.** Unit-testing a mapper proves
nothing when the request never left the process, and round-tripping a happy-path fixture hides
lossy projections. Reproduce the bug's exact shape, then confirm the test fails before you fix it.

## Build and verify

The project needs **JDK 21**. Gradle 9.7.1 and AGP 9.4.1 will not run on an older JVM, and
`gradle-daemon-jvm.properties` pins the toolchain. Do not put `org.gradle.java.home` in
`gradle.properties` — it is machine-specific and breaks CI. Use `~/.gradle/gradle.properties` if you
need to point Gradle at a local JDK.

Android SDK: `compileSdk`/`targetSdk` 37, `minSdk` 30, from `gradle/libs.versions.toml`. A missing
SDK level is an environment problem, not a code change.

```
./gradlew :androidApp:assembleDebug :core:testAndroid
```

For a UI change, install and drive a real emulator:

```
$ANDROID_HOME/platform-tools/adb -s emulator-5554 install -r -t app/build/outputs/apk/debug/androidApp-debug.apk
$ANDROID_HOME/platform-tools/adb -s emulator-5554 logcat -c
$ANDROID_HOME/platform-tools/adb -s emulator-5554 logcat -v time <Tag>:D AndroidRuntime:E '*:S'
```

Compilation passing is not verification. A KMP build can compile an entire feature that is
unreachable at runtime — an unsent request, an empty persisted row, a DI graph that resolves but
never produces data. Reproduce on a device before reporting success.

## Debugging notes

- Kermit logs reach logcat under the logger's tag. `LoggingEventNotifier` is installed only in
  debuggable builds, so a release build stays quiet.
- `FoodNameEntity` has **no fallback column**. It derives `fallback` via `.first()` over its
  translation columns, so a row with every column null throws `NoSuchElementException` on read. Any
  code path that produces a `FoodName` with only a fallback must land that name in a real column —
  see `SearchResultMapper.toFoodNameEntity`.
- Third-party model ids are not stable. Google retires dated snapshots (e.g.
  `gemini-2.5-flash`) for new projects while keeping aliases (e.g. `gemini-flash-lite-latest`)
  serving. Prefer an alias, and surface a distinct "model unavailable" error so a retired id does not
  masquerade as a network fault. `listModels` still advertises retired models, so a picker built from
  it can offer ids that fail.
- Diagnose API failures by reproducing the exact request with `curl` before reading application
  logs. It distinguishes key, quota, model and payload problems in one shot, and avoids inferring a
  cause from a message that has been mapped through several layers.
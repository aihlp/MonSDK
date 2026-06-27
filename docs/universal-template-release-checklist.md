# Universal local-template release checklist

Reviewed against the current MonSDK working tree on 2026-06-27.

## Release decision

Status: **not ready for external template handoff yet**.

The reason is not missing backend/security infrastructure. MonSDK is intentionally a local Android template. The remaining blockers are template quality blockers: strict lint, full device validation, more fixture coverage, and final proof that the local AI pipeline and Room persistence remain stable.

## Baseline contract

- The template is local-only.
- Medical records stay on the device in app-private storage.
- The base template has no server, account system, cloud sync, remote medical analytics, or cloud AI.
- Health Connect access is device-side and permission-scoped.
- Local AI is optional and must never block record saving, navigation, analytics, import/export, or reminders.
- The only intended network operation in the base template is model download from the configured registry, with size and SHA-256 verification.

See:

- [local-data-scope.md](local-data-scope.md)
- [no-backend-scope.md](no-backend-scope.md)
- [health-connect-permissions.md](health-connect-permissions.md)

## Evidence collected

- `.\gradlew.bat testDebugUnitTest --console=plain`: passed.
- `.\gradlew.bat assembleDebug --console=plain`: passed.
- GitHub Actions Android CI exists and passed for unit tests and debug APK assembly.
- `lintDebug` is still report-only in CI because translation lint debt remains.
- A diabetes/glucose fixture exists and validates non-blood-pressure config, Health Connect mapping, analytics, and AI prompt wording.

## Blocking issues before handoff

| Severity | Area | Issue | Required result |
| --- | --- | --- | --- |
| P0 | Local persistence | Manual records must never collapse into one record, and app update reinstall must preserve Room data. | Device test proves 10 unique records survive app restart and `adb install -r`. |
| P0 | AI pipeline | AI worker must always finish with success, failure, cancellation, or safe `Unavailable`. | Device test proves prompt creation, worker execution, native runtime handling, JSON rendering, and UI completion without process crash or permanent busy state. |
| P0 | Quality gate | `lintDebug` still has translation errors and CI does not fail on lint. | Fix translations or commit a reviewed lint baseline, then make lint strict in CI. |
| P0 | Template proof | Diabetes/glucose fixture exists, but mood/activity/medication/custom fixture matrix is incomplete. | Add minimal fixtures proving shared code is not blood-pressure-specific. |
| P1 | Architecture | Some shared layers still depend on app-shell resources/classes or hidden active-program fallback. | Program identity flows through DI/worker input; reusable core does not depend on a concrete app shell. |
| P1 | Permissions | Reference manifest must request only permissions required by the selected program. | Permission tests exist per fixture; blood-pressure build does not request glucose permission. |
| P1 | Documentation | Docs must describe the real local AI runtime and local-only data boundary. | README and guides stay consistent with actual code. |
| P1 | CI completeness | CI lacks instrumentation tests, strict lint, and device/emulator persistence checks. | CI or documented release script runs the full local-template validation suite. |

## 1. Code and architecture checklist

### 1.1 Data persistence

- [ ] Manual save returns an explicit success/failure result to UI.
- [ ] Failed save shows an error and does not clear input.
- [ ] Ten manual records with different values create ten unique Room records.
- [ ] History shows ten records after D1.
- [ ] Analytics input count is ten after D1.
- [ ] App process restart preserves records.
- [ ] `adb install -r` update reinstall preserves records.
- [ ] Full uninstall is documented as destructive.
- [ ] Room migrations do not use destructive fallback in production paths.
- [ ] Timestamp/slot logic cannot overwrite manual records from the same time bucket.

### 1.2 Program modularity

- [ ] New verticals are added through program definitions, not by branching shared screens.
- [ ] Shared code does not check for `blood-pressure-monitor` to decide behavior.
- [ ] AI prompt context comes from active program config.
- [ ] Analytics schema comes from active program config.
- [ ] Health Connect mappings come from active program config.
- [ ] Manifest permissions are scoped to the selected app vertical.
- [ ] Program identity is passed to workers explicitly.
- [ ] No hidden global active-program fallback is required for background work.
- [ ] Blood-pressure reference can be replaced by a glucose program without editing core storage, core analytics, or core AI contracts.

### 1.3 Fixture matrix

- [x] Blood-pressure reference program builds.
- [x] Diabetes/glucose fixture validates:
  - [x] non-blood-pressure program ID;
  - [x] glucose metric;
  - [x] `READ_BLOOD_GLUCOSE` mapping in fixture;
  - [x] no blood-pressure metric in fixture prompt;
  - [x] analytics count and dashboard metric generation.
- [ ] Mood fixture validates score/rating inputs and no Health Connect permission by default.
- [ ] Activity fixture validates steps/exercise mappings.
- [ ] Medication-only fixture validates event/status tracking without vital metrics.
- [ ] Custom fixture validates arbitrary metric IDs and labels.

### 1.4 AI pipeline

- [ ] Prompt builder receives local analytics snapshot only.
- [ ] Prompt token/context limits are enforced before native call.
- [ ] Worker is unique per analysis task and replaces stale manual work.
- [ ] Worker publishes terminal status: success, failure, cancelled, or unavailable.
- [ ] Native errors are caught and converted to `Unavailable`.
- [ ] JSON output is validated before persistence.
- [ ] Invalid JSON is rendered as safe unavailable/error UI.
- [ ] Missing model does not start generation.
- [ ] Corrupted model does not crash process.
- [ ] Cancellation releases busy UI state.
- [ ] App background/foreground transition does not orphan the worker.

### 1.5 Local data boundary

- [ ] No code path uploads medical records.
- [ ] No prompt or analytics snapshot is sent to a remote AI service.
- [ ] Model download does not include medical payload.
- [ ] CSV/report export happens only by explicit user action.
- [ ] Logs do not intentionally print full medical record payloads in release builds.
- [ ] Android backup policy is intentionally set for the target product.

## 2. Build and CI checklist

- [ ] Clean clone builds with documented JDK/SDK/NDK/CMake versions.
- [ ] `testDebugUnitTest` passes.
- [ ] `assembleDebug` passes.
- [ ] `lintDebug` passes or approved baseline is enforced.
- [ ] `assembleDebugAndroidTest` passes.
- [ ] `connectedDebugAndroidTest` runs only on dedicated emulator/test device.
- [ ] CI uploads APK, unit test reports, and lint reports.
- [ ] CI fails on new compile/test/lint failures after the baseline is cleaned.
- [ ] Native arm64 build is covered.

## 3. Documentation and onboarding checklist

- [ ] README explains local-only scope.
- [ ] README explains JDK/Android SDK requirements.
- [ ] README explains build/test commands.
- [ ] README links to program creation guide.
- [ ] Program creation guide explains:
  - [ ] adding metrics;
  - [ ] adding form fields;
  - [ ] adding tags;
  - [ ] adding analytics rules;
  - [ ] adding graph series;
  - [ ] adding Health Connect mappings;
  - [ ] scoping manifest permissions;
  - [ ] changing AI wording;
  - [ ] adding fixture tests.
- [ ] Docs state that `llama.cpp` Android adapter is currently bundled.
- [ ] Docs state local AI is optional.
- [ ] Docs warn not to run destructive device test flows on a user-data device.
- [ ] Docs distinguish app update reinstall from full uninstall.

## 4. Final handoff criteria

The template can be handed to another team only when:

- [ ] all P0 blockers above are closed;
- [ ] local persistence checks pass on a real device;
- [ ] AI analysis finishes or returns safe `Unavailable` on a real device;
- [ ] diabetes/glucose fixture passes;
- [ ] at least two more non-blood-pressure fixtures pass;
- [ ] strict CI is green;
- [ ] documentation matches the local-only architecture;
- [ ] known deviations are listed with owner and planned fix.

## Team extension section

Derived app teams may add their own product-specific checks below without changing the local-template baseline:

- [ ] Product-specific clinical wording reviewed.
- [ ] Product-specific localization reviewed.
- [ ] Product-specific Health Connect permissions approved.
- [ ] Product-specific AI model approved.
- [ ] Product-specific store listing reviewed.

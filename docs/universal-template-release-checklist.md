# Universal template release checklist

Reviewed against the current MonSDK working tree on 2026-06-27.

## Current release decision

Status: **not ready for production publication as a universal template**.

The reference Android app builds, unit tests pass, and a debug APK can be installed on Pixel 8 without clearing app data when using `adb install -r`. The repository is still not ready to be handed to external teams as a production-ready universal starter because CI/CD is absent, `lintDebug` fails, security/compliance controls are incomplete, and the template matrix still needs more non-blood-pressure fixtures. A diabetes/glucose fixture now covers the first alternate vertical.

## Evidence collected

- `.\gradlew.bat compileDebugKotlin testDebugUnitTest --rerun-tasks --console=plain`: passed.
- `.\gradlew.bat assembleDebug --console=plain`: passed, including arm64 native build.
- `.\gradlew.bat lintDebug --console=plain`: failed with 300 errors and 136 warnings.
- `adb install -r app\build\outputs\apk\debug\app-debug.apk`: passed on Pixel 8.
- Root repository now has `.github/workflows/android-ci.yml` for unit tests, debug APK assembly, and lint report upload. Lint is report-only until translation debt is fixed.
- No Terraform, CloudFormation, Docker Compose, fastlane, Dependabot/Renovate, SBOM, or vulnerability scanning configuration was found. The no-backend Android scope is documented in `docs/infrastructure-scope.md`.

## Blocking issues before template publication

| Severity | Area | Issue | Required result |
| --- | --- | --- | --- |
| P0 | CI/CD | Root CI exists, but instrumentation tests, strict lint gate, vulnerability scanning, and SBOM are not yet enabled. | Clean clone runs build, unit tests, lint, Android tests, native build, and artifact upload in CI. |
| P0 | Quality gate | `lintDebug` fails: 300 errors, all `MissingTranslation`, plus 136 warnings. | `lintDebug` passes or a reviewed baseline is committed and CI fails only on new issues. |
| P0 | Template validation | A diabetes/glucose fixture exists, but the full matrix for mood, activity, medication, and a custom vertical is not complete. | Minimal alternate fixtures pass config, storage, analytics, AI, permissions, and persistence tests. |
| P0 | Security/compliance | No implemented user authentication/authorization model and no encryption-at-rest layer for Room/DataStore/SharedPreferences. | Security model is documented and implemented or explicitly scoped as local-only with required product-level controls. |
| P1 | Documentation | README and creation guide now describe bundled `llama.cpp`; remaining docs must stay in sync with runtime changes. | README and guides match the actual AI delivery model. |
| P1 | Architecture | `core` still imports app shell resources/classes in several areas. | Reusable core is separated from Android app adapters or boundaries are documented and tested. |
| P1 | Infrastructure | No IaC exists; current Android-only local-first scope is documented in `docs/infrastructure-scope.md`. | Either provide deployment IaC or document that the Android template has no backend infrastructure and list required product-owned services. |
| P1 | Dependency governance | Many dependencies are outdated according to lint; no automated update/security process found. | Dependency update policy, vulnerability scan, and SBOM are present in CI. |
| P1 | Android permissions | Reference manifest no longer requests glucose permission; product-specific permission tests are still required for each derived app. | Product-specific manifest permissions are generated or documented and tested per vertical. |
| P1 | Release hardening | `release` has `minifyEnabled false`; no signing/Play deployment docs. | Release profile, signing instructions, R8 policy, and artifact provenance are documented. |

## 1. Code and architecture checklist

### 1.1 Bug and defect readiness

- [ ] There are no open P0/P1 bugs affecting data persistence, AI worker completion, navigation, background sync, Room migrations, Health Connect import, model download, or report generation.
- [ ] All P2/P3 known bugs are documented in an issue tracker with:
  - [ ] severity;
  - [ ] affected verticals;
  - [ ] user impact;
  - [ ] workaround;
  - [ ] owner;
  - [ ] target release.
- [ ] Regression tests exist for all previously confirmed P0 issues:
  - [ ] ten manual records remain ten unique Room records;
  - [ ] app reinstall via `adb install -r` preserves database;
  - [ ] missing Room migration never triggers destructive fallback;
  - [ ] local AI runtime errors return `Unavailable`, not process abort;
  - [ ] stale WorkManager rows do not permanently disable AI UI;
  - [ ] model download resume and integrity checks work.

Current status: partially complete. Data-regression tests and reinstall preservation were added earlier. Full AI completion with real model still needs controlled end-to-end verification.

### 1.2 Modular template architecture

- [ ] New verticals are added by replacing or adding `program/*` only:
  - [ ] `UniversalProgramDefinition`;
  - [ ] `ProgramUiDefinition`;
  - [ ] `ProgramAnalyticsSchema`;
  - [ ] `ProgramRecordMapper`;
  - [ ] Health Connect mapping;
  - [ ] prompt context;
  - [ ] localization keys;
  - [ ] visuals.
- [ ] Shared layers do not import concrete program packages:
  - [ ] `core/domain`;
  - [ ] `core/storage`;
  - [ ] `core/normalization`;
  - [ ] `core/analytics`;
  - [ ] `core/ai`;
  - [ ] reusable UI components.
- [ ] `core` does not depend on `MainActivity`, app resources, app string providers, or app-specific navigation.
- [ ] Workers receive program identity through DI or worker input, never through a hidden global.
- [ ] AI persistence uses active `programId`, not `blood-pressure-monitor`.
- [ ] Database tables that store program-owned data are either program-scoped or documented as singleton product state.
- [ ] A second fixture program with a different ID and different metrics validates:
  - [ ] glucose tracking;
  - [ ] mood tracking;
  - [ ] activity tracking;
  - [ ] medication-only tracking;
  - [ ] no blood-pressure-specific labels, permissions, metrics, AI prompts, or reports.

Current status: improved, but not complete. AI hardcode was removed from core and a diabetes/glucose fixture exists, but full module separation and more vertical fixtures are still missing.

### 1.3 Code quality, typing, validation

- [ ] `compileDebugKotlin` passes in a clean clone.
- [ ] `testDebugUnitTest` passes in a clean clone.
- [ ] `lintDebug` passes or has a reviewed baseline with zero new violations.
- [ ] Public program config objects are validated by config-audit tests.
- [ ] Input validation covers:
  - [ ] required metrics;
  - [ ] numeric ranges;
  - [ ] timestamps and timezone behavior;
  - [ ] unknown metric IDs;
  - [ ] unsupported event statuses;
  - [ ] malformed import CSV;
  - [ ] corrupted AI JSON;
  - [ ] damaged GGUF model files.
- [ ] Complex code paths have comments explaining invariants:
  - [ ] normalization merge rules;
  - [ ] Room migration strategy;
  - [ ] AI grammar/output contract;
  - [ ] WorkManager uniqueness/cancellation behavior;
  - [ ] native llama.cpp batch/context constraints.
- [ ] Static typing is not bypassed by raw strings except where unavoidable; stringly-typed statuses have tests or typed wrappers.

Current status: compile and unit tests pass. Lint fails and must be fixed or baselined.

### 1.4 Configuration and hardcoding

- [ ] Application identity is documented and replaceable:
  - [ ] `applicationId`;
  - [ ] namespace;
  - [ ] app name;
  - [ ] deep-link scheme;
  - [ ] FileProvider authority;
  - [ ] launcher icons;
  - [ ] package rename plan.
- [ ] Product-specific IDs are not hardcoded in reusable layers:
  - [ ] program ID;
  - [ ] premium product IDs;
  - [ ] Health Connect permissions;
  - [ ] AI model registry;
  - [ ] remote endpoints;
  - [ ] notification channel copy;
  - [ ] privacy/store copy.
- [ ] If the product uses environment-specific values, provide `.env.example` or equivalent Android-friendly config examples:
  - [ ] API base URLs;
  - [ ] feature flags;
  - [ ] billing product IDs;
  - [ ] model registry overrides;
  - [ ] analytics/crash-reporting DSNs;
  - [ ] CI signing placeholders.
- [ ] No secrets are committed.
- [ ] Debug and release configuration are separated.

Current status: no root `.env.example` exists. For Android-only local template this can be acceptable only if docs explicitly say no runtime env file is required and list Gradle/manifest placeholders instead.

## 2. CI/CD and infrastructure checklist

### 2.1 Required CI workflows

- [ ] Root `.github/workflows/ci.yml` exists.
- [ ] CI runs on pull requests and main branches.
- [ ] CI uses a clean checkout, not local machine state.
- [ ] CI installs JDK 17 and Android SDK API 36 or the documented supported versions.
- [ ] CI caches Gradle safely.
- [ ] CI runs:
  - [ ] `./gradlew testDebugUnitTest`;
  - [ ] `./gradlew lintDebug`;
  - [ ] `./gradlew assembleDebug`;
  - [ ] `./gradlew assembleDebugAndroidTest`;
  - [ ] `./gradlew connectedDebugAndroidTest` on a dedicated emulator/device, never a user-data device;
  - [ ] native CMake build for all supported ABIs;
  - [ ] dependency vulnerability scan;
  - [ ] license/SBOM generation.
- [ ] CI uploads:
  - [ ] APK artifacts;
  - [ ] lint HTML/text reports;
  - [ ] unit test reports;
  - [ ] instrumentation test reports;
  - [ ] logcat/tombstone artifacts for device failures.

Current status: not present.

### 2.2 Branch and release pipeline

- [ ] Branch protection requires green CI.
- [ ] Release pipeline is documented.
- [ ] Release signing is configured via secure CI secrets.
- [ ] Versioning strategy is documented.
- [ ] Release artifacts are reproducible from a tag.
- [ ] Debug and release builds use separate application IDs or install channels if needed.
- [ ] Play Store / internal distribution process is documented if used.

Current status: not present.

### 2.3 Infrastructure as code

- [ ] If the template has backend/cloud dependencies, IaC exists for them:
  - [ ] Terraform, CloudFormation, Pulumi, or equivalent;
  - [ ] one-command dev environment deployment;
  - [ ] staging/prod separation;
  - [ ] secrets management;
  - [ ] logging/monitoring;
  - [ ] teardown instructions.
- [ ] If no backend is required, docs explicitly state:
  - [ ] data is local-first;
  - [ ] Health Connect is device-side;
  - [ ] local AI model is downloaded from configured model registry;
  - [ ] any sync/backend/auth layer is product-owned and out of scope.

Current status: no IaC found. This must be either added or explicitly scoped out.

## 3. Documentation and onboarding checklist

### 3.1 README

- [ ] README lists the stack:
  - [ ] Kotlin;
  - [ ] Compose;
  - [ ] Room;
  - [ ] WorkManager;
  - [ ] Hilt;
  - [ ] Health Connect;
  - [ ] local llama.cpp AI runtime;
  - [ ] Gradle/AGP/JDK/Android SDK versions.
- [ ] README has first-run instructions for Windows, macOS/Linux, and Android Studio.
- [ ] README explains native build prerequisites.
- [ ] README explains how to install on a device without clearing app data.
- [ ] README warns that `connectedDebugAndroidTest` must run only on test devices/emulators.
- [ ] README includes troubleshooting for:
  - [ ] SDK/NDK/CMake not found;
  - [ ] Gradle compatibility flags;
  - [ ] lint translation errors;
  - [ ] Health Connect permission failures;
  - [ ] WorkManager foreground service permission issues;
  - [ ] model download interruption;
  - [ ] AI unavailable state.

Current status: README is minimal and not enough for external teams.

### 3.2 Customization guide

- [ ] Guide documents how to add a new tracker:
  - [ ] glucose;
  - [ ] mood;
  - [ ] activity;
  - [ ] medication;
  - [ ] other disease-specific verticals.
- [ ] Guide includes step-by-step examples for:
  - [ ] defining metrics and units;
  - [ ] defining record form fields;
  - [ ] defining tags/dimensions;
  - [ ] adding graph series;
  - [ ] adding analytics rules;
  - [ ] adding Health Connect mappings;
  - [ ] changing permissions;
  - [ ] changing AI prompt context;
  - [ ] changing localization;
  - [ ] adding tests.
- [ ] Guide clearly marks files normally edited and files that must remain untouched.
- [ ] Guide is consistent with actual local AI runtime packaging.

Current status: guide exists but is incomplete and currently stale regarding bundled `llama.cpp`.

### 3.3 API, database, and internal service docs

- [ ] Room schema is exported and versioned.
- [ ] Migration policy is documented.
- [ ] DAO/repository responsibilities are documented.
- [ ] Ingestion contract is documented:
  - [ ] raw event shape;
  - [ ] normalization rules;
  - [ ] source links;
  - [ ] manual vs synced records;
  - [ ] deletion behavior.
- [ ] Analytics contract is documented:
  - [ ] metric roles;
  - [ ] rule types;
  - [ ] findings;
  - [ ] dashboard inputs.
- [ ] AI contract is documented:
  - [ ] model registry;
  - [ ] download/resume/integrity;
  - [ ] prompt limits;
  - [ ] JSON grammar;
  - [ ] unavailable/error states;
  - [ ] worker lifecycle;
  - [ ] local privacy implications.

Current status: partial docs exist; not enough for handoff without core-team support.

## 4. Security and compliance checklist

### 4.1 Security controls

- [ ] Threat model exists for local health data.
- [ ] Authentication/authorization model is implemented or explicitly scoped to OS-level device access.
- [ ] Sensitive data storage is protected:
  - [ ] Room encryption or documented product decision;
  - [ ] DataStore/SharedPreferences encryption or documented product decision;
  - [ ] AI model/output privacy treatment;
  - [ ] backups disabled or controlled.
- [ ] Manifest security is reviewed:
  - [ ] exported activities are intentional;
  - [ ] deep links are safe;
  - [ ] receivers are not overexposed;
  - [ ] FileProvider authority is product-specific;
  - [ ] unnecessary permissions removed.
- [ ] Network security config is present if any remote endpoints are used.
- [ ] Model downloads are integrity checked by size and SHA-256.
- [ ] Logs do not contain PHI/PII in production builds.
- [ ] Crash reports, if added by products, are scrubbed.

Current status: backups are disabled and model integrity exists, but auth, encryption-at-rest, log policy, and compliance docs are not complete.

### 4.2 Dependencies and vulnerability management

- [ ] Dependency versions are centralized.
- [ ] Dependency update automation exists.
- [ ] Vulnerability scanning runs in CI.
- [ ] License scanning/SBOM generation exists.
- [ ] Vendored `llama.cpp` revision is pinned and documented.
- [ ] Native dependency update process is documented.
- [ ] All known high/critical vulnerabilities are remediated or risk-accepted.

Current status: no automated vulnerability or license process found. Lint reports multiple outdated dependencies.

### 4.3 Medical data compliance

- [ ] Compliance statement explains what the template does and does not provide.
- [ ] HIPAA checklist exists for US deployments:
  - [ ] BAA requirements for cloud vendors;
  - [ ] access controls;
  - [ ] audit logs;
  - [ ] encryption;
  - [ ] breach handling;
  - [ ] retention/deletion policy.
- [ ] GDPR checklist exists for EU/UK deployments:
  - [ ] lawful basis;
  - [ ] consent flows;
  - [ ] data subject rights;
  - [ ] export/delete;
  - [ ] data minimization;
  - [ ] DPIA;
  - [ ] processor list.
- [ ] Region-specific requirements are documented for intended markets.
- [ ] Medical disclaimer and clinical safety boundaries are reviewed by qualified stakeholders.
- [ ] AI output is clearly non-diagnostic and cannot block core data recording.

Current status: not complete. Existing AI safety prompt is not a compliance program.

## 5. Final transfer criteria

The template can be handed to another team only when all conditions below are met:

- [ ] P0 blockers are closed.
- [ ] P1 blockers are either closed or explicitly accepted with documented impact and mitigation.
- [ ] `testDebugUnitTest` passes in clean clone.
- [ ] `lintDebug` passes or baseline is approved.
- [ ] `assembleDebug` passes in clean clone.
- [ ] `assembleDebugAndroidTest` passes in clean clone.
- [ ] `connectedDebugAndroidTest` passes on a dedicated emulator/device.
- [ ] Native arm64 build passes.
- [ ] Pixel 8 smoke test is documented:
  - [ ] install via `adb install -r`;
  - [ ] records survive reinstall;
  - [ ] ten manual records persist and feed analytics;
  - [ ] history/statistics show correct counts;
  - [ ] reminder scheduling works;
  - [ ] import/export works;
  - [ ] model download/resume works;
  - [ ] AI analysis finishes with valid result or safe `Unavailable`;
  - [ ] logcat has no crash/SIGABRT.
- [ ] A new project was created from the template and validated without changing base infrastructure:
  - [ ] glucose fixture;
  - [ ] mood fixture;
  - [ ] activity fixture;
  - [ ] medication fixture;
  - [ ] one custom vertical fixture.
- [ ] README and guides match the code.
- [ ] CI/CD is green on protected branches.
- [ ] Security/compliance checklist is completed for the intended region.
- [ ] Known deviations are documented in an issue tracker.

## Team extension section

Product teams should add their own checks below:

- [ ] Product-specific clinical review completed.
- [ ] Product-specific localization reviewed.
- [ ] Product-specific Health Connect permissions approved.
- [ ] Product-specific AI model approved.
- [ ] Product-specific privacy policy approved.
- [ ] Product-specific release signing configured.
- [ ] Product-specific store listing reviewed.

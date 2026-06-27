# MonSDK

MonSDK is an Android template for program-driven health monitoring applications. It ships a blood-pressure program only as a reference implementation; new products must replace it through the program module and application identity configuration.

## What is reusable

- structured records, normalization, local Room storage, import/export and reminders;
- configurable record, history, statistics and graph UI;
- program-level metrics, actions, tags, analytics, Health Connect mappings and visual configuration;
- local AI infrastructure, including the Android `llama.cpp` adapter, guarded behind model settings and runtime availability.

The contract for reusable UI and program modules is in [docs/ui-program-architecture-tz.md](docs/ui-program-architecture-tz.md). The practical conversion guide is in [docs/creating-new-monitoring-app.md](docs/creating-new-monitoring-app.md). Release gates are tracked in [docs/universal-template-release-checklist.md](docs/universal-template-release-checklist.md).

## Quick start

Requirements: JDK 17 and Android SDK API 36.

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

## Create a product from the template

1. Change `rootProject.name`, `applicationId`, Android namespace, label, launcher assets, deep-link scheme and FileProvider authority.
2. Create a program package under `app/src/main/java/com/medmonitoring/program/` and implement `ProgramModuleDefinition`.
3. Bind that module in `app/src/main/java/com/medmonitoring/app/di/ProgramModule.kt`.
4. Scope the manifest permissions and Health Connect mappings to the product.
5. Update localized strings, AI wording, premium product IDs, privacy copy and store metadata.
6. Complete [docs/new-app-checklist.md](docs/new-app-checklist.md), then run unit tests and a device installation.

For health-data products, also review [docs/health-connect-permissions.md](docs/health-connect-permissions.md), [docs/local-data-scope.md](docs/local-data-scope.md), and [docs/no-backend-scope.md](docs/no-backend-scope.md).

The `com.medmonitoring` package name is intentionally retained in the template to keep the reference build working. Rename it mechanically only after the new program configuration and tests are stable.

## Project layout

```text
app/src/main/java/com/medmonitoring/
  app/       Android entry points, DI and Compose screens
  core/      reusable domain, storage, analytics, ingestion and UI primitives
  program/   replaceable product/reference program modules
```

## Current template status

The reference program builds and unit tests pass. Before treating a release as production-ready, complete the gates in [docs/universal-template-release-checklist.md](docs/universal-template-release-checklist.md). The current template includes a diabetes/glucose test fixture proving a second non-blood-pressure program can pass config, analytics, Health Connect mapping, and AI prompt checks.

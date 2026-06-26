# Template readiness review

Reviewed 2026-06-26 against the current `MonSDK` working tree.

## Verified baseline

- `./gradlew.bat testDebugUnitTest` passes.
- Program configuration has coverage through `ConfigCoverageTest` and package-boundary checks through `TemplateBoundaryTest`.
- The project produces a reference application, not a neutral empty starter. This is acceptable only if the blood-pressure module remains explicitly documented as a replaceable reference program.

## Blockers before independent publication

1. **Make `MonSDK` its own Git repository.** The enclosing repository is `C:\Archive\Health Apps`; it tracks sibling projects and currently sees `MonSDK` as an untracked directory. Do not commit it together with unrelated `MedMonitoring` changes. Initialise or extract a repository at `MonSDK`, add a remote, then make the initial template commit there.
2. **Remove program IDs from common AI code.** `AiConversationContract.DEFAULT_PROGRAM_ID` is `blood-pressure-monitor` and is used by `AiAnalysisUseCase` and `AiChatRepository`. Inject or explicitly pass `UniversalProgramDefinition.programId` at each persistence boundary. Add a test using a non-blood-pressure program ID.
3. **Remove the global active-program service locator.** `AiBackgroundAnalysisRunner` reads `ActiveProgramModuleProvider`, which is configured in `MedApplication` with `BloodPressureProgramModule`. Pass a program module through a worker input/DI entry point instead, so a product has one composition root (`ProgramModule`).
4. **Separate reusable core from Android app shell.** `core` imports `app` resources, `MainActivity`, app localization helpers and `AndroidStringProvider`. Extract Android presentation adapters (notifications, navigation and strings) to an app-facing layer or split Gradle modules. A reusable core must not import the product shell.
5. **Replace AGP-9 compatibility flags.** `gradle.properties` enables deprecated flags including `android.newDsl=false` and `android.builtInKotlin=false`. Identify the dependencies still using legacy variants, upgrade or replace them, then remove the flags before Gradle 10 migration.

## Required follow-up tests

- A second, minimal program fixture with a different ID, no medication action, and a different Health Connect mapping.
- AI persistence tests proving goals and reports use the injected program ID.
- A manifest/permission test proving a product can ship without blood-pressure permissions.
- A build test for the template after changing application identity (application ID, authority and scheme).

## Delivery gate

Publish only when the blockers are closed, `testDebugUnitTest`, `assembleDebug` and `lintDebug` pass in a clean clone, and a device smoke test verifies record creation, reminders, import/export and the selected integrations.

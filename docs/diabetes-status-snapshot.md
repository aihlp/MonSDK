# Diabetes Program Status Snapshot

Snapshot date: 2026-06-28

This document captures the current diabetes/glucose program state before continuing work on the mood program.

## Backup

A filesystem backup was created at:

```text
backup/diabetes-status-20260628-182047
```

Contents:

- `working-tree.diff` - binary-capable diff for tracked file changes.
- `git-status.txt` - repository status at snapshot time.
- `git-log.txt` - recent commit history.
- `untracked-files.txt` - untracked files present at snapshot time.
- `untracked-files.zip` - archive of untracked files.
- `diabetes-docs-selected.zip` - selected diabetes/docs files for fast reference.

## Active Program

`ProgramModule` currently provides:

```kotlin
MoodProgramModule
```

The diabetes program is present in the tree but is not the active injected program at this snapshot.

## Diabetes Program Files

- `app/src/main/java/com/medmonitoring/program/diabetes/DiabetesDefinitions.kt`
- `app/src/main/java/com/medmonitoring/program/diabetes/DiabetesProgramModule.kt`
- `app/src/test/java/com/medmonitoring/DiabetesProgramFixtureTest.kt`

## Current Diabetes Scope

Implemented or partially implemented:

- Glucose as the primary metric.
- Weight as an additional key metric.
- Height as a profile/stable input for BMI.
- BMI as a computed metric from weight and height.
- Diabetes-specific context tags:
  - healthy/useful factors;
  - unhealthy/harmful factors;
  - meal context;
  - symptoms;
  - other medications;
  - custom tags.
- Diabetes-specific AI prompt roles and onboarding questions.
- Health Connect mappings for blood glucose and weight, with activity/sleep/exercise as context mappings.

Known open issues:

- Metric unit preferences are not fully propagated across graph, history, analytics findings, and AI context.
- AI onboarding, recommendation lifecycle, accepted/rejected goals, and patient-context prompt handling still need review.
- Program creation documentation was unclear about boundaries between core, settings/config, and content/localization layers.

## Verification

Last known project-level build check after UI chat fixes:

```powershell
.\gradlew.bat :app:assembleDebug
```

Result: successful.

Before resuming diabetes work, run:

```powershell
.\gradlew.bat test
.\gradlew.bat :app:assembleDebug
```

Then switch `ProgramModule` back to `DiabetesProgramModule` only for diabetes validation.

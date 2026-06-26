# Multi-Program Launch Implementation Plan

This is the active implementation checklist for preparing MonSDK to launch additional program-driven apps.

## Goal

Prepare MonSDK for multiple future apps without breaking the current blood pressure app.

Core principle:

- one canonical `UserRecord`;
- program data through generic `measurements`, `events`, and `dimensions`;
- shared UI driven by program definitions;
- analytics finds relationships from existing records instead of requiring parallel domain entities.

## Phase 1: Shared UI Must Stop Depending On Pressure/Medication Fields

- [ ] Rename or alias shared widget concepts away from medication-specific names.
- [ ] Keep legacy fields only as compatibility projections.
- [x] Ensure shared form/history rendering does not require legacy tag arrays, `systolic`, `diastolic`, or `pulse`.
- [x] Preserve current blood pressure behavior.

Acceptance:

- record screen works from program `recordBlocks`;
- no shared UI component requires medication-specific fields;
- BP app behavior remains the same.

## Phase 2: One Reusable Record Form

- [x] Extract reusable `ProgramRecordForm`.
- [x] Use it from main record screen.
- [x] Use it from edit bottom sheet.
- [x] Centralize page-level spacing in form/screen container.
- [x] Keep blocks responsible only for their own content.

Acceptance:

- create and edit use the same block order and widgets;
- edit sheet no longer duplicates event/metric/tag rendering logic;
- tag groups and input blocks do not overlap due to local layout decisions.

## Phase 3: History Cards Use Canonical UserRecord Data

- [x] Replace legacy `tagsForGroup()` reads with `dimensions`.
- [x] Keep current display behavior: one row per configured manual group, auto/context tags after manual groups.
- [x] Remove magic metric filtering such as `id != "dose"`.
- [x] Build event subtitle from `eventInputs` and canonical `events`.

Acceptance:

- BP history keeps the same visual result;
- future groups do not disappear;
- mood can show mood/energy and emotion groups;
- glucose can show glucose and food/activity context.

## Phase 4: Graph Contract Audit/Fixes

- [ ] Audit existing `GraphDefinition` fields.
- [ ] Ensure legend labels/colors come from graph contract.
- [ ] Ensure event markers come from graph contract.
- [ ] Ensure marker size, line thickness, safe zones, and value label flags are applied.
- [ ] Keep date spacing, collision avoidance, scroll, and density as core renderer behavior.

Acceptance:

- no graph code checks for medication;
- event marker can represent meal, cream, workout, medicine, or any configured event.

## Phase 5: Analytics Reads Existing Records Correctly

- [ ] Ensure analytics reads `measurements`, `events`, `dimensions`, `timestamp`, `source`, and `quality`.
- [ ] Keep generic rules for metric average, metric by dimension, metric by event/status, frequent factor, and calendar/time pattern.
- [ ] Avoid raw-count-only conclusions where sampling bias can mislead.
- [ ] Findings must include basis/confidence.

Acceptance:

- findings can represent examples such as morning mango lowering glucose, summer increasing BP, or end-of-month mood drops;
- no parallel event entity is required.

## Phase 6: Localization Without New Fallback Debt

- [ ] Preserve existing 17-language behavior.
- [ ] Stop adding new program-specific fallback cases to shared `localize()`.
- [ ] New program text must use keys/fallbacks from program config.
- [ ] Analytics findings should use structured keys and arguments.
- [ ] Add or update localization audit tests.

Acceptance:

- BP strings still work;
- new programs do not require editing shared fallback maps for every tag or metric.

## Phase 7: Proof Program Configs

- [ ] Add mood proof config or test fixture: mood 1-10, energy 1-10, positive/negative emotions.
- [ ] Add glucose proof config or test fixture: decimal glucose, food/activity context.
- [ ] Use proof configs to smoke test shared UI/contracts.

Acceptance:

- shared UI compiles and renders without BP-specific edits;
- any required special case becomes a contract fix.

## Phase 8: Regression

Run after implementation phases:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Current status:

- [x] `testDebugUnitTest` passed after Phase 2/3.
- [x] `assembleDebug` passed after Phase 2/3.
- [ ] Pixel 8 Russian/dark screenshot regression after Phase 2/3.

Device validation:

- Pixel 8;
- Russian locale;
- dark theme;
- screenshots for record, edit sheet, graph, history, statistics, and settings.

## Current Implementation Order

1. Define program-owned record slots (morning/day/evening/night for the BP program).
2. Merge raw automatic samples into one `UserRecord` per program/date/slot.
3. Preserve manual measurements, events, note, and edits; automation fills only missing values and context.
4. Regress source updates/deletions and source links without producing duplicate slot records.
5. Test analytics on representative multi-program records; findings must include a basis and confidence.
6. Verify analytics plus local AI end-to-end: runtime, model download, structured response, and fallback.
7. Harden the shared renderer for the primary accessibility mode: light theme and large system font.
8. Add mood and glucose proof configurations only to expose missing platform contracts.
9. Run Pixel 8 regression; apply local cosmetic fixes last.

## Locale Rule

The app follows the Android system locale unless the user explicitly chooses an app language in Android system settings. Platform code must not set a program locale or force a default locale at startup.

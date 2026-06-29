# Mood Program: Cycle Context Module Task

## Goal

Add an optional, isolated cycle context module for programs where hormonal-cycle correlation is relevant. The module must be disabled by default unless the user explicitly enables it in settings. It must not affect male users or users who do not want cycle tracking.

## Scope

- Core module: `core/cycle`
- Settings toggle: per program and per user profile
- Output: normalized system dimensions, not primary metrics
- Consumers: analytics, graph annotations, AI prompt context, export
- Initial target program: `mood-energy-monitor`

## Data Model

Create a small domain model independent from mood records:

- `CycleSettings(enabled: Boolean, sourcePriority: List<CycleSource>)`
- `CycleProfile(lastPeriodStart: LocalDate?, averageCycleLengthDays: Int, averagePeriodLengthDays: Int)`
- `CycleDayContext(day: Int?, phase: CyclePhase?, confidence: CycleConfidence, source: CycleSource)`
- `CyclePhase`: `MENSTRUATION`, `FOLLICULAR`, `OVULATION`, `LUTEAL`, `UNKNOWN`
- `CycleSource`: `MANUAL`, `HEALTH_CONNECT`, `INFERRED`

The module emits dimensions:

- group: `cycle_context`
- tags: `cycle.day.N`, `cycle.phase.menstruation`, `cycle.phase.follicular`, `cycle.phase.ovulation`, `cycle.phase.luteal`, `cycle.phase.unknown`

## Integration Points

1. `RecordSlotResolver` or a new `SystemContextEnricher` asks `CycleContextProvider` for the record timestamp.
2. If enabled, append `RecordDimension(group = "cycle_context", key = ..., label = ...)`.
3. Analytics treats `cycle_context` as `DimensionRole.System`.
4. AI prompt builder receives cycle context only when the user enabled it.
5. Export includes emitted dimensions exactly like other tags.

## Health Connect

Investigate current Android Health Connect support for menstruation/cycle records before implementation. If available in the target SDK:

- Add a new `HealthConnectRecordType` only after confirming API availability.
- Map period starts and cycle events into `CycleProfile` or direct `CycleDayContext`.
- Keep Health Connect reads behind the existing Health Connect settings and permission flow.
- If permissions are missing, keep manual/inferred cycle context working.

## Privacy Requirements

- Default: disabled.
- Explicit setting copy must explain that cycle context can be sensitive.
- No cycle tags are generated, exported, shown to AI, or synced while disabled.
- Deleting/disabling cycle tracking should remove stored cycle profile data, but not historical mood records unless the user chooses a data cleanup action.

## Developer Task

1. Add `core/cycle` interfaces and pure unit tests for phase/day calculation.
2. Add settings UI toggle and optional manual cycle profile editor.
3. Add a system-context enrichment step that appends cycle dimensions.
4. Add analytics schema support for `cycle_context` in mood only.
5. Add Health Connect support only after API verification.
6. Add regression tests proving diabetes and blood pressure records are unchanged when cycle tracking is disabled.

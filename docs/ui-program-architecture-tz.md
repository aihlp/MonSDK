# UI Program Architecture Technical Specification

This document defines how MonSDK screens, widgets, cards, tags, graphs, localization, and visual settings must be split between reusable core and program modules.

The goal is not to make every pixel configurable. The goal is to make future apps such as blood pressure, glucose, mood, activity, pain, skincare, or supplements possible without adding program-specific branches to shared screens.

## 1. Terms

### Program

A program is one monitoring product configuration. Examples:

- blood pressure
- glucose
- mood
- activity
- pain
- topical treatment / skincare

A program owns medical or domain meaning. Core owns rendering, persistence contracts, analytics primitives, and reusable interaction patterns.

### Product Goal

The product goal is to identify factors that influence the user's process and main indicators over time.

Examples:

- glucose drops after eating mango in the morning.
- blood pressure is higher in summer.
- mood declines near the end of the month.
- energy changes after poor sleep or intense activity.

The app is not only a diary and not only a chart. It must preserve enough structured context to discover relationships between:

- measured indicators;
- active actions;
- user-selected tags;
- automatic context dimensions;
- calendar/time dimensions;
- sensor/Health Connect dimensions;
- photo/CV-derived dimensions where a program module supports them.

### UserRecord

`UserRecord` is the canonical record entity.

Core fields:

- `measurements`: measured numeric values.
- `events`: active user actions such as medicine, supplement, food, cream, workout, mood episode, sleep event.
- `dimensions`: tags/context used for display and analytics.
- `quality`, `source`, `timestamp`, `note`.

Legacy fields may exist only as compatibility projections. New screens must read and write canonical fields first.

### Widget

A widget is a reusable interaction or display role, not a medical entity.

Correct widget naming:

- `EventStatusInputWidget`: choose status for a configured event.
- `EventTextInputWidget`: edit event name/amount/unit when the event supports it.
- `MetricInputWidget`: capture one or more configured measurements.
- `TagSelectionWidget`: select display tags.
- `GraphWidget`: visualize configured metric series and event markers.
- `HistoryWidget`: show saved records through a configured card contract.
- `AnalyticsSummaryWidget`: show configured metrics from analytics output.
- `AnalyticsFindingsWidget`: show analytics findings.
- `NoteWidget`: free text.
- `DateTimeWidget`: record timestamp.
- `SaveActionWidget`: save current draft.

Incorrect widget naming:

- `MedicationStatusWidget`
- `DoseWidget`
- `BloodPressureWidget`
- `PulseWidget`

Those names describe one program, not reusable roles.

## 2. Core vs Program Responsibility

### Core Owns

- screen renderer
- layout grid and page spacing
- reusable widget behavior
- graph renderer
- history card renderer
- generic record editing
- canonical persistence path
- generic analytics primitives
- localization lookup mechanism
- theme token application
- accessibility behavior
- responsive behavior

### Program Module Owns

- metrics to measure
- input block order and grouping
- event inputs and statuses
- user-visible tag groups
- analytics dimensions
- graph series and event marker definitions
- normal/safe ranges
- analytics rules and thresholds
- localized string keys and default fallback text
- visual roles where the domain needs them
- Health Connect / sensor mapping
- reminder types

### Program Module Must Not Own

- arbitrary padding inside shared screens
- Compose layout branches in core
- hardcoded screen implementation
- hardcoded graph renderer behavior
- shared card typography
- shared chip implementation
- storage schema shape

## 3. Localization Contract

Everything user-visible must be localizable. This does not mean every text belongs in global `LocalizationUtils`.

Each program definition must provide stable keys for:

- program name
- metric labels and units when needed
- event labels
- event status labels
- tag group titles
- tag labels
- graph series labels
- graph empty state
- reminder type labels
- analytics rule/finding labels
- onboarding/help copy

Core may provide shared strings:

- Save
- Cancel
- OK
- Settings
- History
- Note
- Add
- Delete
- Edit
- Other
- More
- Loading

Program-specific fallback maps in shared app UI are not acceptable for new programs. `LocalizationUtils.localize()` currently contains blood-pressure-era labels and must be replaced with key-based localization plus program-provided fallback text.

## 4. Screen Architecture

### App Shell

Core role:

- app bar
- tabs / destinations
- settings sheet entry
- theme provider
- premium / AI destination access

Program role:

- app title key
- icon resource / visual role
- tab visibility if the program disables a destination
- product-specific empty states

Current issue:

- shell is partly reusable, but `MainActivity` contains shared widgets, local spacing constants, legacy labels, and rendering logic in one file.

Target:

- `MonitoringAppShell`
- `ProgramRecordScreen`
- `ProgramStatisticsScreen`
- `ProgramSettingsScreen`
- widgets moved into reusable files by role.

### Record Screen

Core role:

- receives `ProgramUiDefinition.recordBlocks`
- creates unified vertical layout
- owns spacing between blocks
- renders each block through a registry
- handles save FAB placement and bottom inset
- passes `RecordInputState` and callbacks to blocks

Program role:

- block order
- block config IDs
- metrics mapped to input blocks
- event input IDs mapped to event blocks
- visible tag groups
- save preview pattern

Rules:

- individual blocks must not create page-level spacing.
- blocks may have internal spacing only inside their own content.
- blocks must not know sibling blocks.
- a block must not assume it is first or last.
- page bottom spacer must come from visual config / scaffold inset, not hardcoded `96.dp`.

Current issues:

- `RecordScreen` is in `MainActivity`.
- `AppSpacing` duplicates `ProgramVisualConfig.spacing`.
- `EventStatusBlock` has local top padding and uppercase rules.
- `TagGroupsBlock` renders every program tag group as a user-facing group.
- save preview uses metric map and string replacement but has no structured preview tokens for events.

### Edit Record Sheet

Core role:

- edit an existing `UserRecord` through the same block renderer as record creation.
- initialize draft from canonical measurements/events/dimensions.
- preserve source/quality metadata.

Program role:

- same block order as record screen, or optional `editBlocks`.

Rules:

- create and edit must not drift.
- edit must not manually reimplement event + metric + tag rendering.
- edit must not select tags from legacy arrays first.

Current issue:

- `EditRecordSheet` manually renders events, only two metric block types, all tag groups, note, save.

Target:

- one renderer used by create and edit:

```text
ProgramRecordForm(
  mode = Create | Edit,
  blocks = program.ui.recordBlocks,
  draft = RecordInputState,
  contract = ProgramFormContract
)
```

## 5. Widget Specifications

### DateTime Widget

Core:

- display selected timestamp.
- open date/time pickers.
- format according to locale.

Program:

- optional label key.
- optional visibility/order.

Must not:

- hardcode one date pattern when locale should decide.

### Event Status Widget

Purpose:

- capture active action status. The action may be medicine, supplement, food, cream, workout, mood episode, sleep, exposure, or any configured event.

Core:

- render event label.
- render statuses from `EventInputDefinition.statuses`.
- update `RecordInputState.eventStatuses[eventKey]`.
- open event text editor when event has name/amount/unit fields.
- support 0, 1, 2, or more statuses without layout overlap.

Program:

- event key.
- label key.
- default name.
- default amount/unit.
- statuses, status labels, status semantic roles.

Primary action policy:

- Core must support zero, one, or many active actions.
- A program may define one default action for convenience, but this must not be a core assumption.
- Multiple actions can be equal. Example: glucose may track meal, insulin, exercise, and symptoms; mood may track sleep, conflict, alcohol, cycle, or therapy; skin may track cream, cleanser, diet, and CV observations.
- Analytics rules decide which actions matter for a specific finding. There is no universal "primary action" in core.

Required contract changes:

- `EventStatusDefinition.positive` is too narrow. Replace with semantic role:

```text
Normal | Warning | Critical | Neutral | Success | Skipped | Custom(roleKey)
```

- `WidgetType.MedicationStatusWidget` must become `EventStatusWidget`.
- Event text parsing must be explicit, not regex guessing only. Program must define whether the event supports:
  - name
  - amount
  - unit
  - free text

MVP input rule:

- A combined editable text field is acceptable only as a temporary convenience for one simple action.
- The canonical draft must store action name, amount, unit, status, and free text as structured fields.
- The UI can present a compact combined line, but parsing must not be the only source of truth.

### Metric Input Widget

Purpose:

- capture numeric measurement values.

Core:

- render one metric, paired metrics, or N metrics through layout modes.
- validate input range.
- support integer and decimal values.
- show unit.
- show normal/caution/danger state if ranges exist.

Program:

- metric ID.
- label key.
- unit key.
- input range.
- default value.
- value type.
- normal/caution ranges.
- preferred input style.

Required contract changes:

- support decimal metrics for glucose/weight.
- pair widget should be generic `MetricGroupInputWidget`, not special paired vertical wheel.
- `MetricType` enum should not contain `BLOOD_PRESSURE`, `PULSE`, `MEDICATION_ADHERENCE` in core. Use metric keys and roles.
- support unit systems without forcing a settings screen for every program.

Unit handling:

- Glucose can be entered as mmol/L, for example `4.6`, or mg/dL, for example `130`.
- Both are valid depending on country and user habit.
- The app should infer the user's preferred input unit from the first/manual values when safe.
- Internally analytics may normalize to a canonical unit when needed, but UI should preserve and display the user's chosen unit.
- Dynamic graph ranges mean the renderer does not need a fixed country-specific axis by default.
- Normal ranges must be unit-aware when a program uses units with conversion.

### Tag Selection Widget

Purpose:

- user-facing tag selection for record context.

Core:

- render configured visible tag groups.
- support custom/other tag creation.
- update dimensions.
- display selected tags consistently.

Program:

- display tag groups.
- tag keys/labels.
- whether custom tags are allowed.
- color role.
- icon role if needed.

Important architecture split:

There must be two tag/dimension group contracts:

1. `DisplayTagGroupDefinition`
   - shown to the user.
   - used on record screen and history cards.
   - optimized for usability and visual grouping.

2. `AnalyticsDimensionDefinition`
   - used by analytics rules.
   - may include hidden/system/context dimensions.
   - may include normalized or generated dimensions not shown as chips.

Why:

- not every analytics dimension should be user-facing.
- not every display tag should be analytically meaningful.
- context tags from sensors/calendar are useful for analysis but can overwhelm history cards.
- history cards need compact display groups.

Current issue:

- `tagGroups` tries to serve record input, history display, and analytics at once.
- `UserRecord.tagsForGroup()` maps only fixed legacy groups.

Target:

```text
program.displayTagGroups
program.analyticsDimensions
program.historyCard.visibleDimensionGroups
```

Mood example:

- Metrics:
  - `mood`: 1-10
  - `energy`: 1-10
- Display groups:
  - positive emotions, for example calm, joyful, motivated, grateful.
  - negative emotions, for example anxious, sad, irritated, exhausted.
- Core should not know that these are "positive" or "negative" except through configured semantic roles.
- Analytics can correlate mood/energy with emotions, sleep, calendar period, food, activity, weather, or cycle dimensions.
- History cards can show emotion groups on separate rows, while auto tags follow after them as compact context chips.

History display rule:

- Current visual behavior is correct as a direction: each user-facing group gets its own row; automatic context tags are displayed after manual groups as a compact sequence.
- This display rule belongs to history card/layout configuration, not analytics.
- Analytics can use a broader or different set of dimensions than history displays.

### Note Widget

Core:

- multiline text input.
- update draft note.

Program:

- optional placeholder key.
- optional max length.
- optional visibility.

### Save Action Widget

Core:

- save draft.
- validation.
- visual placement.
- bottom inset handling.

Program:

- preview tokens.
- fallback label key.
- required fields.

Required contract changes:

- `previewPattern` should be structured tokens, not raw string replacement only.
- preview must support event tokens and metric tokens.

## 6. History Architecture

History has two separate concerns:

1. record card display.
2. analytics grouping.

They must not share one tag-group list by accident.

### History Card

Core:

- display a compact summary of a `UserRecord`.
- support menu actions: edit, duplicate, delete.
- render configured metric slots.
- render configured event summary.
- render configured display dimensions.
- apply severity outline if configured.

Program:

- metric slots and priority.
- visible dimension groups.
- event summary rules.
- card severity source.
- max visible tag rows.
- subtitle format.

Required contract:

```text
HistoryCardDefinition(
  metricSlots: List<HistoryMetricSlot>,
  eventSummary: EventSummaryDefinition,
  visibleDimensionGroups: List<String>,
  maxVisibleRows: Int,
  severity: HistorySeverityDefinition
)
```

Current issues:

- metric row assumes vital-sign card with fixed gaps.
- hides `dose` by ID.
- subtitle includes event status/name/amount as raw values.
- tags come through legacy arrays.

### Analytics Grouping

Core:

- analytics reads canonical dimensions.
- rules reference `AnalyticsDimensionDefinition`.
- hidden dimensions are allowed.
- hidden dimensions can be shown in debug/support detail views.

Program:

- which dimensions matter for analysis.
- grouping rules.
- thresholds.

Current issue:

- display tag groups and analytics tag groups are effectively coupled.

Debug/support rule:

- Hidden analytics dimensions should not clutter normal history cards.
- They should be available in a debug/details surface for support, QA, and model explanation.
- This is especially important for auto tags and future CV-derived dimensions.

## 7. Graph Architecture

Graph has three layers:

1. Data contract.
2. Visual contract.
3. Universal chart layout behavior.

These must not be confused.

### Graph Data Contract

Program-owned:

- metric series.
- event marker series.
- safe zones.
- point overlay metric.
- normal ranges.
- labels.
- colors as semantic/data colors.

Examples:

- glucose app: glucose line, meal event markers, hypo/hyper safe zones.
- mood app: mood score, sleep duration, medication/supplement markers.
- activity app: steps bars, workout events.

### Graph Visual Contract

Program-owned when it expresses domain meaning:

- series color.
- event marker symbol.
- safe zone color.
- line thickness if the product visual style requires it.
- whether value labels are shown.
- whether legend is shown.
- point marker style if domain-specific.
- marker symbol/icon choice.

Core-owned defaults:

- minimum touch target.
- clipping avoidance.
- accessibility contrast.
- responsive fallback behavior.

### Graph Universal Layout Behavior

Core-owned:

- axis label collision handling.
- date tick spacing.
- chart viewport calculation.
- scroll/zoom behavior.
- min/max density for readable dates.
- edge padding.
- measuring text height.
- avoiding overlap between markers and axis labels.

These are not "program data". They are renderer quality rules shared by all apps.

But they still must be configurable through renderer-level style tokens when needed:

- chart height.
- x-label line count.
- x-label formatter style.
- min record slot width.
- max visible labels.
- event lane count.
- event lane height.

Current issues:

- legend rendering is partially hardcoded.
- point overlay uses `HeartShape` in core.
- chart height and point size are hardcoded.
- `eventMarkerSizeDp` and `eventLaneGapDp` exist but are not fully applied.
- event markers are appended into x-axis labels, which makes date spacing and event marker display interfere.

Target:

- event markers should render in a dedicated event lane, not inside date label text.
- legend should render from `GraphSeriesDefinition` and `GraphEventMarkerDefinition`.
- point shape should be in graph visual contract:

```text
Circle | Square | Diamond | Heart | Icon(resourceKey) | None
```

Marker symbol rule:

- Support both text symbols and vector/resource icons.
- Text symbols are acceptable for fast program configuration and simple MVPs.
- Vector/resource icons are preferred for production where accessibility, consistency, localization, and platform rendering matter.
- The graph marker contract should allow:

```text
GraphMarkerGlyph.Text(symbol)
GraphMarkerGlyph.Icon(resourceName)
GraphMarkerGlyph.Shape(shapeRole)
```

- Every marker must also have a localized label for accessibility and legend display.

- x-axis layout should be renderer-controlled:

```text
GraphLayoutConfig(
  chartHeightDp,
  minSlotWidthDp,
  axisLabelMaxLines,
  dateFormatStyle,
  eventLaneHeightDp,
  eventLanePlacement
)
```

## 8. Statistics Screen

### Summary Metrics

Core:

- horizontal list/grid renderer.
- responsive metric tile size.
- role-based formatting.

Program:

- metric order.
- label keys.
- unit keys.
- role.
- thresholds for severity when metric is a percent/count.

Current issue:

- card width is estimated from string length.
- percent severity assumes higher is better.

Target:

- `StatisticMetric` needs desired direction / severity mapping.

### Findings Cards

Core:

- render finding cards.
- support severity.
- support evidence chips.
- support paging.

Program:

- rule labels and localized text.
- thresholds.

Current issue:

- localized messages parse English fallback text in places.
- card height is estimated from character counts.

Target:

- findings must carry structured interpolation fields, not parse message strings.

## 9. Settings Screen

Core:

- settings section renderer.
- reminders.
- export/import.
- AI settings.
- premium.
- sensor/Health Connect settings.

Program:

- enabled sections.
- reminder types.
- export/import availability.
- Health Connect mappings.
- AI wording.
- premium product IDs.

Current issue:

- settings is closer to reusable after reminder refactor, but AI/premium copy still has hardcoded English product text.
- section layout uses local hardcoded padding.

## 10. AI Screen

Core:

- chat UI.
- goal/checklist renderer.
- reminder draft editor.
- onboarding/menu actions.

Program:

- AI prompt context.
- allowed actions.
- action labels.
- reminder defaults.
- risk wording.

Current issues:

- hardcoded widths, padding, colors.
- some strings are global product text.
- reminder type now uses program config, but visual layout is separate from the rest of the app.

## 11. Visual System

### Keep In Program Visual Config

- color scheme.
- semantic palettes.
- tag palettes.
- typography scale.
- shape scale.
- spacing scale.
- component style defaults.

### Keep In Core Renderer

- responsive adaptation.
- preventing overlap.
- min sizes.
- accessibility.
- date/tick collision strategy.
- readable density.

### Current Issue

There are two spacing systems:

- `ProgramVisualConfig.spacing`
- `AppSpacing`

Target:

- remove `AppSpacing`.
- provide `LocalProgramSpacing`.
- all screens and widgets use one spacing source.

## 12. Required Refactor Plan

### Phase 1: Rename and Separate Contracts

- Rename `MedicationStatusWidget` to `EventStatusWidget`.
- Replace core `MedicationStatus` usage in UI with generic event status strings + semantic roles.
- Mark medication legacy fields as storage/report compatibility only.
- Split `tagGroups` into display groups and analytics dimensions.
- Add `HistoryCardDefinition`.
- Add support for multiple equal active actions.

### Phase 2: Unified Form Renderer

- Create `ProgramRecordForm`.
- Use it for create and edit.
- Move widget renderers out of `MainActivity`.
- Remove page-level spacing from individual blocks.
- Replace `AppSpacing` with program spacing.

### Phase 3: History Renderer

- Implement metric slot layout.
- Use canonical dimensions.
- Use display dimension groups, not analytics groups.
- Remove `id != "dose"` special case.

### Phase 4: Graph Renderer

- Move event markers out of x-axis labels.
- Apply all graph visual contract fields.
- Add graph layout config.
- Add point shape contract.
- Fix fixed-range semantics.

### Phase 5: Localization Cleanup

- Remove program-specific fallback cases from shared `LocalizationUtils`.
- Require all program labels to have keys or explicit fallback text.
- Add config audit for missing keys.

### Phase 6: Regression Matrix

Each reusable screen must be checked with at least four sample program configs:

- blood pressure: paired metrics + event + many display tags.
- glucose: decimal metric + meal/insulin events + safe zones.
- mood: score metric + mood tags + hidden analytics dimensions.
- activity: step/workout metrics + event markers + no medicine wording.
- skin: photo/CV module boundary, treatment actions, visual progress metrics.

Device checks:

- Pixel 8
- Russian locale
- dark theme
- small and large font sizes
- no-record empty state
- 1 record
- many records
- long localized labels

## 13. Acceptance Criteria

- A new program can be created by adding one program module and resources.
- Shared screens do not branch on blood pressure, medication, pulse, systolic, diastolic, glucose, mood, or activity.
- Record create and edit use the same renderer.
- History cards use canonical measurements/events/dimensions.
- Analytics uses analytics dimensions, not display-only tag grouping.
- Graph legend, data series, event markers, safe zones, colors, labels, and point styles come from graph contracts.
- Graph date spacing and collision avoidance are core renderer behavior.
- All user-facing text is localized through keys or shared core strings.
- Russian/dark/Pixel 8 screenshots pass for each sample program.
- Programs can define several active actions without one being treated as globally primary.
- Unit inference works for metrics such as glucose where multiple country-specific units are valid.
- Hidden analytics dimensions are excluded from normal history cards but visible in debug/support details.

## 14. Program Examples

### Mood Program

Purpose:

- track mood dynamics and energy dynamics;
- identify factors that influence emotional state and user progress.

Metrics:

- `mood`, range 1-10.
- `energy`, range 1-10.

Display tag groups:

- positive emotions.
- negative emotions.

Optional event/actions:

- sleep quality entry.
- alcohol/caffeine.
- exercise.
- conflict/stress event.
- therapy/meditation.

Analytics dimensions:

- manual emotion tags.
- calendar period: day of week, month segment, season.
- time of day.
- sleep/recovery context.
- activity context.
- custom user factors.

History:

- show mood and energy as primary metric slots.
- show positive and negative emotion groups on separate rows.
- show automatic context tags after manual groups.

Analytics examples:

- mood decline near end of month.
- energy lower after poor sleep.
- mood higher after walking.
- negative emotions cluster on weekdays.

### Glucose Program

Purpose:

- track glucose dynamics and discover food/activity/timing effects.

Metrics:

- `glucose`, decimal.

Unit behavior:

- user may enter mmol/L (`4.6`) or mg/dL (`130`).
- infer unit from entered values where possible.
- preserve display unit.
- normalize internally only when analytics/range comparison requires it.

Events/actions:

- meal.
- insulin.
- exercise.
- medication/supplement if configured.

Analytics examples:

- glucose drops after mango in the morning.
- glucose spikes after late meals.
- exercise lowers post-meal glucose.

### Skin Program

Purpose:

- track visible skin progress and factors that influence it.

Important boundary:

- skin analysis should be a separate CV/photo module, not a basic tag-only program.
- photos, lesion/acne/spot counts, region mapping, and visual change comparison require dedicated data contracts.

Core reusable pieces still apply:

- actions such as cream, cleanser, supplement, food.
- display tags such as irritation, dryness, redness.
- graph/history/analytics primitives.

CV module responsibilities:

- photo capture/import.
- consistent region/lighting guidance.
- acne/spot/redness estimates.
- before/after comparison.
- visual progress metrics.
- confidence/quality metadata.

Analytics examples:

- fewer spots after treatment routine.
- irritation increases after a cream.
- redness changes with weather/season.

## 15. Closed Questions / Decisions

1. A program can have multiple equal active actions. Core must not assume one primary action.
2. Active action amount/unit must be structured in canonical state. Combined text is only a compact MVP presentation.
3. Hidden analytics dimensions should be visible in debug/support details, not normal cards.
4. History display groups should have their own order/limits. They usually reference display tag groups, but analytics dimensions are separate.
5. Graph markers should support both text symbols and vector/resource icons.

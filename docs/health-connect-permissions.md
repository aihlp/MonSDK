# Health Connect permissions by product vertical

Health Connect permissions must match the active product vertical. Do not ship a broad manifest that requests every health permission supported by the template.

## Reference blood-pressure app

The reference app may request:

- `android.permission.health.READ_BLOOD_PRESSURE`
- `android.permission.health.READ_HEART_RATE`
- optional context permissions if enabled by the product:
  - `READ_STEPS`
  - `READ_EXERCISE`
  - `READ_SLEEP`
  - `READ_WEIGHT`

It must not request `READ_BLOOD_GLUCOSE`.

## Diabetes / glucose app

A glucose product should request:

- `android.permission.health.READ_BLOOD_GLUCOSE`

It should add only the context permissions it actually uses, for example steps or exercise if those are part of the glucose analytics design.

## Mood, activity, medication, and other verticals

- Mood tracking usually does not need Health Connect permissions unless it uses sleep/activity context.
- Activity tracking should request only activity-related permissions such as steps/exercise.
- Medication-only tracking may not need Health Connect permissions.
- Any new vertical must add a manifest test proving unnecessary permissions are absent.

## Required handoff test

For every derived app:

- [ ] list required Health Connect mappings in the program definition;
- [ ] list matching manifest permissions;
- [ ] run a test that rejects unrelated permissions;
- [ ] verify Android permission rationale text matches the selected permissions.

# Creating A New Monitoring App

This guide describes the normal edit surface for turning MonSDK into a new monitoring app.

## 1. Rename App Identity

Edit:
- `settings.gradle`: project name.
- `app/build.gradle`: `applicationId`.
- `app/src/main/res/values/strings.xml`: `app_name`.
- `app/src/main/AndroidManifest.xml`: deep-link scheme and `FileProvider` authorities.

Keep Kotlin package renames as a separate mechanical change. Do it once, after tests pass with the new program config.

## 2. Change Branding

Edit:
- launcher icons in `app/src/main/res/mipmap-*` and `drawable`.
- `ProgramVisualConfig` in the active program definition.
- store/privacy copy outside the template source tree.

Do not hardcode colors directly in shared screens. Put reusable theme values in program visual config.

## 3. Define The Program

Use `app/src/main/java/com/medmonitoring/program/bloodpressure` as the reference. For a new vertical, create a sibling package such as:

```text
app/src/main/java/com/medmonitoring/program/glucose
```

Define:
- `UniversalProgramDefinition`
- `ProgramUiDefinition`
- `ProgramAnalyticsSchema`
- `ProgramRecordMapper`

Then update `app/src/main/java/com/medmonitoring/app/di/ProgramModule.kt` to provide the new definitions and mapper.

## 4. Add Metrics And Input Fields

Declare metrics in `metricComponents`, `recordSchema`, and `recordScreenBlocks`.

Use existing `WidgetType` renderers when possible. If a new widget is genuinely required:
- add the renderer to common UI;
- register it in `ConfigRegistry`;
- add or update the raw-event mapper;
- add a config audit test.

Core record/history/statistics screens should not check for a specific program id.

## 5. Add Analytics Rules

Add analytics metrics, tag groups, actions, and rules to the program analytics schema.

Rules must match renderer support in `ConfigRegistry.analyticsRuleRenderers`. Tests should validate the generic contract and place vertical-specific expected behavior in reference-program tests.

## 6. Add Graphs And Statistics

Update `graphDefinition`:
- metrics listed in `metrics`;
- `series` that reference declared metrics;
- safe zones and empty state text;
- graph labels with localization keys.

Run config tests after every graph change.

## 7. Configure Health Connect And Sensors

Edit `integrations` in the program definition.

Health Connect mappings are optional and program scoped. Only request Android permissions in `AndroidManifest.xml` for the vertical being shipped.

## 8. Configure AI Wording And Runtime

Edit prompt context through program config and localized strings. Keep AI storage, chat, checklist, and worker infrastructure in core.

The current template bundles the Android `llama.cpp` adapter and builds an arm64-v8a native runtime. Local AI is still optional at runtime: products may keep it disabled, ship a curated model registry, or remove the native runtime intentionally if they do not want local generation.

When keeping local AI:
- keep model download integrity checks enabled;
- keep prompts compact enough for phone inference;
- verify grammar-constrained JSON output;
- run a device smoke test where AI finishes with either a valid result or safe `Unavailable`.

## 9. Update Localization

Every key used by the program must be listed in `ProgramLocalizationConfig.translatableStringKeys` and exist in the base language resource.

Translate only the target locales for the product. Remove locales the product will not maintain.

## 10. Run Tests

Run:

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

For device validation:

```powershell
.\gradlew.bat installDebug
```

Run `connectedDebugAndroidTest` only on a dedicated emulator or test device. That Gradle task may uninstall/clear the target app as part of the test APK lifecycle and must not be used on a device containing user data.

## 11. Configure Permissions Per Vertical

Do not ship every Health Connect permission in the base manifest. Match manifest permissions to the selected program mappings.

Examples:
- blood pressure: `READ_BLOOD_PRESSURE`, optionally heart rate and selected context permissions;
- diabetes/glucose: `READ_BLOOD_GLUCOSE`;
- activity: steps/exercise only;
- mood: usually no Health Connect permission unless sleep/activity context is part of the product;
- medication-only: usually no Health Connect permission.

See [health-connect-permissions.md](health-connect-permissions.md).

## 12. Security, Compliance, And Infrastructure Scope

Before release, complete:
- [security-compliance-scope.md](security-compliance-scope.md)
- [infrastructure-scope.md](infrastructure-scope.md)

## Files Normally Edited For A New App

- `settings.gradle`
- `app/build.gradle`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/values*/strings.xml`
- `app/src/main/res/drawable*`
- `app/src/main/res/mipmap*`
- `app/src/main/java/com/medmonitoring/app/di/ProgramModule.kt`
- `app/src/main/java/com/medmonitoring/program/<your-program>/...`
- tests under `app/src/test/java/com/medmonitoring`

## Files Normally Not Edited

- shared screens in `app/src/main/java/com/medmonitoring/app`
- `core/storage`
- `core/reports`
- `core/reminders`
- `core/premium`
- `core/ui/components`
- common analytics engine code
- database migrations, except for intentional schema changes

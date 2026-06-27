# Local data scope

MonSDK is a local Android application template.

The base template does not include accounts, backend storage, server sync, clinician dashboards, remote analytics, or cloud AI. Medical records created by the user are stored on the device in the app sandbox.

## Data boundary

- Manual records are stored locally in Room.
- App preferences and model state are stored locally.
- Health Connect is accessed only through Android device permissions.
- Local AI uses an on-device native runtime.
- Medical records are not uploaded to a server by the template.
- There is no backend infrastructure to deploy for the base template.

## Network use in the base template

The only intended network path in the template is model download from the configured model registry. Downloaded model files are checked by expected size and SHA-256 before use.

Model download must not upload medical records, analytics snapshots, prompts, or AI results.

## Required template checks

- [ ] Manifest requests only permissions needed by the selected program.
- [ ] No medical records are sent through network clients.
- [ ] Local AI prompt construction uses only local analytics snapshots.
- [ ] Exported CSV/report files are created only through explicit user action.
- [ ] Android backup policy is intentional for the product.
- [ ] Device reinstall/update via `adb install -r` preserves Room data.
- [ ] Full uninstall is documented as destructive because Android removes the app sandbox.

## Out of scope

The template does not implement server-side controls because it has no server.

If a derived product later adds sync, accounts, cloud AI, crash reporting with health payloads, or any other off-device data transfer, that product must design and test that separate system. It is not part of the MonSDK local-template baseline.

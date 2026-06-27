# No-backend scope

MonSDK is not a backend project. It is an Android local-template project.

There is no backend or cloud deployment layer in the base template. This is intentional.

## What the template must provide

- Android build and test pipeline.
- Local Room persistence.
- Local normalization and analytics.
- Local reminders and notifications.
- Device-side Health Connect integration.
- Optional on-device AI runtime.
- Model download with resume and integrity validation.
- Program fixtures proving the template works beyond blood pressure.

## What the template must not require

- Server accounts.
- Remote medical record storage.
- Remote prompt processing.
- Cloud dashboards.
- Backend deployment scripts.
- Infrastructure-as-code for services that do not exist.

## If a derived app adds a backend

That is a different product architecture. The derived app owns its own backend design, deployment, data-transfer policy, and tests. The base MonSDK template must remain usable without any server.

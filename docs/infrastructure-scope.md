# Infrastructure scope

MonSDK currently ships as an Android local-first template and does not require backend infrastructure for the reference build.

## No bundled backend

The template does not include Terraform, CloudFormation, Pulumi, server runtime, database-as-a-service, object storage, or account/auth backend. Therefore there is no one-command cloud deployment in the base project.

This is intentional for the current template scope:

- records are stored locally in Room;
- Health Connect integration is device-side;
- reminders are local Android alarms/notifications;
- local AI uses on-device `llama.cpp`;
- model download uses the model registry URLs configured in the app.

## When a product adds cloud services

If a derived product adds sync, user accounts, clinician dashboards, remote analytics, crash reporting with PHI, or cloud AI, that product must add its own infrastructure package:

- [ ] IaC for dev/staging/prod;
- [ ] secrets management;
- [ ] IAM/access policies;
- [ ] audit logging;
- [ ] backup/restore;
- [ ] monitoring and alerting;
- [ ] data retention/deletion jobs;
- [ ] HIPAA/GDPR vendor controls where applicable.

## CI expectation

Even without backend IaC, the Android template repository must still provide CI for:

- Kotlin/Android build;
- unit tests;
- lint;
- instrumentation tests on a dedicated emulator/device;
- native arm64 build;
- dependency and license scanning.

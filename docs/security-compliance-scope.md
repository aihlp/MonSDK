# Security and compliance scope

MonSDK is a local-first Android application template. It is not, by itself, a certified HIPAA/GDPR compliance package and it does not provide a backend identity, audit, or cloud data platform.

## Implemented in the template

- Android app backup is disabled in the reference manifest.
- Health data is stored locally in Room and app-private files.
- Local AI model files are verified by expected byte count and SHA-256 before use.
- Local AI is optional and must return safe unavailable states instead of blocking record creation.
- Health Connect access remains under Android permission control and can be revoked by the user.

## Product-owned controls required before release

Every product created from this template must explicitly decide and document:

- authentication and authorization model;
- whether the app is single-user local-only or connected to a backend account;
- encryption-at-rest strategy for Room, DataStore, SharedPreferences, exported CSV files, AI reports, and model outputs;
- retention and deletion policy;
- log and crash-report PHI/PII scrubbing;
- consent and privacy policy text;
- incident response and support workflow;
- clinical review and medical disclaimer scope.

## HIPAA checklist for US deployments

- [ ] Identify whether the product is a covered entity/business associate workflow.
- [ ] Execute BAAs with every cloud/vendor touching PHI.
- [ ] Implement user identity, access control, and session policy when data leaves the device.
- [ ] Encrypt PHI at rest and in transit.
- [ ] Maintain audit logs for backend access.
- [ ] Define breach notification process.
- [ ] Define data retention and deletion policy.
- [ ] Review AI output safety and non-diagnostic wording.

## GDPR checklist for EU/UK deployments

- [ ] Define lawful basis and explicit consent where required.
- [ ] Provide privacy notice and processor list.
- [ ] Support data export and deletion.
- [ ] Minimize collected health data to the selected vertical.
- [ ] Complete DPIA for health data processing.
- [ ] Document cross-border transfer mechanism if any backend is used.
- [ ] Ensure analytics/crash reporting do not collect PHI without consent.

## Dependency and supply-chain requirements

Before publishing a product:

- [ ] run dependency vulnerability scanning in CI;
- [ ] generate an SBOM;
- [ ] review licenses for Android and native dependencies;
- [ ] pin and document the vendored `llama.cpp` revision;
- [ ] define the native runtime update process;
- [ ] fail CI on high/critical unaccepted vulnerabilities.

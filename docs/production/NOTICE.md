# OmniFile OSS / NOTICE Register Entry Point

This file establishes the scaffold's notice-management entry point. It is not
the final release notice bundle. Before any distributable release, the full
resolved dependency graph must be reviewed for license and notice obligations,
and the resulting transitive register must be checked against the exact
release artifact.

## Direct declarations in Production Scaffold V1

- `androidx.core:core-ktx:1.17.0`
- `androidx.activity:activity-compose:1.12.3`
- Compose libraries governed by `androidx.compose:compose-bom:2025.12.00`
- `androidx.compose.material3:material3:1.4.0`
- `androidx.compose.material3.adaptive:adaptive:1.2.0`
- `junit:junit:4.13.2` for unit tests
- `androidx.test.ext:junit:1.3.0` for instrumentation tests
- `androidx.test:runner:1.7.0` for instrumentation tests

The exact resolved graph is represented by `app/gradle.lockfile`. Artifact
checksums are represented by `gradle/verification-metadata.xml`. Those files
are the inputs for the later license/notice inventory; they are not a license
waiver.

## Release gate

Before a release configuration or production signing is introduced, add a
complete transitive OSS register with component versions, licenses, required
copyright notices, source-offer obligations where applicable, and the exact
artifact/report provenance. Keep this scaffold entry point and update the
register in the same controlled change.

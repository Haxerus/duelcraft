# Upstream Engine Update Implementation Plan

**Goal:** Update the pinned upstream engine and restore Deck tribute triggers with current Mitsurugi Ritual scripts.

**Architecture:** Advance the ygopro-core gitlink without local engine patches. Keep the JNI and Java protocol unchanged unless the upstream comparison requires an adaptation. Add a regression that exercises the new Lua argument even with older local EDOPro scripts.

**Scope:** The approved upstream dependency update; preserve unrelated card-cache and documentation edits. Existing diagnosis and red/green experiment are in build/kusanagi-diagnosis/README.md.

- [x] Compare upstream C API, message producers, query serialization, Lua revision and build inputs against 7471af3c.
- [x] Add a native Deck-tribute regression using ReleaseRitualMaterial(group, true), and confirm failure on the old engine.
- [x] Advance the submodule to 122e0d091a0f399221a4510cc98a406ae485905f, regenerate VS projects if needed, rebuild both DLLs.
- [x] Run the focused regression, the exact Prism snapshot reproduction, and the full Gradle build/test suite.
- [x] Update the engine wiki for the new snapshot, including unconditional QUERY_IS_PUBLIC and changed ShuffleSetCard ordering; verify source fingerprints and links.
- [x] Review the final diff and report the built artifact and verification results.

Verification: full build passed (595 tests, zero failures/skips); exact Prism snapshot/Habakiri regression passed without a script overlay; independent review found no actionable issues. Packaged native DLL hashes match the rebuilt resources. Core wiki fingerprints and links pass; the global wiki checker still reports the preexisting host-documentation failures recorded in source-versions.md.

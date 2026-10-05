# MT269 refactor baseline

The only binary behavior baseline for this lane is **MT269-v1.1-hotfix3.apk**.

- SHA-256: `18a73c868c29d53acdff0298ccc264f9fb97f6473eeddcfc2291bf0410006ea9`
- package: `bin.mt.plus.canary`
- version: `2.26.9-clone (26091198)`
- targetSdk: `30`
- architecture: `arm64-v8a`

Later hotfix4–8 and the previous refactor-alpha1 are explicitly not used as binary bases.

## Runtime refactor boundaries

The refactor isolates second-packager compatibility code behind two runtime bridges:

1. `PanelBridge`
   - latest-request-wins semantics for each pane/controller;
   - stale success/error callbacks are ignored;
   - failed refreshes keep the last good list instead of clearing the pane;
   - ZIP/APK rows use a stable secondary ordering, preserving MT's primary sort choice.

2. `RootBridge`
   - the 600 ms ServerSocket SO_TIMEOUT is only a poll quantum;
   - a poll timeout is no longer surfaced as a fatal handshake failure;
   - the owner path uses a separate bounded overall deadline.

The baseline APK itself is not committed to this public repository. APK builds are produced from the user-supplied baseline and then statically audited against its unchanged-entry hash set.

# MT269 refactor lane

This branch is an isolated reconstruction lane for MT269 2.26.9-clone on Android 17.

## Behaviour baseline

Known-good UI/file-panel baseline: **MT269-v1.1-hotfix3.apk**

- package: `bin.mt.plus.canary`
- version: `2.26.9-clone (26091198)`
- targetSdk: **30**
- hotfix3 SHA-256: `18a73c868c29d53acdff0298ccc264f9fb97f6473eeddcfc2291bf0410006ea9`

Hotfix3 is treated as immutable behaviour reference. Later hotfix6/7/8 and refactor-alpha1 are not used as architectural bases.

## Refactor boundaries

1. **Root bridge**
   - Never use one 600 ms deadline for both helper startup and socket acceptance.
   - Connection setup is a state machine with a bounded overall deadline, retry/fallback semantics, cancellation and explicit failure causes.
   - A socket poll timeout is an internal scheduling event, not a user-facing fatal error by itself.

2. **File panels**
   - Left and right panes have independent generations.
   - An async load may commit only when its generation still matches that pane.
   - No shared global "latest load" token.
   - No background task may mutate the list currently owned by the UI.

3. **Sorting**
   - MT's selected sort mode remains authoritative.
   - APK-special priority is a secondary policy, not a replacement for time/size/type ordering.
   - Comparator equality must preserve existing stable order.

4. **Compatibility**
   - Keep targetSdk 30.
   - Preserve the second-packaged feature layer unless a specific injected path is proven broken.
   - Do not remove seccomp/native sandbox globally.

## CI contract

Every commit on this branch must pass:
- pure-Java state-machine regression tests;
- per-pane generation/race tests;
- sorting invariants;
- patch-plan lint;
- toolchain availability checks.

The actual APK is not committed to this repository. Binary output is rebuilt from the user's supplied baseline and separately audited against the baseline fingerprint manifest.

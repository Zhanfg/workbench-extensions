# Hotfix 3 refactor patch map

Hotfix 3 remains the immutable behavioral baseline.

## ROOT bridge

- classes3.dex: Ll/۫۠ܽ;->ۨ(Z)Ll/᩷֨ܽ;
- classes4.dex: Ll/᩶۠ܽ;->run()V (F99O)
- legacy behavior: a single 600 ms ServerSocket poll and one 600 ms join;
- the first SocketTimeoutException is promoted to a fatal IOException.

Refactor contract:

- 250 ms poll slices;
- up to 40 slices before the accept deadline is exhausted;
- 2 s LocalSocket window before TCP fallback;
- 12 s parent wait budget for the TCP helper;
- a poll timeout is not surfaced until the overall budget expires.

## Dual-pane loader

- classes4.dex: Ll/ᩴۛܽ; (123V)
- ۚ()V starts the backend load;
- ۨ()V is the success/UI commit;
- ۨ(Exception)V is the error/UI commit.

Refactor contract:

- each loader receives a generation ticket tied to its concrete pane controller;
- stale success and stale error completions are ignored;
- the UI snapshot is copied before injected ZIP/APK sorting;
- the exact sorted snapshot is written back before any later completion logic.

## Non-goals for this stage

- keep targetSdk 30;
- do not remove the second-packaged feature layer wholesale;
- do not remove native sandbox/seccomp globally;
- do not rewrite unrelated editor/archive engines.

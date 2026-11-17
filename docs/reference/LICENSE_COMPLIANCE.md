# License compliance record

How Command Panel relates to **Better Dual Screen Assistant** (BDSA, © Maik Thomalla / Stormpanda,
"Better Dual Screen Assistant Source-Available License v1.0"), written so the upstream author can check it.
Facts below were gathered on 2026-11-16 from git history and the files in this folder; open items are listed as
open, not glossed over.

## 1. What Command Panel is built from

| Source | How it is used |
|---|---|
| **AYN's stock app** `com.odin.dualscreen.assistant` v1.3, pulled from Brian's own AYN Thor and decompiled | The basis for every stock feature, UI measurement, settings key, broadcast and binder protocol. See `STOCK_TEARDOWN.md`. |
| **A capability probe** written for this project (`PROBE_RESULTS.md`, `probe-report.txt`) | Measures what a normal user app can do on the Thor. Written from scratch. |
| **Brian's own changes in his BDSA fork** | Staged in `fork-port/`, see §3. |
| **BDSA upstream code** | **Not used.** No upstream file or upstream-authored code is copied into Command Panel. |

## 2. How BDSA was consulted

BDSA was read as prior art for *how a user app can replace the stock assistant* (personal reading is permitted by
licence §1). Files read: `LICENSE`, `AGENTS.md`, `CLAUDE.md`, `docs/SYSTEM_INTEGRATION_REFERENCE.md`,
`docs/TECHNICAL_ARCHITECTURE.md`, `docs/devices/AYN_THOR.md`, `docs/FUNCTIONAL_SPECIFICATION.md`,
`app/src/main/AndroidManifest.xml`, `privileged/PServerClient.kt`.

Every technical fact taken from those docs was then re-derived from AYN's stock app, and several were found to be
wrong or different (`STOCK_TEARDOWN.md` §8, "BDSA claims checked against stock"). In particular:

- The `PServerBinder` root-shell protocol is AYN's: the stock app's own client is
  `p028J0/C0103c.java` (`ServiceManager.getService("PServerBinder")`, transaction 0, `writeStringArray([cmd, wait])`,
  `createByteArray()` reply). BDSA's `PServerClient.kt` itself credits the stock app as its source.
  The probe's ~15-line client implements that same vendor protocol; it was written after reading
  `PServerClient.kt`, so this is disclosed here.
- Settings keys, fan/mapping/recents binder interfaces, window types, sysfs paths and timings in Command Panel come
  from the stock decompile, not from BDSA.

The general approach shared with BDSA — a user app, root via the vendor's own `PServerBinder`, an `app_process`
helper for privileged calls, an overlay on the bottom display — is an architecture, not copied code.

## 3. Brian's own fork changes (`fork-port/`)

- Source: `BrianJr03/better-dual-screen-assistant`, which is 43 commits ahead / **0 behind** upstream, so
  `git diff 6b388b5..1e681d5` contains only the fork's own work (40 commits, 115 files, +18,375 / −1,750, matching
  `FORK_CHANGES.md`).
- Files the fork created are copied unchanged; the fork's edits to upstream files are kept only as patches, used as a
  map of what changed, not pasted.
- `git blame -C -C -M` found upstream-authored code inside three fork-created files (`QuickTogglesReorderCard.kt`
  160 lines, two tests 21 and 17 lines). Those exact lines are listed in `fork-port/_upstream_origin_lines/` and are
  **rewritten**, not ported. Details in `PORT_MANIFEST.md`.
- Two ported features sat on upstream infrastructure (the screen-capture engine under the replay buffer, the settings
  activity hosting most fork settings). Command Panel writes its own versions of those instead of porting them.

## 4. Licence clauses

| Clause | Status |
|---|---|
| §1 personal, non-commercial use on owned hardware | Command Panel is a personal project for Brian's own AYN Thor. |
| §2.1 no commercial use | None. |
| §2.2 no pre-installation / bundling on devices | None. |
| §2.3 binaries | No BDSA or modified-BDSA binary has ever been shared. The fork's history has never contained an APK, keystore, `local.properties` or mapping file (`git log --all --diff-filter=A` is empty for them), `.gitignore` excludes them, and the fork has no GitHub releases. Command Panel is unpublished. |
| §2.3 modified source | **Open item — see §5.** |
| §3 contributions | No PRs have been opened upstream, so nothing has been licensed to the author under §3. |

## 5. Status / conditions

1. **The fork's source — RESOLVED 2026-11-17.** `BrianJr03/better-dual-screen-assistant` was briefly public only so
   Claude Code could read it (private-repo access was not set up for the tool); it is now **private again**
   (verified `gh repo view` → `visibility=PRIVATE`), with a local copy kept. No binaries, keystore, `local.properties`
   or mapping file were ever committed to it, and it has no GitHub releases. This closes the one item that was
   outside §2.3.
2. **Command Panel stays unpublished.** It has no git remote (local only) and tracks no binaries/keystore. It contains
   staged fork code (`fork-port/`) that is the fork's own authorship but was written
   against BDSA's codebase. It stays unpublished until each ported feature has been integrated and its upstream
   dependencies rewritten.
3. **AYN assets.** Command Panel reuses the stock app's icons and strings. That concerns AYN, not BDSA, and is for
   personal use only.

## Files in this folder

| File | Contents |
|---|---|
| `STOCK_TEARDOWN.md` | Teardown of AYN's stock app (copy of `~/thor-re/DualScreenAssistant/NOTES.md`) |
| `PROBE_RESULTS.md`, `probe-report.txt` | Capability probe write-up and raw output |
| `FORK_CHANGES.md` | Brian's breakdown of his fork vs upstream |
| `PORT_MANIFEST.md` | What was ported from the fork, provenance check, what was left out |
| `LICENSE_COMPLIANCE.md` | This record |

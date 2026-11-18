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
license §1). Files read: `LICENSE`, `AGENTS.md`, `CLAUDE.md`, `docs/SYSTEM_INTEGRATION_REFERENCE.md`,
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

## 4. License clauses

| Clause | Status |
|---|---|
| §1 personal, non-commercial use on owned hardware | Command Panel is a personal project for Brian's own AYN Thor. |
| §2.1 no commercial use | None. |
| §2.2 no pre-installation / bundling on devices | None. |
| §2.3 binaries | No BDSA or modified-BDSA binary has ever been shared. The fork's history has never contained an APK, keystore, `local.properties` or mapping file (`git log --all --diff-filter=A` is empty for them), `.gitignore` excludes them, and the fork has no GitHub releases. Command Panel releases contain only its own APK (see §8). |
| §2.3 modified source | **Open item — see §5.** |
| §3 contributions | No PRs have been opened upstream, so nothing has been licensed to the author under §3. |

## 5. Status / conditions

1. **The fork's source — RESOLVED 2026-11-17.** `BrianJr03/better-dual-screen-assistant` was briefly public only so
   Claude Code could read it (private-repo access was not set up for the tool); it is now **private again**
   (verified `gh repo view` → `visibility=PRIVATE`), with a local copy kept. No binaries, keystore, `local.properties`
   or mapping file were ever committed to it, and it has no GitHub releases. This closes the one item that was
   outside §2.3.
2. **Command Panel published publicly — 2026-11-17.** With every ported feature integrated and its upstream
   dependencies rewritten, and with `fork-port/` purged from git history (see §6), Command Panel was pushed to a
   **public** GitHub repo. What is published is the app in `app/` plus project docs — it contains **no BDSA
   (stormpanda) code**, so BDSA's license (which governs BDSA's code, not Brian's own) does not restrict it. It ships
   OFL fonts (with notices) and, as of the same day, **no AYN icon art** (loaded at runtime instead — see §7). No
   keystore or signing secret is tracked (`.gitignore` excludes `*.jks`/`*.keystore`/`*.apk`); release signing runs
   in CI from repo secrets.
3. **AYN assets.** Command Panel's own labels are its own functional English; it also bundles 67 of the stock app's
   strings (`stock_strings.xml`, 8 locales) so stock labels keep their ja/ko/zh translations. As of 2026-11-17 it **no longer bundles
   AYN's icon art** — the stock drawables are loaded at runtime from the installed stock app (see §7). This concerns
   AYN, not BDSA.

## 6. Re-audit — 2026-11-17 (after looks / arrange / replay / icon were integrated)

Re-checked the shipped app (`app/src/`) after the big feature ports (16-look system, long-press drag-and-drop
Arrange mode, Replay Buffer, launcher icon). Findings:

- **No upstream code in the app.** `grep` for `stormpanda` / `betterdualscreenassistant` across `app/src/` → **0 hits**
  (every ported file was package-renamed; the only `retroid`/`megingiard` hits are descriptive comments in Brian's
  own files, not code or dependencies).
- **Provenance.** The ported features' commits are all authored by BrianJr03: looks `0335e21`,
  arrange `a38cef7`, replay `8a37f8f`/`14f0ce1`.
- **Flagged upstream-origin lines stay out.** The three files the port analysis flagged for upstream-origin lines
  (`QuickTogglesReorderCard`, `ReplayBufferRepositoryTest`, `SettingsBackupTest`) were **not** ported — the reorder
  UI, the replay tests, and the backup test in Command Panel are clean-room. A line-by-line scan confirms none of
  their flagged lines appear in `app/src/`.
- **Clean-room rewrites of upstream-base code.** The spec/widget base (`TccLookSpec`, `TccLookWidgets`, the overlay
  widgets), the scoped-path validator (`PrivilegedHelperMain.isValidScopedMediaOutputPath`) and the screen-capture
  engine (`ReplayCaptureEngine`, upstream's `ScreenRecorderEngine` capture was **not** ported) are Command Panel's
  own code; only Brian's pure helpers (replay bitrate/path math, ring buffer, renderers) were ported.
- **Fonts.** The 8 bundled font families are SIL OFL 1.1; `assets/THIRD_PARTY_NOTICES.txt` ships the attribution +
  full license alongside them.
- **Still unpublished / private** (as of this re-audit; superseded by §5.2 and §8). Command Panel had **no git remote** and tracks no binaries/keystore; the fork is
  **private** (`gh repo view` → `PRIVATE`); no upstream `LICENSE`/`README`/binary is copied into `app/src/`.

**Open item — RESOLVED 2026-11-17.** `fork-port/` (whose `patches/*.patch` carried upstream base lines as diff
context) was first git-excluded, then **purged from all git history** with `git-filter-repo --path fork-port
--invert-paths` before the public push (a full-history backup bundle was kept locally). `git log --all -- 'fork-port/*'`
now returns nothing, so no stormpanda code is present in any published commit; the directory remains on disk as Brian's
untracked local porting reference.

## 7. AYN icons loaded at runtime, not bundled — 2026-11-17

Command Panel previously bundled ~104 of AYN's stock drawables (`app/src/main/res/drawable/`, base + `_black`
pairs). Those were **deleted** and are now loaded at runtime from the installed stock app
(`com.odin.dualscreen.assistant`, a non-removable, world-readable system app on every AYN Thor) via
`jr.brian.commandpannel.privileged.StockResources` (single chokepoint `rememberStockPainter`). The repo and the APK
now ship **zero AYN icon art** — only three CP-authored drawables remain (`ic_launcher_background`,
`ic_launcher_foreground`, `ic_qs_replay_buffer`). If a stock icon can't be read, a neutral CP-owned placeholder is
shown (never AYN art). This removes AYN-asset redistribution as a concern for the public repo. CP's own labels are its
own functional English; the stock labels it reuses come from `stock_strings.xml` (see §5.3).

## 8. Permission from the upstream author — 2026-11-18

Brian reports that on 2026-11-18 stormpanda (Maik Thomalla), the BDSA copyright holder, gave him **full
permission** for Command Panel's use of BDSA-derived work. This is the "prior written permission" BDSA's license
asks for (§2, §2.3); Brian keeps the written message with his records. It covers the items a full-history comparison
turned up the same day:

- The stock ring in `TccLookWidgets.kt` (`drawStockRing`) uses BDSA's `0.055f` stroke calibration (upstream
  `8e3ccf8`; AYN's stock app uses `0.05f`), in the same code shape as upstream's ring gauge.
- The look-spec API keeps upstream's names (`TccLookSpec`, `LocalTccLookSpec`, `TccTileVariant`,
  `tccTextStyle`/`tccDisplayTextStyle`, and spec fields such as `ringDiameterRatio` and `panelCardShape`) so the
  renderers ported from Brian's fork compile unchanged.
- The ported fork features were written inside the BDSA fork, so §2.3 ("modified source code") could otherwise be
  read to reach them.

The rest of that comparison, run against every blob on every Command Panel ref and all 83 upstream commits, found
no upstream code. The only identical files were Android Studio's template launcher icons, replaced in `097ca68`.
The longest shared token run, ignoring imports, is 65 tokens of standard EGL/GLES setup. `fork-port/` is in no commit.
Command Panel is public (`BrianJr03/command-panel`, GPL-3.0 since `e9dea1f`) and its GitHub releases contain only its
own APK.

## Files in this folder

| File | Contents |
|---|---|
| `STOCK_TEARDOWN.md` | Teardown of AYN's stock app (copy of `~/thor-re/DualScreenAssistant/NOTES.md`) |
| `PROBE_RESULTS.md`, `probe-report.txt` | Capability probe write-up and raw output |
| `FORK_CHANGES.md` | Brian's breakdown of his fork vs upstream |
| `PORT_MANIFEST.md` | What was ported from the fork, provenance check, what was left out |
| `LICENSE_COMPLIANCE.md` | This record |

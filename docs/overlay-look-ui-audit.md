# Overlay look UI-audit prompt

Paste the block below into a fresh Claude Code session (with the AYN Thor connected over adb and the
Command Panel debug app installed) to sweep every overlay look, screenshot it, and flag/ fix
readability, clipping and spacing issues.

---

**Task: audit and clean up the Command Panel overlay looks.**

Go through **every** overlay look and verify the bottom-screen overlay is visually correct. Work from
`~/Desktop/Projects/command-panel`.

**Device setup**
- Resolve the serial: `SER=$(adb devices | tail -n +2 | grep -w device | head -1 | awk '{print $1}')`, then
  `export ANDROID_SERIAL=$SER`. The Thor exposes two displays: **top = `0`** (default), **bottom =
  `4630946482288158084`** (overlay lives here). The debug package is `jr.brian.commandpannel.debug`; its prefs
  file is `/data/data/jr.brian.commandpannel.debug/shared_prefs/command_panel.xml` (read/write via `run-as`).

**The looks** (the `overlay_look` enum values):
`AUTO AURORA_GLASS CARTRIDGE TACTICAL_HUD SOFT_CONSOLE SOFT_CONSOLE_OLED SEGMENT_LCD BLOOM NEON_ARCADE
TERMINAL COCKPIT STICKER_BOOK INDUSTRIAL JELLY COMIC_PANEL Y2K_CHROME E_INK`

**For each look, and for BOTH themes** (`tcc_theme_mode` = `4` dark, `3` light — set with
`adb shell settings put system tcc_theme_mode <n>`):
1. Set the look: `run-as ... sed -i 's|\(overlay_look">\)[^<]*|\1<LOOK>|' <prefs>` (the key must already exist;
   if not, open the app once and pick any look so it is written).
2. Reload: `adb shell am force-stop jr.brian.commandpannel.debug` then
   `adb shell am start -n jr.brian.commandpannel.debug/jr.brian.commandpannel.MainActivity` (the service reloads
   prefs on start). Wait ~3s.
3. Show the overlay: `adb shell am broadcast -a jr.brian.commandpannel.action.SHOW_OVERLAY -p jr.brian.commandpannel.debug`.
   It opens on the **Settings tab** (frame-rate card, quick tiles, and the TEMP/FAN + CPU/GPU/PWR/RAM ring row).
4. Capture the bottom screen: `adb exec-out screencap -p -d 4630946482288158084 > audit_<LOOK>.png`.
5. Switch to the **Mode tab** (`adb shell input -d 4 tap 339 940` taps the nav "Mode") and capture again, and
   page the quick tiles (`adb shell input -d 4 swipe 950 360 650 360 200`) to see the overflow tiles.

**Be efficient:** capture all looks first, then crop the ring row (`crop=(0,620,1240,900)`) and montage into one
labeled sheet with PIL so you review 16 looks in one or two image reads instead of 32.

**Check every look for:**
- **Readability** — no label the same tone as its background (no dark text on dark card, no white text on a
  white sticker/paper surface). This bites light theme and the paper/chrome/sticker looks hardest.
- **CPU / GPU / PWR / RAM ring labels** — none clipped by the card edge or the gauge, the value (`1.84`, `401`,
  `0.00`, `6.xx`) and unit (`GHz/MHz/W/GB`) fully visible, and the four evenly spaced with consistent gaps.
- **Mode-tab labels** (`Dual-screen display`, `Only top/bottom screen`) and the nav labels
  (`Mode/Task/Launch/Settings`) — not clipped by the selection box, legible in the selected state.
- **Quick-tile labels** — two-line labels fit; no overlap with the icon or the next tile.

**Fix location** — the per-look renderers in `app/src/main/java/jr/brian/commandpannel/ui/components/looks/*Renderers.kt`
(gauge/tile/slider/nav renderers), the shared built-ins in
`app/src/main/java/jr/brian/commandpannel/ui/components/TccLookWidgets.kt` and `TccLookSurfaces.kt`, the palette
derivation in `ui/theme/TccLookColors.kt`, and the per-look spec (shapes/fonts/ring ratios) in
`ui/theme/TccLookSpec.kt`. `RingGaugeLayout` in `TccLookWidgets.kt` owns the ring label/value/unit text sizing and
the title band; contrast is clamped in `TccLookColors.kt` (`clampContrast`, `LOOK_TEXT_CONTRAST`).

Report a short table of look → issue → fix, apply the fixes, rebuild (`./gradlew :app:assembleDebug`), reinstall,
and re-capture the affected looks to confirm. **Restore `overlay_look` to its original value and
`tcc_theme_mode` when done.**

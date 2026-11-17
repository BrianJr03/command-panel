# Creating Themes ("Looks") with an AI Agent

Command Panel ships a **Look** system that re-skins the whole overlay — cards, tiles, gauges, sliders, nav bar, backdrop and fonts — live. This guide shows how to add a new Look, ideally by handing the work to an AI coding agent (Claude Code, Cursor, etc.). It's written so you can paste a prompt, point the agent at a template, and iterate from screenshots.

> **One Look themes both surfaces.** The companion settings app reuses the same Look system via the companion's **Appearance → "Companion follows overlay look"** switch (off = the companion's default style). You do **not** write a separate theme for the companion — a single new Look restyles both the overlay and, when that switch is on, the companion.

---

## 1. How a Look is built (the mental model)

A Look is four small things plus optional art:

| Piece | File | What it is |
|---|---|---|
| **Enum value** | `app/.../ui/theme/LookStyle.kt` → `TccUiLook` | The Look's id + display name |
| **Spec** | `app/.../ui/theme/TccLookSpec.kt` → `<Name>LookSpec` + `specForLook()` | Shapes, fonts, and which **style** each part uses |
| **Colors** | `app/.../ui/theme/TccLookColors.kt` → `resolveLookColors()` | Per-Look colors derived from the active palette |
| **Fonts** (optional) | `app/src/main/res/font/tcc_*.ttf` + `ui/theme/TccFonts.kt` | Bundled OFL fonts |
| **Renderers** (only for a *new* visual style) | `app/.../ui/components/looks/<Name>Renderers.kt` | Custom drawing for cards/tiles/gauges/sliders/nav |

The `LookTraits` inside a spec pick one value from each **style enum**:

- `CardStyle`, `TileStyle`, `GaugeStyle`, `SliderStyle`, `NavStyle`, `BackdropStyle`, `HeaderStyle`

Each enum value maps (in `ui/components/looks/LookRenderers.kt`, via the `.renderer()` `when` blocks) to a renderer object. The first few values are shared `BuiltIn*Renderer`s; every other value belongs to one Look's `*Renderers.kt`.

### Two levels of effort

- **Level 1 — Remix existing styles (recommended, ~1 file of real work).** Compose a new Look purely from style values that already exist (e.g. `CardStyle.GLASS` + `TileStyle.NEON` + `GaugeStyle.RING` + a bundled font + new colors). **No rendering code** — just enum + spec + colors. Most "new themes" are this.
- **Level 2 — New visual style (advanced).** Invent a new card/tile/gauge/slider/nav look. Add an enum value to the relevant style enum, write a renderer object implementing the interface, and wire it into the `.renderer()` `when`. The Kotlin compiler forces you to handle the new value (exhaustive `when`), which is a helpful checklist.

---

## 2. Quick start: add a Level‑1 Look

1. **Enum** — in `LookStyle.kt`, add a value to `TccUiLook` and a `label` case:
   ```kotlin
   enum class TccUiLook { AUTO, AURORA_GLASS, /* … */, E_INK, MY_LOOK; }
   // label: MY_LOOK -> "My Look"
   ```
2. **Fonts (optional)** — drop an OFL `.ttf` into `res/font/` named `tcc_<name>.ttf`, add a `FontFamily` in `TccFonts.kt`, and **add attribution to `assets/THIRD_PARTY_NOTICES.txt`** (required — see Gotchas).
3. **Colors** — in `TccLookColors.kt`, `resolveLookColors(palette, look)` returns a `TccLookColors`. Add/branch the fields your Look needs; reuse an existing Look's block as a starting point. Colors should be **derived from `palette`** (accent + bg + text) so the Look follows the user's accent on dark *and* light palettes.
4. **Spec** — in `TccLookSpec.kt`, copy `StockLookSpec` and override only what differs:
   ```kotlin
   val MyLookSpec: TccLookSpec = StockLookSpec.copy(
       look = TccUiLook.MY_LOOK,
       panelCardShape = RoundedCornerShape(aynDp(12f)),
       traits = LookTraits(
           card = CardStyle.GLASS, tile = TileStyle.NEON, gauge = GaugeStyle.RING,
           slider = SliderStyle.GLOW_GRADIENT, nav = NavStyle.GLASS_PILL, backdrop = BackdropStyle.AURORA,
           labelFontFamily = TccMonoFontFamily, displayFontFamily = TccMonoFontFamily,
           canvasFontRes = R.font.tcc_mono_bold, // used by the native-Paint FPS number
       ),
   )
   ```
5. **Map it** — add one line to `specForLook()` in `TccLookSpec.kt`:
   ```kotlin
   TccUiLook.MY_LOOK -> MyLookSpec
   ```
6. **Build, install, screenshot** both overlay tabs and the companion (see Verification). Iterate.

That's the whole Level‑1 flow. No renderer code.

### Level 2 extras (new visual style)

- Add a value to the style enum (e.g. `CardStyle.MY_CARD`).
- Create `ui/components/looks/MyLookRenderers.kt` with an object implementing the interface (`LookCardRenderer`, `LookTileRenderer`, `LookGaugeRenderer`, `LookSliderRenderer`, or `LookNavRenderer`). **Copy the closest existing `*Renderers.kt` as a template** — they show every method you can override.
- Wire the enum value → your object in the matching `.renderer()` `when` in `LookRenderers.kt`.
- Reference the new style in your spec's `LookTraits`.

---

## 3. Driving an agent

Give the agent this file, a **template Look to mirror**, and the ability to build + screenshot. A good prompt:

> Add a new overlay Look called **"<Name>"** to this repo. Follow `GUIDELINE.md`. Mirror **`<ClosestExistingLook>`** as a template (its spec in `TccLookSpec.kt`, colors in `TccLookColors.kt`, and — if it has them — its renderers in `ui/components/looks/`). The vibe is **<describe: colors, mood, font feel, card/tile/gauge shapes>**. Steps: add the `TccUiLook` value + label; add colors in `resolveLookColors`; (add a font if needed, with a `THIRD_PARTY_NOTICES.txt` entry); create `<Name>LookSpec` and map it in `specForLook`; build `:app:assembleDebug`, install on device **<serial>**, set the Look (see below), and screenshot **both overlay tabs and the companion in dark and light**. Then run the audit in `docs/overlay-look-ui-audit.md` and fix any contrast/clipping it finds.

**To switch the Look for screenshots** without tapping through the UI, write the pref directly (debug build is `jr.brian.commandpannel.debug`):
```sh
adb -s <serial> shell am force-stop jr.brian.commandpannel.debug
# shared_prefs/command_panel.xml → <string name="overlay_look">MY_LOOK</string>
adb -s <serial> shell run-as jr.brian.commandpannel.debug cp /data/local/tmp/prefs.xml shared_prefs/command_panel.xml
adb -s <serial> shell am start -n jr.brian.commandpannel.debug/jr.brian.commandpannel.MainActivity
adb -s <serial> shell am broadcast -a jr.brian.commandpannel.action.SHOW_OVERLAY -p jr.brian.commandpannel.debug
adb -s <serial> exec-out screencap -p -d <bottom-display-id> > look.png   # bottom screen = the overlay
```
Dark vs light is `Settings.System tcc_theme_mode` (1 = follow night, 3 = light, else pure black).

There is **no Compose `@Preview`** in this project — the agent must verify on a device/emulator.

---

## 4. Gotchas (read these — they are where themes break)

**Palettes & contrast**
- A Look must look right on **dark (pure-black/AMOLED) and light** palettes, and follow the user's **accent**. Colors come from `palette` in `resolveLookColors` — don't hard-code a single background/text color. Test both themes.
- **Selected nav label color:** the nav renderer's `textColor` must contrast with the **panel** (the label sits below the highlight), not reuse the on-highlight icon tint — otherwise the selected label becomes invisible. Override `textColor(p, selected)` explicitly.
- **Light-theme faint labels:** thin/low-contrast label colors can disappear on light palettes. Check both.

**Fonts & sizing (the #1 source of clipping)**
- Wide or tall display fonts (e.g. Michroma, 7‑segment, rounded‑bold, serif) **overflow** the small telemetry cards. Tune, per Look, in `SettingsTabLookSpec`: `ringValueScale`, `ringTitleScale`, `ringUnitScale`, `ringDiameterRatio`, and `infoValueScale` (TEMP/FAN). Shrink until nothing clips.
- `tccTextStyle()` = the **label** font; `tccDisplayTextStyle()` = the **display/number** font. Use the display font for big numerals.
- The big FPS number is drawn with a native `Paint` using `traits.canvasFontRes` — set it if your Look has a display font, or the FPS number falls back to the default face.

**Layout realities**
- The hardware cards are **adaptive**: the telemetry rings render 2×2 in a tall card and 1×4 in a wide strip; TEMP/FAN shows two stat columns when tall and stacked rows when short; the FPS counter grows in a big card. If you write custom gauge/card renderers, make sure they look right **both** large and small.
- Nav selection shapes should be centered on the **icon box** (`size.height / 2`), not a fixed offset, or the highlight floats above the icon.

**Assets & licensing (this is open source — take it seriously)**
- **Fonts:** bundle only **SIL OFL / permissively-licensed** fonts, named `tcc_*.ttf`, and add an attribution block to `assets/THIRD_PARTY_NOTICES.txt`. No fonts you don't have the right to ship.
- **Icons are NOT bundled.** The overlay loads AYN's stock drawables **at runtime** from the installed stock app (`com.odin.dualscreen.assistant`); your renderers just tint them via `ColorFilter`. **Do not add AYN icon art to the repo.** (See `docs/reference/LICENSE_COMPLIANCE.md`.)
- Only contribute colors/fonts/code you have the right to.

**Mechanics**
- `StockLookSpec` (the `AUTO` Look) is the baseline — `copy()` it and override only differences.
- Adding a **style enum value** without wiring its `.renderer()` case is a **compile error** (exhaustive `when`) — that's your reminder to add the renderer.
- E‑Ink deliberately ignores the accent (stays monochrome); some Looks intentionally diverge from the "follow the accent" rule — that's fine, just be deliberate.
- Changing `TccUiLook`'s set doesn't need a migration: saved looks resolve by name and fall back to `AUTO`.

---

## 5. Verification checklist

Build, install, and for your Look check **overlay Mode tab, overlay Settings tab, and the companion**, in **dark and light**:

- [ ] Card fills/borders read correctly (no card-on-card or text-on-same-color).
- [ ] Quick-tile **selected** state is obvious; labels aren't clipped.
- [ ] Nav bar: the **selected** item's label is visible **and** its highlight is centered on the icon.
- [ ] Gauges: CPU/GPU/PWR/RAM **labels + values fit** (no top/side clipping) at both 2×2 and 1×4.
- [ ] TEMP/FAN values fit in both the stacked and 2‑column forms.
- [ ] FPS card: the `60Hz/120Hz` corner label and the big counter fit, in your font.
- [ ] Sliders, dialogs (fan/controller/theme/focus), and the backdrop all match the Look.
- [ ] Run **`docs/overlay-look-ui-audit.md`** (a ready-made screenshot-audit prompt) and fix anything it flags.

When it passes in both themes on a device, open a PR with before/after screenshots of each tab.

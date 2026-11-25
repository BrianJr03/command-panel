# Shared theme

Command Panel publishes its overlay's resolved colors and the applied look's fonts so another app can match them. The
contracts are `SharedThemeContract.kt` (colors, version 1) and `SharedFontsContract.kt` (fonts, version 2),
constants-only files a client can copy.

## Reading the theme

```
content://jr.brian.commandpannel.theme/palette
```

Debug builds of Command Panel serve `content://jr.brian.commandpannel.debug.theme/palette`, for debug clients only.

A query returns one row of integers. Pass a projection for a subset. An unknown column is an error. The result is `null`
if Command Panel can't read its theme, and the provider isn't available until the device is first unlocked, so treat
either case as "no shared theme". Inserts, updates and deletes are refused.

| Column | Value |
|---|---|
| `version` | Contract version, currently 1 |
| `is_dark` | 1 for a dark palette, 0 for a light one |
| `bg`, `rail`, `surface`, `surface_high`, `border` | Backgrounds and lines |
| `accent`, `accent_soft`, `accent_focused`, `on_accent` | Accent, its soft tint, its focused fill, and content on it |
| `text_primary`, `text_secondary`, `text_tertiary` | Text |
| `danger`, `success` | Status colors |

Colors are ARGB ints, and `accent_soft` is translucent. They're fully resolved: theme mode, the built-in look's colors,
LED accent sync and Theme Studio's pinned colors are already applied. Soft Console, Soft Console OLED, Segment LCD,
Bloom, Neon Arcade, Terminal, Cockpit, Industrial, Comic Panel and E-Ink supply their own palette. Other built-in looks
differ only in how they draw, so they publish the base colors, and so does a saved look apart from its pinned colors.
Theme Studio's unsaved draft is never published, and "Companion follows overlay look" doesn't affect the result.

`text_primary` and `text_secondary` meet 4.5:1 on `surface`. `text_tertiary`, `accent`, `danger` and `success` only
meet 3:1, so a client with a stricter rule must clamp them.

## Reading the fonts

```
content://jr.brian.commandpannel.theme/fonts
```

A query returns one row (debug builds serve it on the debug authority, like the palette). Projections, errors, `null`
results and the refused writes work as for `/palette`.

| Column | Value |
|---|---|
| `version` | Integer, contract version 2 |
| `look_id` | The applied look: a built-in look's name (`TERMINAL`) or `custom:<id>` for a saved look |
| `display_font` | Font for big numbers and titles, or null |
| `display_font_sha256` | SHA-256 of the display font's file (lowercase hex) for a `user:` font, otherwise null |
| `label_font` | Font for labels and body text, or null |
| `label_font_sha256` | SHA-256 of the label font's file for a `user:` font, otherwise null |

A font value is one of:

- `bundled:<key>`: an open font Command Panel bundles. The client ships its own copy of the same files
  (`app/src/main/res/font/tcc_*.ttf`, licenses in `assets/THIRD_PARTY_NOTICES.txt`).
- `user:<id>`: a font the user imported. Read its file from `fonts/<id>` (below) and cache it by its SHA-256.

| Key | Family | Files |
|---|---|---|
| `condensed` | Barlow Condensed | `tcc_condensed_semibold`, `tcc_condensed_bold`, `tcc_condensed_extrabold` |
| `condensed_display` | Barlow Condensed ExtraBold | `tcc_condensed_extrabold` |
| `mono` | JetBrains Mono | `tcc_mono_regular`, `tcc_mono_bold` |
| `segment` | DSEG7 Classic Bold | `tcc_segment_bold` |
| `rounded` | Baloo 2 ExtraBold | `tcc_rounded_bold` |
| `stencil` | Black Ops One | `tcc_stencil` |
| `comic` | Bangers | `tcc_comic` |
| `chrome_wide` | Michroma | `tcc_chrome_wide` |
| `serif` | Literata | `tcc_serif_regular`, `tcc_serif_italic` |

The fonts are resolved the way the overlay draws them: a saved look's own fonts over its base look's, and a look with no
display font of its own publishes its label font as `display_font`. Null means the system font, which is also what an
imported font whose file is gone falls back to. Treat a `bundled:` key you don't know as null. Theme Studio's unsaved
draft is never published.

### Imported font files

```
content://jr.brian.commandpannel.theme/fonts/<id>
```

`ContentResolver.openFileDescriptor(uri, "r")` returns the font file (`font/sfnt`, TrueType or OpenType). It's served
only to an allowlisted app: every current signer of the calling package must match the pin Command Panel holds for that
package. Today that's Jarngreipr (`jr.brian.home`) with its release key; a debug Command Panel also serves Jarngreipr's
debug build (`jr.brian.home.debug`). Any other caller gets a `SecurityException`, a write mode is refused, and an id that
isn't an imported font (including anything with `.` or `/`) gets a `FileNotFoundException`. Bundled fonts aren't
served.

## Client rules

1. Declare the authority in `<queries>`. A release client declares the release authority only:
   ```xml
   <queries>
       <provider android:authorities="jr.brian.commandpannel.theme" />
   </queries>
   ```
2. **Authenticate before querying or observing.** Resolve the provider with `PackageManager.resolveContentProvider`, then
   require both:
   - its `packageName` is exactly `RELEASE_PACKAGE` (`jr.brian.commandpannel`). A release client must not trust
     `jr.brian.commandpannel.debug`: debug builds use a different key and aren't a release identity.
   - that package is signed with `RELEASE_CERT_SHA256`, checked with
     `PackageManager.hasSigningCertificate(packageName, certDigestBytes, PackageManager.CERT_INPUT_SHA256)`.

   Without this, another app could claim the authority when Command Panel isn't installed. If either check fails, use
   your own theme. Check again whenever you re-read, since the package can be replaced while your app runs.
3. Ignore a row whose `version` you don't support, and fall back to your own theme.
4. Register a `ContentObserver` on each URI you read (`/palette`, `/fonts`); each is notified only when its own row
   changes. Also re-read on resume and on configuration changes, because Command Panel can only send a change while its
   process is running.

## Compatibility

Version 1 is frozen: one row, these 16 integer columns (`version`, `is_dark` and 14 color roles). Never add, remove,
rename or reorder a column. Any change to the row means a new `VERSION`. Read columns by name.

The fonts row is version 2 and frozen the same way: these 6 columns, in this order. A bundled key is never renamed or
reused; a new bundled family only adds a key, which older clients treat as null.

Which colors and fonts a look resolves to can change without a new version. The roles and their meaning stay the same.

Shapes and look renderers stay in Command Panel.

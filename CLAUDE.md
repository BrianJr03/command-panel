# CLAUDE.md

## UI strings

- Always put user-facing text in string resources, never as literals in Kotlin. This covers `Text(...)`, `contentDescription`, button/chip/radio labels, card titles and descriptions, dialog titles and options, tile and nav labels, status messages put into UI state, and notifications.
- New strings go in `app/src/main/res/values/strings.xml`, under the matching prefix group (`settings_*`, `overlay_*`, `tile_*`, `dialog_*`, `nav_*`, `look_*`, `unit_*`, `msg_*`, …).
- If the text matches an existing key in `stock_strings.xml` exactly, reuse that key. Those keys already have ja/ko/zh translations. Don't edit `stock_strings.xml`; it mirrors the AYN stock app.
- How to resolve a string:
  - In composables: `stringResource(R.string.x)`.
  - Where you have a `Context` (repository, ViewModel via `app.getString`, service): `getString(...)`.
  - For counts: `<plurals>` with `getQuantityString`.
- Enums that carry a display name store a `@StringRes` id (`labelRes` / `titleRes`), not a `String`.
- Use positional format args (`%1$s`, `%1$d`) for interpolated values, and write `\n` for forced line breaks. Escape apostrophes (`\'`). Mark a string `formatted="false"` if it contains a literal `%` and is never formatted.
- Leave these as literals: pref keys, enum `key`s, drawable names (`"ic_..."`), animation debug labels, log tags, shell commands, and the Terminal look's decorative glyphs.

## Language

- Use American English everywhere: UI strings, code comments, tests, commit messages and docs. For example: color, behavior, favorite, neighbor, center, gray, license, canceled, modeled, toward, and `-ize` forms (customize, normalize, recognize). This applies even if the session appears to be in the UK.
- Exceptions: `stock_strings.xml` (AYN's own text) and any third-party license text quoted word for word.

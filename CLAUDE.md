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

## Gamepad focus and scrolling (companion)

- Focused items scale up and draw a focus ring, so anything inside a scrolling container needs room at its edges. Compose's default `BringIntoViewSpec` scrolls a focused item only until it's flush with the viewport edge, which clips the first/last item's ring and scale.
- Wrap scrolling content in `PaddedBringIntoView { ... }` (`ui/settings/CompanionWidgets.kt`), which provides a `PaddedBringIntoViewSpec` through `LocalBringIntoViewSpec` to every nested scrollable, vertical and horizontal. The settings pane and Theme Studio already do this. A new full-screen companion view must wrap its own content, since it doesn't sit inside the settings pane.
- Also give horizontally scrolling rows some content padding (padding applied after `horizontalScroll`, as `ChipRow` does) so the first and last items aren't clipped at rest.
- Never put a raw `BasicTextField` / `TextField` in the companion: it traps the D-pad (arrows move the cursor) and B falls through to Back and closes the app. Use `GamepadTextField` (`CompanionWidgets.kt`): focus stops on it, A starts editing, and D-pad up/down or B stop editing.

## Promo videos

- Videos live in a separate private repo, `BrianJr03/command-panel-reveal` (local: `~/Desktop/Projects/command-panel-reveal`). Read its `CLAUDE.md` before starting a new video: it has the decisions, preferences, music edit, capture setup and rendering gotchas from the 60-second reveal.

## "Ship it" shortcut

When the user says **"ship it"**, run this sequence end-to-end. The phrase is the authorization; only stop for the questions below.

This repo ships straight from `main` (no `dev` branch, no PRs). If the current branch isn't `main`, ask whether to merge it into `main` first or ship that branch as-is.

1. **Patch notes first (`app/src/main/res/raw/patch_notes.md`), every time.**
   - Ask via `AskUserQuestion`: fresh page for the new version (default, matches past releases: link back to the previous version's release notes) or append to the current page.
   - Draft entries from everything since the last release tag (`git log <last-tag>..HEAD` plus the working-tree diff). User-facing wording only: what they see or can now do. Short, one line per entry, bold lead-in like the existing notes. No file paths, class names or dev jargon. **No em dashes.** American English.
   - Keep the `## Command Panel v<version>` heading and the license footer.
2. **Version.** Ask what version to ship (suggest a minor bump for features, patch for fixes) and whether to bump `versionCode` (default yes, +1). Update `versionName` / `versionCode` in `app/build.gradle.kts`. The heading in `patch_notes.md` and `versionName` MUST match.
3. **Build check.** `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` must pass before committing.
4. **Commit everything** (never keystores, `.properties` credentials or `.env`). Short message saying why, in the repo's `type(scope): summary` style. No `Co-Authored-By` or generated-by footers. Skip if there's nothing to commit.
5. **Push `main`** to `origin`. Pushing a `versionName` change also starts the workflow's early-access build; that's expected.
6. **Cut the release** with the `Build Release` workflow (it signs with the CI keystore and uses `patch_notes.md` as the release body):
   ```sh
   gh workflow run build-release.yml --ref main \
     -f create_release=true \
     -f release_tag=v<versionName> \
     -f release_name="Command Panel v<versionName>" \
     -f prerelease=false
   ```
7. **Report** the pushed commit, and the run URL (`gh run list --workflow=build-release.yml --branch main --event workflow_dispatch --limit 1 --json url -q '.[0].url'`). Watch the run, and once it finishes, report the release URL (`gh release view v<versionName> --json url -q .url`) or the failure.

**Never** force-push, skip hooks, retag an existing release, or write em dashes into patch notes.

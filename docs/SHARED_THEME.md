# Shared theme

Command Panel publishes its overlay's resolved colors so another app can match them. The contract is
`SharedThemeContract.kt`, a constants-only file a client can copy.

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

Colors are ARGB ints, and `accent_soft` is translucent. They're fully resolved: theme mode, LED accent sync and Theme Studio's pinned colors are already
applied. Theme Studio's unsaved draft is never published, and "Companion follows overlay look" doesn't affect the result.

`text_primary` and `text_secondary` meet 4.5:1 on `surface`. `text_tertiary`, `accent`, `danger` and `success` only
meet 3:1, so a client with a stricter rule must clamp them.

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
4. Register a `ContentObserver` on the URI. Also re-read on resume and on configuration changes, because Command Panel
   can only send a change while its process is running.

## Compatibility

Version 1 is frozen: one row, these 16 integer columns (`version`, `is_dark` and 14 color roles). Never add, remove,
rename or reorder a column. Any change to the row means a new `VERSION`. Read columns by name.

Which colors a look resolves to can change without a new version, for example when built-in looks start supplying their
own palette. The roles and their meaning stay the same.

The first version shares colors only. Shapes, fonts and look renderers stay in Command Panel.

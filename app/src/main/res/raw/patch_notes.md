## Command Panel v0.2.1

A bug-fix release.

### Fixes
- **The AYN button no longer falls back to the stock assistant.** With *Replace Stock Assistant* on, the stock panel could open instead of Command Panel's overlay, usually after the screen had been off. Android was restarting the stock assistant after the screen turned on, and the restarted copy wasn't held back. Command Panel now keeps it suppressed through screen on and off, and takes the button back if it ever restarts.
- **Theme Studio's preview switches tabs right away.** Picking *Settings* or *Mode* above the preview now updates it immediately, instead of only after switching to *Cards* and back.

### Good to know
- Command Panel's overlay can't appear while Android Settings (or another protected system screen) is open on the top screen: Android hides app overlays there. Leave that screen and the overlay shows.

See the [v0.2.0 notes](https://github.com/BrianJr03/command-panel/releases/tag/v0.2.0) for everything else in this version.

---
*Personal project for the AYN Thor, licensed under the GPL v3. Bundles OFL fonts (see THIRD_PARTY_NOTICES); AYN's icons are loaded from the stock app on your device, not shipped.*

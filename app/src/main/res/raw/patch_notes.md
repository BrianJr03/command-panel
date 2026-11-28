## Command Panel v0.15.1

Your Thor can reach deep sleep again with the Replay Buffer on.

### Fixes
- **Less battery drain in sleep.** The Replay Buffer now pauses while the screen is off or the lid is closed, and turns back on when you wake the Thor. Before, it kept recording through sleep and kept the Thor out of deep sleep.
- **A quieter panel in sleep.** The panel stops refreshing its stats while the screen is off, and catches up the moment you wake the Thor.
- **Save shortcuts wait for the screen.** TCP SC and the AYN double press do nothing while the screen is off, so they can't turn the Replay Buffer back on in sleep.
- **Clips keep their sound.** A clip saved just as the Replay Buffer stops no longer loses its audio.

See the [v0.15.0 notes](https://github.com/BrianJr03/command-panel/releases/tag/v0.15.0) for the previous release.

> **About `tcp-sc-0.15.1.apk`:** an optional helper app, TCP SC. It's a shortcut for Replay Buffer: open it from a home screen shortcut, a gesture app or an app like Back Button Hero to save a clip. You only need it for that, and Command Panel can install it for you from its Replay Buffer section. If you install it by hand, install it after Command Panel.

---
*Licensed under the GPL v3. Bundles OFL fonts (see THIRD_PARTY_NOTICES); AYN's icons are loaded from the stock app on your device, not shipped.*

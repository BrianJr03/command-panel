# What's new tour notes

User-facing changes, noted as they land on `main`, so each release's "what's new" tour can be generated quickly. See "What's new tour notes" in `CLAUDE.md` for the format and when to add entries. At "ship it", `## Unreleased` becomes `## v<version>`.

The steps live in `WHATS_NEW_TOURS` (`ui/settings/CompanionTour.kt`), keyed by `WHATS_NEW_TOUR_VERSION`. At "ship it", the newest list is pinned to the shipped version and the next release's list starts under a new key.

## Unreleased

- **Gradient backgrounds and their library** (#67)
  - Where: Appearance, Create your own animated background card (opens the gradient library)
  - Spot: new: GRADIENTS
  - Callout: Keep your own gradient backgrounds in a library and apply one to show it behind the panel on any look. Your image stays saved, so you can switch back anytime.

- **Built-in gradient presets** (#69)
  - Where: Appearance, Create your own animated background card, Open gradients (Presets section of the library)
  - Spot: GRADIENTS
  - Callout: Not sure where to start? Pick one of a dozen presets, or one that matches your look, and press Use to save your own copy and show it.

- **Gradient Studio** (#68)
  - Where: Appearance, Create your own animated background card, Open gradients, then New gradient or a gradient's Edit
  - Spot: GRADIENTS
  - Callout: Build your own gradient with a live preview: pick up to six colors, a style, motion and speed, or press Shuffle for colors that go together. Save and apply shows it behind the panel.

- **Performance details for animated backgrounds** (#80)
  - Where: Experimental, Performance row under the Animated backgrounds switch
  - Spot: ANIMATED_BACKGROUNDS
  - Callout: Open Performance under Animated backgrounds to see what a moving background costs. On the Thor a moving gradient uses about as much as an animated GIF, and only while the panel is open.

- **Background tile** (#72)
  - Where: panel: Background tile (was Background Image)
  - Spot: none
  - Callout: The Background Image tile is now called Background, since it turns a gradient on and off too. Find it in the panel's Quick Tiles.

- **Save clips to the SD card** (#75, replaces the #74 message)
  - Where: Replay Buffer, Save location (Choose folder…)
  - Spot: REPLAY
  - Callout: Choose folder now takes a folder on your SD card or a USB drive, so clips stop filling internal storage. If the card is out, clips go to TCP Clips until it's back.

- **Smoother GIF backgrounds** (#73)
  - Where: panel: an animated GIF or WebP background
  - Spot: none
  - Callout: Animated GIF and WebP backgrounds now play in full color on the panel, so smooth shades no longer show bands.

- **Links in What's new** (#53)
  - Where: What's new dialog
  - Spot: none
  - Callout: Links and notes in What's new now show properly. Move to a link with the D-pad and press A to open it.

## v0.12.0

- **Custom fan speed** (#59)
  - Where: panel: Fan Control tile, Custom option
  - Spot: none
  - Callout: At High performance, pick Custom in the panel's Fan Control tile and a slider sets the fan from 50% to 100%.
- **Background image** (#57, #61)
  - Where: Appearance, Background image card
  - Spot: new: BACKGROUND_IMAGE
  - Callout: Pick a PNG, JPG or WebP to show behind the panel on any look.
- **Animated backgrounds** (#60, #61)
  - Where: Experimental, Animated backgrounds switch
  - Spot: ANIMATED_BACKGROUNDS
  - Callout: Turn this on to use GIF and animated WebP backgrounds. They play only while the panel is open.
- **Background Image tile** (#58)
  - Where: Quick Tiles, tile picker (Tools)
  - Spot: TILE_PICKER
  - Callout: Add the Background Image tile to switch between your image and the look's backdrop from the panel.

# Panel plugins, contract version 1

A Panel plugin is another app that Command Panel (CP) can hand the bottom screen to. The user opens it from a tile in
CP's panel; the plugin's UI replaces the panel on the bottom screen until the plugin closes, the user presses the AYN
button, or the lid closes and reopens. Then CP's panel comes back.

The constants below live in `PanelPluginContract.kt`
(`app/src/main/java/jr/brian/commandpannel/plugin/`). Copy that file into your app unchanged (you may change its
`package` line). It has no Android or CP imports.

## Discovery

Declare a bound service:

```xml
<service
    android:name=".PanelPluginService"
    android:exported="true"
    android:label="@string/plugin_tile_label"
    android:icon="@drawable/ic_plugin_tile">
    <intent-filter>
        <action android:name="jr.brian.commandpanel.plugin.action.PANEL_PLUGIN" />
    </intent-filter>
    <meta-data
        android:name="jr.brian.commandpanel.plugin.VERSION"
        android:value="1" />
</service>
```

- **Action** `ACTION_PANEL_PLUGIN` = `jr.brian.commandpanel.plugin.action.PANEL_PLUGIN`. This is a fixed namespace,
  not CP's app id, so it doesn't change between CP's release and debug builds.
- **Meta-data** `META_VERSION` = `jr.brian.commandpanel.plugin.VERSION`: the highest contract version the plugin
  speaks, as an integer.
- **Label and icon**: CP's tile uses the service's `android:label` and `android:icon` (falling back to the app's).
  One tile per plugin service.

CP finds plugins with `PackageManager.queryIntentServices` on that action.

## Trust

The contract is open, but CP only lists a plugin when **every current signer of its package** has a SHA-256
certificate digest on CP's allowlist. In version 1 the allowlist has one entry: ThorKBM's release key. A plugin
signed with any other key (including a debug key) isn't listed. To be added, send the SHA-256 digest of your release
certificate (`apksigner verify --print-certs your.apk`).

The plugin checks the caller too: on every message, `Message.sendingUid` must belong to one of
`COMMAND_PANEL_PACKAGES` (`PackageManager.getPackagesForUid`), signed with `COMMAND_PANEL_CERT_SHA256`. Ignore messages
that fail the check. A debug plugin build may relax the certificate part to test against a debug CP.

## Versions

- CP ignores a plugin whose `META_VERSION` is missing, not a positive integer, or **greater** than the version CP
  supports. That plugin needs a newer CP.
- Otherwise the session uses the plugin's version: CP talks down to an older plugin. `MSG_SHOW` carries that version in
  `arg2`.
- A plugin that gets a version it doesn't speak replies `MSG_CLOSED` with `CLOSED_ERROR`.
- Values in `PanelPluginContract.kt` never change meaning. A new message or argument comes with a new version.

## Messages

CP binds the service with `Context.BIND_AUTO_CREATE`. `onBind` returns a `Messenger`. Every message is a
`android.os.Message` with these `what` values:

| Message | Direction | Arguments | Meaning |
|---|---|---|---|
| `MSG_SHOW` (1) | CP to plugin | `arg1` display id, `arg2` session version, `replyTo` CP's Messenger | Show your UI on that display. |
| `MSG_HIDE` (2) | CP to plugin | none | CP's panel is already back. Remove your UI now. |
| `MSG_SHOWN` (101) | plugin to CP | none | Your UI is drawn on the requested display. Once per `MSG_SHOW`. |
| `MSG_CLOSED` (102) | plugin to CP | `arg1` reason (`CLOSED_*`) | You ended the session. |

Reasons for `MSG_CLOSED`:

- `CLOSED_BY_USER` (0): the user closed the plugin, for example its own Hide key.
- `CLOSED_OPENED_ELSEWHERE` (1): the plugin's UI was opened outside CP (see below).
- `CLOSED_ERROR` (2): the plugin can't show (unsupported version, its UI failed to start).

Unknown reasons are treated like `CLOSED_BY_USER`.

## A session

1. The user picks the plugin's tile. CP binds the service and sends `MSG_SHOW`.
2. The plugin starts its UI on display `arg1` (for example `ActivityOptions.makeBasic().setLaunchDisplayId(arg1)`)
   and, once its first frame is drawn, sends `MSG_SHOWN` to `replyTo`.
3. CP hides its panel only after `MSG_SHOWN`, so the bottom screen is never empty.
4. The session ends in one of these ways. Each time, CP's panel comes back:
   - **The plugin closes** (its Hide key, say): it sends `MSG_CLOSED`. CP shows its panel, then sends `MSG_HIDE`.
   - **The user presses the AYN button**: CP shows its panel, then sends `MSG_HIDE`.
   - **The lid closes and reopens**: on reopen CP shows its panel, then sends `MSG_HIDE`.
   - **The plugin never says it's shown**: no `MSG_SHOWN` within `SHOWN_TIMEOUT_MS` (3 s) of `MSG_SHOW`. CP keeps
     its panel and sends `MSG_HIDE`.
   - **The plugin dies**: the binder dies (`onServiceDisconnected` / `binderDied`). CP shows its panel.
5. CP unbinds after `MSG_HIDE` or binder death.

### What the plugin does on MSG_HIDE

CP's panel is already on screen when `MSG_HIDE` arrives, so there's no blank flash. The plugin must:

- finish or remove its bottom-screen UI right away, without an exit animation that covers the panel;
- not reply (no `MSG_CLOSED`), and not start anything on the bottom screen afterward;
- treat itself as out of plugin mode from then on (its Hide key goes back to normal).

### Lower screen power

CP owns the bottom screen's power during a session. A plugin must not turn the bottom screen off or on itself.

### Opened outside CP

A plugin is in **plugin mode** only between `MSG_SHOW` and the end of that session. Opened any other way (its
launcher icon, a notification, another app) it behaves as a normal app and sends nothing.

If the user opens the plugin's UI another way **during** a session (for example from its launcher icon on the top
screen), the session is over: the plugin sends `MSG_CLOSED` with `CLOSED_OPENED_ELSEWHERE`, leaves plugin mode, and
carries on as a normal app. CP then shows its panel and sends `MSG_HIDE`; the plugin ignores that `MSG_HIDE` for the
UI it just opened elsewhere.

## Checklist for a plugin

- [ ] Exported service with the `ACTION_PANEL_PLUGIN` filter, `META_VERSION` meta-data, `android:label`, `android:icon`.
- [ ] Release build signed with a key on CP's allowlist.
- [ ] Every incoming message checked against `sendingUid`.
- [ ] `MSG_SHOWN` sent once the UI is drawn on the given display, well under 3 s.
- [ ] `MSG_CLOSED` sent when the plugin closes itself or is opened elsewhere mid-session.
- [ ] `MSG_HIDE` removes the UI immediately, with no reply.
- [ ] No changes to the bottom screen's power.

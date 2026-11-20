#!/usr/bin/env bash
# Verifies #57 (image and GIF backgrounds for custom looks) and #58 (the Background image tile) on the debug build.
#   scripts/verify-image-background.sh [--no-build] [--sleep]
#
# Debug build only (jr.brian.commandpannel.debug). It sends no D-pad, A, B or Back keys: images are seeded with the
# debug-only DEBUG_BACKGROUND broadcast (the same import checks as Theme Studio's picker, then a "Background test" look
# is applied), the panel is shown and hidden with SHOW_OVERLAY / HIDE_OVERLAY, and the tile is pressed through
# DEBUG_BACKGROUND --ez press_tile true (the tile's own onTileClick path). With --sleep it also puts the screen to sleep
# and wakes it (KEYCODE_SLEEP / KEYCODE_WAKEUP, never the power key), after checking the focused app on the top screen
# isn't the release Command Panel; it never gets past a lock screen.
#
# Test images are made here with ffmpeg: a red PNG, a JPG stored sideways with EXIF orientation 6 (blue on top, yellow
# below, so upright it is yellow on the left and blue on the right), a 50 fps animated GIF, and two oversized files.
# Checks:
#   - PNG: imported, the panel's edges are red; frames, CPU and memory with the panel shown and hidden;
#   - JPG: EXIF rotation applied (left edge yellow, right edge blue), cropped to fill;
#   - GIF: imported as animated; it animates while shown, at no more than about 30 fps (frames / ms from the
#     "animation stopped" log line); decoding stops when the panel hides (log line, no frames while hidden);
#   - limits: an animation over twice the screen is refused; a still photo over it is downscaled on import, to the
#     smallest size that still covers twice the screen;
#   - tile (#58): off shows the backdrop (red gone) and stops a GIF; on brings the image back; the pref flips;
#     on a built-in look (no image) the press does nothing;
#   - --sleep: the GIF stops when the screen sleeps and starts again on wake.
# At the end the user's look comes back, the test look and its images are deleted, and the prefs file is restored.
# Screenshots and the numbers go to $OUT_DIR; looks, smoothness, battery and heat need a person.
set -uo pipefail
cd "$(dirname "$0")/.."

build=true
sleep_test=false
for arg in "$@"; do
  case "$arg" in
    --no-build) build=false ;;
    --sleep) sleep_test=true ;;
    *) echo "unknown argument: $arg" >&2; exit 2 ;;
  esac
done

PKG="jr.brian.commandpannel.debug"
RELEASE_PKG="jr.brian.commandpannel"
COMPANION="$PKG/jr.brian.commandpannel.MainActivity"
APP_APK="app/build/outputs/apk/debug/app-debug.apk"
PREFS="/data/user_de/0/$PKG/shared_prefs/command_panel.xml"
ACTION_PREFIX="jr.brian.commandpannel.action"
DEV_DIR="/data/local/tmp/cp-bg"
FFMPEG="${FFMPEG:-/opt/homebrew/bin/ffmpeg}"
FFPROBE="${FFPROBE:-$(dirname "$FFMPEG")/ffprobe}"
OUT_DIR="${OUT_DIR:-$(mktemp -d -t verify-image-background)}"

passes=0; fails=0; skips=0
pass() { echo "  PASS  $*"; passes=$((passes + 1)); }
fail() { echo "  FAIL  $*"; fails=$((fails + 1)); }
skip() { echo "  SKIP  $*"; skips=$((skips + 1)); }
step() { echo; echo "== $*"; }
check() { local what="$1"; shift; if "$@"; then pass "$what"; else fail "$what"; fi; }
note() { echo "  info  $*"; echo "$*" >> "$OUT_DIR/numbers.txt"; }

sh_dev() { adb shell "$@" 2>/dev/null | tr -d '\r'; }
wait_for() { local n="$1" i; shift; for i in $(seq 1 "$n"); do if eval "$*"; then return 0; fi; sleep 1; done; return 1; }
broadcast() { sh_dev am broadcast -a "$ACTION_PREFIX.$1" -p "$PKG" "${@:2}" >/dev/null; }
pref() { sh_dev run-as "$PKG" cat "$PREFS" | grep "name=\"$1\"" | grep -oE '(value="[^"]*"|>[^<]*<)' | sed -E 's/^value="//; s/"$//; s/^>//; s/<$//'; }
tile_pref() { local v; v="$(pref background_image_on)"; echo "${v:-true}"; }
service_up() { sh_dev dumpsys activity services "$PKG" | grep -q AssistantService; }
start_service() { sh_dev am start -W -n "$COMPANION" --display 0 >/dev/null; wait_for 15 service_up; }
keyguard_up() { local d; d="$(sh_dev dumpsys window)"; echo "$d" | grep 'isKeyguardShowing=' | grep -q 'isKeyguardShowing=true'; }

# The app focused on the top screen (display 0); keys are only sent when it isn't the release Command Panel.
focused_top() {
  sh_dev dumpsys window displays | awk '/Display: mDisplayId=/ { cur = $2 } cur == "mDisplayId=0" && /mCurrentFocus=/ { print; exit }'
}
guard_focus() {
  local f; f="$(focused_top)"
  if echo "$f" | grep -q "$RELEASE_PKG/" && ! echo "$f" | grep -q "$PKG"; then
    fail "the release Command Panel has focus on the top screen ($f): no keys sent, stopping"
    exit 1
  fi
}

# Log lines from LookBackground since the last clear.
LOG=""
log_clear() { adb logcat -c 2>/dev/null; }
log_read() { LOG="$(adb logcat -d -s LookBackground:* 2>/dev/null | tr -d '\r')"; }
log_has() { log_read; echo "$LOG" | grep -qE "$1"; }

# Surfaced windows of the debug package on the bottom display.
panel_windows() {
  sh_dev dumpsys window windows | awk -v d="mDisplayId=$BOTTOM_ID" -v p="package=$PKG" '
    function flush() { if (disp && surf && mine) n++ }
    /^  Window #/ { flush(); disp = surf = mine = 0; next }
    index($0, d " ") { disp = 1 }
    { for (i = 1; i <= NF; i++) if ($i == p) mine = 1 }
    /mHasSurface=true/ { surf = 1 }
    END { flush(); print n + 0 }'
}
show_panel() { broadcast SHOW_OVERLAY; wait_for 8 '[ "$(panel_windows)" -ge 1 ]' && sleep 1.5; }
hide_panel() { broadcast HIDE_OVERLAY; wait_for 8 '[ "$(panel_windows)" -eq 0 ]'; sleep 1; }

# Seeds the test look with the device file $1 and waits for the outcome; prints the stored file name.
seed() {
  log_clear
  broadcast DEBUG_BACKGROUND --es path "$DEV_DIR/$1"
  wait_for 10 'log_has "debug background (set|failed)"' || return 1
  echo "$LOG" | grep -o "debug background set [^ ]*" | tail -1 | awk '{print $4}'
}
press_tile() { broadcast DEBUG_BACKGROUND --ez press_tile true; sleep 1.5; }

# Frames the app rendered over $1 seconds (all its windows; the panel is the only one up).
frames_over() {
  sh_dev dumpsys gfxinfo "$PKG" reset >/dev/null
  sleep "$1"
  sh_dev dumpsys gfxinfo "$PKG" | awk -F': *' '/Total frames rendered/ { s += $2 } END { print s + 0 }'
}
cpu_pct() { local pid; pid="$(sh_dev pidof "$PKG")"; [ -n "$pid" ] && sh_dev top -b -q -n 2 -d 2 -p "$pid" -o %CPU | tail -1 | xargs; }
pss_kb() { sh_dev dumpsys meminfo "$PKG" | awk '/TOTAL PSS:/ { print $3; exit } /^ *TOTAL / { print $2; exit }'; }
measure() {
  local label="$1"
  local f c m
  f="$(frames_over 5)"; c="$(cpu_pct)"; m="$(pss_kb)"
  note "$label: frames in 5 s = $f, CPU = ${c:-?}%, PSS = ${m:-?} kB"
  LAST_FRAMES="$f"
}

# Bottom screen screenshot to $OUT_DIR/$1.png, and mean "R G B" of its left or right edge strip (8 px, middle half).
shot() { adb exec-out screencap -d "$BOTTOM_PHYSICAL" -p > "$OUT_DIR/$1.png" 2>/dev/null; echo "  screenshot: $OUT_DIR/$1.png"; }
edge_rgb() {
  local crop; if [ "$2" = left ]; then crop="8:ih/2:0:ih/4"; else crop="8:ih/2:iw-8:ih/4"; fi
  "$FFMPEG" -v error -i "$OUT_DIR/$1.png" -vf "crop=$crop,scale=1:1:flags=area" -f rawvideo -pix_fmt rgb24 - | od -An -tu1 | xargs
}
is_red() { local r g b; read -r r g b <<< "$1"; [ "$r" -ge 150 ] && [ "$g" -le 90 ] && [ "$b" -le 90 ]; }
not_red() { ! is_red "$1"; }
is_yellow() { local r g b; read -r r g b <<< "$1"; [ "$r" -ge 150 ] && [ "$g" -ge 150 ] && [ "$b" -le 110 ]; }
is_blue() { local r g b; read -r r g b <<< "$1"; [ "$b" -ge 150 ] && [ "$r" -le 90 ] && [ "$g" -le 90 ]; }
differs() { ! cmp -s "$OUT_DIR/$1.png" "$OUT_DIR/$2.png"; }

# Frames per second from the last "animation stopped ... frames=N ms=M" line.
last_fps() {
  log_read
  echo "$LOG" | grep "animation stopped" | tail -1 | sed -E 's/.*frames=([0-9]+) ms=([0-9]+).*/\1 \2/' |
    awk '$2 > 0 { printf "%.1f", $1 * 1000 / $2 }'
}
fps_capped() { awk -v f="$1" 'BEGIN { exit !(f != "" && f <= 31.5) }'; }

# --- test images ----------------------------------------------------------------------------------------------------

make_images() {
  local d="$OUT_DIR/images"; mkdir -p "$d"
  "$FFMPEG" -v error -y -f lavfi -i "color=red:s=1240x1080" -frames:v 1 "$d/red.png" &&
  "$FFMPEG" -v error -y -f lavfi -i "color=yellow:s=400x800,drawbox=x=0:y=0:w=400:h=400:color=blue:t=fill" -frames:v 1 -q:v 2 "$d/plain.jpg" &&
  python3 -I - "$d/plain.jpg" "$d/rotated.jpg" <<'PY' &&
import struct, sys
data = open(sys.argv[1], "rb").read()
assert data[:2] == b"\xff\xd8"
# One-entry big-endian TIFF IFD: Orientation (0x0112), SHORT, count 1, value 6 (rotate 90 clockwise to view).
tiff = b"MM\x00\x2a" + struct.pack(">I", 8) + struct.pack(">H", 1) + struct.pack(">HHIHH", 0x0112, 3, 1, 6, 0) + struct.pack(">I", 0)
payload = b"Exif\x00\x00" + tiff
app1 = b"\xff\xe1" + struct.pack(">H", len(payload) + 2) + payload
open(sys.argv[2], "wb").write(data[:2] + app1 + data[2:])
PY
  "$FFMPEG" -v error -y -f lavfi -i "testsrc2=s=320x240:r=50:d=3" -loop 0 "$d/anim.gif" &&
  "$FFMPEG" -v error -y -f lavfi -i "testsrc2=s=2600x2300:r=5:d=0.4" -loop 0 "$d/huge.gif" &&
  "$FFMPEG" -v error -y -f lavfi -i "color=red:s=5000x4400" -frames:v 1 "$d/huge.png" || return 1
  sh_dev mkdir -p "$DEV_DIR"
  local f; for f in red.png rotated.jpg anim.gif huge.gif huge.png; do adb push "$d/$f" "$DEV_DIR/$f" >/dev/null || return 1; done
  sh_dev chmod 644 "$DEV_DIR/*"
}

# --- setup ----------------------------------------------------------------------------------------------------------

step "Device"
if ! adb get-state >/dev/null 2>&1; then fail "no device connected"; echo; echo "$passes passed, $fails failed, $skips skipped"; exit 1; fi
echo "  output: $OUT_DIR"
[ -x "$FFMPEG" ] || { fail "ffmpeg not found at $FFMPEG (set FFMPEG)"; exit 1; }
if $build; then ./gradlew -q :app:assembleDebug || { fail "debug build"; exit 1; }; fi
check "debug app installed" sh -c "adb install -r '$APP_APK' >/dev/null"
check "service running" start_service
bottom="$(sh_dev dumpsys display | grep -oE "type=EXTERNAL, valid=true, isActive=true, displayId=[0-9]+, uniqueId='local:[0-9]+'" | head -1)"
BOTTOM_ID="$(echo "$bottom" | grep -oE 'displayId=[0-9]+' | cut -d= -f2)"
BOTTOM_PHYSICAL="$(echo "$bottom" | grep -oE "local:[0-9]+" | cut -d: -f2)"
[ -n "$BOTTOM_ID" ] || { fail "no bottom display found"; exit 1; }
echo "  bottom: display $BOTTOM_ID ($BOTTOM_PHYSICAL)"

# Saved so the end of the run puts the user's look, tile state and prefs back.
saved_prefs="$OUT_DIR/prefs-before.xml"
sh_dev run-as "$PKG" cat "$PREFS" > "$saved_prefs"
saved_look="$(pref overlay_look)"
echo "  look before: ${saved_look:-<default>}, tile pref before: $(tile_pref)"
cleanup() {
  step "Restore"
  broadcast HIDE_OVERLAY
  # Deletes the test look and prunes its images, then puts the whole prefs file back with the app stopped.
  broadcast DEBUG_BACKGROUND --es restore_look "$saved_look"
  sleep 2
  sh_dev am force-stop "$PKG"
  adb push "$saved_prefs" /data/local/tmp/verify-bg-prefs.xml >/dev/null 2>&1
  sh_dev run-as "$PKG" cp /data/local/tmp/verify-bg-prefs.xml "$PREFS"
  sh_dev rm -rf /data/local/tmp/verify-bg-prefs.xml "$DEV_DIR"
  start_service && echo "  service restarted, look: $(pref overlay_look), tile pref: $(tile_pref)"
  echo "  images left in the app: $(sh_dev run-as "$PKG" ls files/backgrounds 2>/dev/null | wc -l | xargs)"
  echo; echo "$passes passed, $fails failed, $skips skipped (output: $OUT_DIR)"
}
trap cleanup EXIT
hide_panel
check "test images made and pushed" make_images

# --- #57: still PNG --------------------------------------------------------------------------------------------------

step "PNG background"
name="$(seed red.png)"
check "red.png imported ($name)" test -n "$name"
check "stored as a still image" log_has "animated=false"
# The tile must be on for the image to show; it's put back at the end.
if [ "$(tile_pref)" = false ]; then press_tile; fi
check "tile pref is on" test "$(tile_pref)" = true
show_panel; check "panel shown on the bottom screen" test "$(panel_windows)" -ge 1
shot png-shown
rgb="$(edge_rgb png-shown left)"; check "left edge is red (RGB $rgb)" is_red "$rgb"
measure "PNG shown"
hide_panel; check "panel hidden" test "$(panel_windows)" -eq 0
measure "PNG hidden"

# --- #57: JPG with EXIF rotation -------------------------------------------------------------------------------------

step "JPG with EXIF orientation 6"
name="$(seed rotated.jpg)"
check "rotated.jpg imported ($name)" test -n "$name"
show_panel
shot jpg-shown
left="$(edge_rgb jpg-shown left)"; right="$(edge_rgb jpg-shown right)"
check "upright: left edge yellow (RGB $left)" is_yellow "$left"
check "upright: right edge blue (RGB $right)" is_blue "$right"
hide_panel

# --- #57: animated GIF -----------------------------------------------------------------------------------------------

step "Animated GIF (50 fps source)"
name="$(seed anim.gif)"
check "anim.gif imported ($name)" test -n "$name"
check "stored as an animation" log_has "animated=true"
log_clear
show_panel
check "animation started when the panel showed" wait_for 5 'log_has "animation started"'
shot gif-a; sleep 0.7; shot gif-b
check "frames change while shown" differs gif-a gif-b
measure "GIF shown"
check "it renders while shown (>= 40 frames in 5 s)" test "${LAST_FRAMES:-0}" -ge 40
check "no more than about 30 fps of rendering (<= 175 frames in 5 s)" test "${LAST_FRAMES:-999}" -le 175
hide_panel
check "animation stopped when the panel hid" wait_for 5 'log_has "animation stopped"'
fps="$(last_fps)"; note "GIF playback: ${fps:-?} fps (source 50 fps)"
check "playback capped at 30 fps (${fps:-?} fps)" fps_capped "$fps"
log_clear
measure "GIF hidden"
check "no frames while hidden (${LAST_FRAMES:-?} in 5 s)" test "${LAST_FRAMES:-999}" -le 2
no_restart() { ! log_has "animation started"; }
check "no decoding while hidden (no new start)" no_restart

if $sleep_test; then
  step "GIF stops with the screen off (--sleep)"
  show_panel; log_clear
  guard_focus
  sh_dev input keyevent KEYCODE_SLEEP >/dev/null
  check "animation stopped on sleep" wait_for 8 'log_has "animation stopped"'
  log_clear
  guard_focus
  sh_dev input keyevent KEYCODE_WAKEUP >/dev/null
  sleep 2
  if keyguard_up; then
    skip "lock screen is up after wake: not bypassed (unlock by hand)"
  elif [ "$(panel_windows)" -ge 1 ]; then
    check "animation started again on wake" wait_for 8 'log_has "animation started"'
  else
    skip "panel didn't come back after wake"
  fi
  hide_panel
fi

# --- #57: limits -----------------------------------------------------------------------------------------------------

step "Import limits"
name="$(seed huge.gif)"
refused() { [ -z "$name" ] && log_has "debug background failed"; }
check "an animation over twice the screen is refused" refused
name="$(seed huge.png)"
check "a 5000x4400 still is accepted ($name)" test -n "$name"
if [ -n "$name" ] && [ -x "$FFPROBE" ]; then
  adb exec-out run-as "$PKG" cat "files/backgrounds/$name" > "$OUT_DIR/stored-$name" 2>/dev/null
  dims="$("$FFPROBE" -v error -select_streams v:0 -show_entries stream=width,height -of csv=p=0:s=x "$OUT_DIR/stored-$name")"
  bsize="$(sh_dev wm size -d "$BOTTOM_ID" | grep -oE '[0-9]+x[0-9]+' | tail -1)"
  note "huge.png stored as $dims (bottom screen $bsize)"
  # `wm size` gives the panel's natural (portrait) size; the panel is landscape, so the long side is the width.
  w="${dims%x*}"; h="${dims#*x}"; a="${bsize%x*}"; b="${bsize#*x}"
  sw=$(( a > b ? a : b )); shh=$(( a > b ? b : a ))
  # The smallest size that still covers twice the screen: one side is exactly 2x (1 px rounding), the other at least 2x.
  just_covers() {
    { [ $((w - sw * 2)) -ge -1 ] && [ $((w - sw * 2)) -le 1 ] && [ "$h" -ge $((shh * 2 - 1)) ]; } ||
      { [ $((h - shh * 2)) -ge -1 ] && [ $((h - shh * 2)) -le 1 ] && [ "$w" -ge $((sw * 2 - 1)) ]; }
  }
  check "downscaled to just cover twice the screen ($dims for ${sw}x${shh})" just_covers
  check "smaller than the original 5000x4400" test "${w:-99999}" -lt 5000 -a "${h:-99999}" -lt 4400
else
  skip "stored size check (ffprobe at $FFPROBE)"
fi

# --- #58: the tile ---------------------------------------------------------------------------------------------------

step "Tile with a still image"
seed red.png >/dev/null
show_panel
press_tile
check "tile off: pref false" test "$(tile_pref)" = false
shot tile-off
rgb="$(edge_rgb tile-off left)"; check "backup backdrop shows (left edge RGB $rgb, not red)" not_red "$rgb"
press_tile
check "tile on: pref true" test "$(tile_pref)" = true
sleep 1; shot tile-on
rgb="$(edge_rgb tile-on left)"; check "image back (left edge RGB $rgb)" is_red "$rgb"
hide_panel

step "Tile with a GIF"
seed anim.gif >/dev/null
log_clear
show_panel
check "animation running" wait_for 5 'log_has "animation started"'
log_clear
press_tile
check "tile off stops decoding" wait_for 5 'log_has "animation stopped"'
measure "GIF look, tile off"
check "no animation frames with the tile off (${LAST_FRAMES:-?} in 5 s)" test "${LAST_FRAMES:-999}" -le 15
shot gif-tile-off
sleep 10; note "GIF look, tile off, 10 s later: PSS = $(pss_kb) kB"
log_clear
press_tile
check "tile on starts it again" wait_for 5 'log_has "animation started"'
hide_panel

step "Tile on a look without an image"
# A built-in look has no image (the user's own look comes back at the end).
broadcast DEBUG_BACKGROUND --es restore_look AURORA_GLASS
sleep 2
before="$(tile_pref)"
show_panel
press_tile
check "press does nothing (pref stays $before)" test "$(tile_pref)" = "$before"
shot tile-unavailable
echo "  (the tile shows grayed out only when it's enabled in Tiles settings: check $OUT_DIR/tile-unavailable.png by eye)"
hide_panel

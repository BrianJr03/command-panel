#!/usr/bin/env bash
# Verifies the panel's background image on the debug build: #57 (images and GIFs), #58 (the Background image tile),
# #60 (GIFs on their own surface) and #61 (one image for any look, set in Appearance; GIFs behind Experimental's
# Animated backgrounds switch; Appearance's animated link to that switch).
#   scripts/verify-image-background.sh [--no-build] [--sleep]
#
# Debug build only (jr.brian.commandpannel.debug). Images are set with the debug-only DEBUG_BACKGROUND broadcast (the
# same import checks as Appearance's picker, including the Animated backgrounds gate), the look and the switch are set
# through it too, the panel is shown and hidden with SHOW_OVERLAY / HIDE_OVERLAY, and the tile is pressed through
# DEBUG_BACKGROUND --ez press_tile true (the tile's own onTileClick path). Gamepad keys are sent only for the link
# check, on the top screen, and only after checking that the debug companion has focus there (never the release
# app's). With --sleep it also puts the screen to sleep and wakes it (KEYCODE_SLEEP / KEYCODE_WAKEUP, never the power
# key), after the same check; it never gets past a lock screen.
#
# Test images are made here with ffmpeg: a red PNG, a JPG stored sideways with EXIF orientation 6 (blue on top, yellow
# below, so upright it is yellow on the left and blue on the right), a 50 fps animated GIF, and two oversized files.
# Checks:
#   - PNG: imported, the panel's edges are red; frames, CPU and memory with the panel shown and hidden;
#   - JPG: EXIF rotation applied (left edge yellow, right edge blue), cropped to fill;
#   - GIF: imported as animated; it animates while shown, at no more than about 30 fps (frames / ms from the
#     "animation stopped" log line); decoding stops when the panel hides (log line, no frames while hidden);
#   - GIF on its own surface (#60): it moves onto a SurfaceView layer once the panel is shown, the panel window
#     doesn't repaint per frame, it plays at 20 fps or more on the surface, it leaves the surface as the hide starts
#     (before the panel goes away) and no surface layer is left while hidden; per-thread CPU (top -H) and
#     SurfaceFlinger's CPU for a GIF and a PNG go to numbers.txt;
#   - limits: an animation over twice the screen is refused; a still photo over it is downscaled on import, to the
#     smallest size that still covers twice the screen;
#   - tile (#58): off shows the backdrop (red gone) and stops a GIF; on brings the image back; the pref flips;
#     with no image set the press does nothing;
#   - #61: every check runs on a built-in look (Aurora Glass); with Animated backgrounds off a GIF is refused on
#     import, and one already set shows still (no "animation started", no surface, image visible); turning the switch
#     on while the panel is open starts it, off stops it at once;
#   - #61 link: with the switch off, the companion opens on Appearance, D-pad walks to the "in Experimental" link and A
#     follows it: focus lands on the Animated backgrounds switch in Experimental, and the spotlight dims the rest of
#     the screen (the rail gets darker), then fades (it's back about 2.5 s later);
#   - --sleep: the GIF stops when the screen sleeps and starts again on wake.
# At the end the user's look comes back, the image is removed and its files deleted, and the prefs file is restored.
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

# Sets the device file $1 as the background image (Animated backgrounds on, or $2 = off) and waits for the outcome;
# prints the stored file name (empty when refused).
seed() {
  log_clear
  broadcast DEBUG_BACKGROUND --ez animations "$([ "${2:-on}" = off ] && echo false || echo true)" --es path "$DEV_DIR/$1"
  wait_for 10 'log_has "debug background (set|failed)"' || return 1
  echo "$LOG" | grep -o "debug background set [^ ]*" | tail -1 | awk '{print $4}'
}
animations() { broadcast DEBUG_BACKGROUND --ez animations "$1"; sleep 1.5; }
anim_pref() { local v; v="$(pref animated_backgrounds)"; echo "${v:-false}"; }

# --- the companion on the top screen (the #61 link check) ---
TOP_PHYSICAL=""
top_shot() { adb exec-out screencap -d "$TOP_PHYSICAL" -p > "$OUT_DIR/$1.png" 2>/dev/null; echo "  screenshot: $OUT_DIR/$1.png"; }
# Mean "R G B" of the top screen's left 12% (the rail), which the spotlight dims.
rail_rgb() { "$FFMPEG" -v error -i "$OUT_DIR/$1.png" -vf "crop=iw*0.12:ih*0.8:0:ih*0.1,scale=1:1:flags=area" -f rawvideo -pix_fmt rgb24 - | od -An -tu1 | xargs; }
luma() { local r g b; read -r r g b <<< "$1"; echo $(( (r * 3 + g * 6 + b) / 10 )); }
dump_ui() { sh_dev uiautomator dump /sdcard/verify-bg-ui.xml >/dev/null; adb pull /sdcard/verify-bg-ui.xml "$OUT_DIR/ui.xml" >/dev/null 2>&1; }
# Text inside the focused node (Compose reports focus on the focusable row or card).
focused_text() {
  dump_ui
  python3 -I - "$OUT_DIR/ui.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
for n in ET.parse(sys.argv[1]).getroot().iter("node"):
    if n.get("focused") == "true":
        print(" | ".join(d.get("text") or d.get("content-desc") or "" for d in n.iter("node")))
        break
PY
}
focus_has() { focused_text | grep -q "$1"; }
# The debug companion is the resumed activity on the top screen (display 0), and not the release app.
companion_in_front() {
  local d; d="$(sh_dev dumpsys activity activities)"
  echo "$d" | awk '/^Display #/{d=$2} d == "#0" && /topResumedActivity=/ && !p {print; p=1}' | grep -q "$PKG/"
}
# One gamepad key on the top screen, only while the debug companion has focus there.
key() {
  if ! companion_in_front; then fail "the debug companion isn't in front on the top screen: no key sent, stopping"; exit 1; fi
  sh_dev input -d 0 keyevent "$1" >/dev/null; sleep 0.6
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
# The app's busiest threads (top -H, second 5 s sample) and SurfaceFlinger's CPU, into numbers.txt (#60).
threads() {
  local label="$1" pid out n sf
  pid="$(sh_dev pidof "$PKG")"; [ -n "$pid" ] || return 0
  out="$(sh_dev top -H -b -q -n 2 -d 5 -p "$pid" -o %CPU,CMD)"; n="$(echo "$out" | wc -l)"
  note "$label: threads (% of a core): $(echo "$out" | tail -n $((n / 2)) | sort -rn | head -6 | awk '{ printf "%s %s, ", $2, $1 }')"
  sf="$(sh_dev pidof surfaceflinger)"
  [ -n "$sf" ] && note "$label: SurfaceFlinger CPU = $(sh_dev top -b -q -n 2 -d 5 -p "$sf" -o %CPU | tail -1 | xargs)%"
}
# The GIF's own surface (#60) in SurfaceFlinger's layer list. A SurfaceView layer is named after its window's title,
# and the panel window has none, so it is "SurfaceView[]#<id>" (apps' own SurfaceViews carry their activity's name).
surface_layers() { sh_dev dumpsys SurfaceFlinger --list | grep -c '^SurfaceView\[\]#'; }
# Frames per second the player drew onto its surface, from the last "animation stopped" line.
last_surface_fps() {
  log_read
  echo "$LOG" | grep "animation stopped" | tail -1 | sed -E 's/.* ms=([0-9]+).*surfaceFrames=([0-9]+).*/\2 \1/' |
    awk '$2 > 0 { printf "%.1f", $1 * 1000 / $2 }'
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
  # Removes the image and deletes its files, then puts the whole prefs file back (the look, the tile, the switch).
  broadcast DEBUG_BACKGROUND --ez clear true --es look "$saved_look"
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
top="$(sh_dev dumpsys display | grep -oE "type=INTERNAL, valid=true, isActive=true, displayId=0, uniqueId='local:[0-9]+'" | head -1)"
TOP_PHYSICAL="$(echo "$top" | grep -oE "local:[0-9]+" | cut -d: -f2)"
# The image is the same for every look (#61): all checks run on a built-in one.
broadcast DEBUG_BACKGROUND --es look AURORA_GLASS
sleep 1
check "a built-in look is applied" test "$(pref overlay_look)" = AURORA_GLASS

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
threads "PNG shown"
check "a still image stays in the panel window (no surface)" test "$(surface_layers)" -eq 0
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
# Once the show animation ends, the GIF moves onto its own surface under the panel (#60).
check "the GIF moved onto its own surface once the panel was shown" wait_for 5 'log_has "surface attached"'
check "its surface layer is up" wait_for 3 '[ "$(surface_layers)" -ge 1 ]'
shot gif-a; sleep 0.7; shot gif-b
check "frames change while shown" differs gif-a gif-b
measure "GIF shown"
# The panel window only repaints for its own updates (clock, readouts), not per GIF frame.
check "the panel window doesn't repaint per frame (${LAST_FRAMES:-?} window frames in 5 s)" test "${LAST_FRAMES:-999}" -le 20
threads "GIF shown"
log_clear
hide_panel
# The image follows the panel out: off the surface when the hide starts, then the surface is gone.
check "the GIF left its surface when the panel hid" wait_for 5 'log_has "surface detached"'
check "animation stopped when the panel hid" wait_for 5 'log_has "animation stopped"'
log_read
detach_line="$(echo "$LOG" | grep -n "surface detached" | head -1 | cut -d: -f1)"
stop_line="$(echo "$LOG" | grep -n "animation stopped" | head -1 | cut -d: -f1)"
check "it left the surface before the panel went away" test "${detach_line:-99}" -lt "${stop_line:-0}"
check "no surface layer left while hidden" test "$(surface_layers)" -eq 0
fps="$(last_fps)"; sfps="$(last_surface_fps)"
note "GIF playback: ${fps:-?} fps drawn, ${sfps:-?} fps onto the surface (source 50 fps)"
check "playback capped at 30 fps (${fps:-?} fps)" fps_capped "$fps"
check "it really plays on the surface (${sfps:-?} fps, at least 20)" awk -v f="$sfps" 'BEGIN { exit !(f != "" && f >= 20) }'
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

# --- #61: Animated backgrounds switch ---------------------------------------------------------------------------------

step "Animated backgrounds off (#61)"
name="$(seed anim.gif off)"
refused_off() { [ -z "$name" ] && log_has "debug background failed: .*Experimental"; }
check "a GIF is refused while the switch is off, pointing to Experimental" refused_off
check "the switch is off" test "$(anim_pref)" = false
# A GIF set while it was on, then the switch turned off: it shows still.
seed anim.gif >/dev/null
animations false
log_clear
show_panel
sleep 2
no_start() { ! log_has "animation started"; }
check "off: no player (no \"animation started\")" no_start
check "off: no surface layer" test "$(surface_layers)" -eq 0
shot gif-still
rgb="$(edge_rgb gif-still left)"
bright() { local r g b; read -r r g b <<< "$1"; [ $((r + g + b)) -ge 150 ]; }
check "off: the first frame shows (left edge RGB $rgb, not the dark backdrop)" bright "$rgb"
measure "GIF set, switch off"
check "off: the panel window is idle (${LAST_FRAMES:-?} frames in 5 s)" test "${LAST_FRAMES:-999}" -le 20
log_clear
animations true
check "turning it on plays the GIF with the panel open" wait_for 5 'log_has "animation started"'
log_clear
animations false
check "turning it off stops it at once" wait_for 3 'log_has "animation stopped"'
hide_panel

step "Tile with no image set"
broadcast DEBUG_BACKGROUND --ez clear true
sleep 2
before="$(tile_pref)"
show_panel
press_tile
check "press does nothing (pref stays $before)" test "$(tile_pref)" = "$before"
shot tile-unavailable
echo "  (the tile shows grayed out only when it's enabled in Tiles settings: check $OUT_DIR/tile-unavailable.png by eye)"
hide_panel

# --- #61: the link from Appearance to the switch -----------------------------------------------------------------------

step "Link to Animated backgrounds (gamepad, top screen)"
check "the switch is off (the link only shows then)" test "$(anim_pref)" = false
sh_dev am start -W -n "$COMPANION" --display 0 --es section appearance >/dev/null
sleep 2
if ! companion_in_front; then
  fail "the debug companion didn't come to the front on the top screen: skipping the link check"
else
  # From the rail into the pane, then down until the link has focus.
  key KEYCODE_DPAD_RIGHT
  found=false
  for i in $(seq 1 16); do
    if focus_has "in Experimental"; then found=true; break; fi
    key KEYCODE_DPAD_DOWN
  done
  check "the link has focus in Appearance's Background image card" $found
  if $found; then
    top_shot link-before
    key KEYCODE_BUTTON_A
    # Section switch, 520 ms scroll and the spotlight's glide; it holds 1.5 s.
    sleep 0.8
    top_shot link-spotlight
    check "focus landed on the Animated backgrounds switch" focus_has "Animated backgrounds"
    before_l="$(luma "$(rail_rgb link-before)")"; during_l="$(luma "$(rail_rgb link-spotlight)")"
    note "link: rail brightness before $before_l, during the spotlight $during_l"
    check "the spotlight dims the rest of the screen ($before_l -> $during_l)" test "$during_l" -lt $((before_l * 3 / 4 + 1))
    sleep 2.5
    top_shot link-after
    after_l="$(luma "$(rail_rgb link-after)")"
    note "link: rail brightness after the fade $after_l"
    check "then it fades ($after_l, back near $before_l)" test "$after_l" -ge $((before_l * 3 / 4))
    check "focus is still on the switch" focus_has "Animated backgrounds"
  fi
fi

# Shared helpers for the panel background scripts (verify-image-background.sh, verify-gradient-background.sh).
# Sourced, not run. The caller sets BG_SCRIPT_NAME (for the default output folder) before sourcing, and BOTTOM_ID,
# BOTTOM_PHYSICAL and TOP_PHYSICAL once it has found the displays. Gamepad and power keys go through key() or after
# guard_focus(), which check the focused package first: the release Command Panel can catch stray presses.
PKG="jr.brian.commandpannel.debug"
RELEASE_PKG="jr.brian.commandpannel"
COMPANION="$PKG/jr.brian.commandpannel.MainActivity"
APP_APK="app/build/outputs/apk/debug/app-debug.apk"
PREFS="/data/user_de/0/$PKG/shared_prefs/command_panel.xml"
ACTION_PREFIX="jr.brian.commandpannel.action"
DEV_DIR="/data/local/tmp/cp-bg"
FFMPEG="${FFMPEG:-/opt/homebrew/bin/ffmpeg}"
FFPROBE="${FFPROBE:-$(dirname "$FFMPEG")/ffprobe}"
OUT_DIR="${OUT_DIR:-$(mktemp -d -t "${BG_SCRIPT_NAME:-verify-background}")}"

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

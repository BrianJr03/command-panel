#!/usr/bin/env bash
# Verifies the panel's animated gradient background on the debug build, and measures what it costs (#65, for the
# gradient feature: #64 model, #66 renderer).
#   scripts/verify-gradient-background.sh [--no-build] [--sleep] [--baseline] [--baseline-only] [--runs N]
#
# Uses DEBUG_BACKGROUND's `gradient` extra (system/BackgroundImageDebug.kt) and, for anything that moves, the renderer
# (#66). Debug build only (jr.brian.commandpannel.debug). Gradients are sent as GradientJson (`--es gradient '<json>'`)
# and the hook's log line is checked against what was sent; the look and the Animated backgrounds switch through the same
# broadcast, and the panel is shown and hidden with SHOW_OVERLAY / HIDE_OVERLAY. The only keys sent are SLEEP / WAKEUP
# with --sleep (never the power key), each after checking the release Command Panel doesn't have focus on the top
# screen; it never gets past a lock screen.
#
# Checks (default run):
#   - a still gradient (speed 0, red to blue, linear): the panel's opposite edges are red and blue; two shots of the
#     edges match; no "animation started"; the panel window is idle;
#   - a moving gradient (DRIFT): "animation started" when shown; two shots of the edges a second apart differ;
#     gfxinfo frames, CPU (top), per-thread CPU (top -H), SurfaceFlinger CPU and PSS into numbers.txt; on hide the
#     "animation stopped frames= ms=" line gives 30 fps or less; no frames and no new start while hidden;
#   - every motion (drift, rotate, breathe, hue_cycle, wave) and style (linear, radial, aurora) decodes as sent,
#     starts, moves and keeps the cap;
#   - Animated backgrounds off: the gradient shows still (edges still red and blue, no player); turning the switch on
#     with the panel open starts it, off stops it at once;
#   - screencap -d on both displays while it moves (the top screen's shots are kept for a person to look at);
#   - --sleep: the gradient stops when the screen sleeps, and starts again on wake.
# Baseline (--baseline adds it at the end, --baseline-only runs just it): the same look with a still PNG, the 50 fps
# test GIF and a moving gradient, each shown for --runs samples (default 3): window frames in 5 s, app CPU,
# SurfaceFlinger CPU, PSS and the player's fps. Rows go to baseline.tsv, a summary to numbers.txt, and the budget is
# checked: gradient CPU and SurfaceFlinger at or below the GIF's (plus CPU_SLACK points), PSS within PSS_SLACK_PCT %
# of the PNG's.
#
# The renderer is expected to log on the LookBackground tag, like the GIF player: "animation started <...>" and
# "animation stopped <...> frames=N ms=M ...". Change START_RE / STOP_RE if #66 words it differently.
# At the end the user's look, image and prefs come back and the test gradients are deleted. Screenshots and numbers
# go to $OUT_DIR; looks, smoothness, battery and heat need a person.
set -uo pipefail
cd "$(dirname "$0")/.."

build=true
sleep_test=false
baseline=false
checks=true
RUNS=3
while [ $# -gt 0 ]; do
  case "$1" in
    --no-build) build=false ;;
    --sleep) sleep_test=true ;;
    --baseline) baseline=true ;;
    --baseline-only) baseline=true; checks=false ;;
    --runs) shift; RUNS="${1:-}"; [[ "$RUNS" =~ ^[1-9][0-9]*$ ]] || { echo "--runs needs a number" >&2; exit 2; } ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
  shift
done

BG_SCRIPT_NAME=verify-gradient-background
# Constants, pass/fail counters and the adb, log, panel, measurement, screenshot and test-image helpers.
source scripts/lib/background-helpers.sh

START_RE="${START_RE:-animation started}"
STOP_RE="${STOP_RE:-animation stopped}"
CPU_SLACK="${CPU_SLACK:-1}"
PSS_SLACK_PCT="${PSS_SLACK_PCT:-15}"
GRADIENT_IDS=()

# --- gradients ------------------------------------------------------------------------------------------------------

# GradientJson (system/GradientSpec.kt) on one line with no spaces or single quotes (it goes through the device shell
# in single quotes). Style and motion are the stored keys (linear/radial/aurora, drift/rotate/breathe/hue_cycle/wave);
# colors are given as AARRGGBB hex and sent as JSON numbers (ARGB as an unsigned long), which is what the codec reads.
#   gradient_json <id> <style> <motion> <speed> [angle] [stops as "AARRGGBB@pos,...", in position order]
DEFAULT_STOPS="FFFF0000@0,FF0000FF@1"
gradient_json() {
  local id="$1" style="$2" motion="$3" speed="$4" angle="${5:-0}" stops="${6:-$DEFAULT_STOPS}"
  local js="" s parts
  IFS=',' read -ra parts <<< "$stops"
  for s in "${parts[@]}"; do js+="${js:+,}{\"color\":$((16#${s%@*})),\"position\":${s#*@}}"; done
  printf '{"format":"command-panel-gradient","version":1,"id":"%s","name":"%s","stops":[%s],"style":"%s","angle":%s,"motion":"%s","speed":%s}' \
    "$id" "$id" "$js" "$style" "$angle" "$motion" "$speed"
}

# Makes a gradient the active source (Animated backgrounds on, or $5 = off) and waits for the hook's outcome. The
# decoder is lenient (an unknown style or motion falls back to linear/drift, a bad color to black), so the hook logs
# what it decoded, and this fails loudly unless style, motion, speed and every stop color match what was sent.
#   set_gradient <id> <style> <motion> <speed> [on|off] [angle] [stops]
set_gradient() {
  local id="$1" style="$2" motion="$3" speed="$4" stops="${7:-$DEFAULT_STOPS}" json line want_colors got got_speed
  json="$(gradient_json "$id" "$style" "$motion" "$speed" "${6:-0}" "$stops")"
  GRADIENT_IDS+=("$id")
  log_clear
  broadcast DEBUG_BACKGROUND --ez animations "$([ "${5:-on}" = off ] && echo false || echo true)" --es gradient "'$json'"
  wait_for 10 'log_has "debug gradient (set|failed)"' || { fail "$id: no answer from the debug hook"; return 1; }
  line="$(echo "$LOG" | grep "debug gradient set $id " | tail -1)"
  if [ -z "$line" ]; then fail "$id: the hook refused the spec ($(echo "$LOG" | grep 'debug gradient failed' | tail -1))"; return 1; fi
  want_colors="$(echo "$stops" | tr ',' '\n' | sed 's/@.*//' | tr 'a-f' 'A-F' | paste -sd, -)"
  got="style=$(echo "$line" | sed -nE 's/.* style=([^ ]+).*/\1/p') motion=$(echo "$line" | sed -nE 's/.* motion=([^ ]+).*/\1/p')"
  got+=" colors=$(echo "$line" | sed -nE 's/.* colors=([^ ]+).*/\1/p')"
  got_speed="$(echo "$line" | sed -nE 's/.* speed=([^ ]+).*/\1/p')"
  if [ "$got" != "style=$style motion=$motion colors=$want_colors" ] ||
    ! awk -v a="$got_speed" -v b="$speed" 'BEGIN { exit !(a != "" && a + 0 == b + 0) }'; then
    fail "$id: decoded spec doesn't match what was sent: sent style=$style motion=$motion speed=$speed colors=$want_colors, got $got speed=$got_speed"
    return 1
  fi
}
# The probe uses a non-default style and motion and a non-red color, so a codec mismatch can't pass as the defaults.
hook_present() { set_gradient debug-probe radial wave 0 on 0 "FF00FF00@0,FF0000FF@1"; }

# Raw pixels of an edge strip (left/right/top/bottom, 8 px, middle half) of a bottom screen shot, for comparing shots
# without the clock or readouts in the middle of the panel getting in the way.
edge_raw() {
  local crop
  case "$2" in
    left) crop="8:ih/2:0:ih/4" ;; right) crop="8:ih/2:iw-8:ih/4" ;;
    top) crop="iw/2:8:iw/4:0" ;; bottom) crop="iw/2:8:iw/4:ih-8" ;;
  esac
  "$FFMPEG" -v error -i "$OUT_DIR/$1.png" -vf "crop=$crop" -f rawvideo -pix_fmt rgb24 - | od -An -tu1 | xargs
}
edge_mean() {
  local crop
  case "$2" in
    left) crop="8:ih/2:0:ih/4" ;; right) crop="8:ih/2:iw-8:ih/4" ;;
    top) crop="iw/2:8:iw/4:0" ;; bottom) crop="iw/2:8:iw/4:ih-8" ;;
  esac
  "$FFMPEG" -v error -i "$OUT_DIR/$1.png" -vf "crop=$crop,scale=1:1:flags=area" -f rawvideo -pix_fmt rgb24 - | od -An -tu1 | xargs
}
# Blue-ish: blue clearly leads (a scrim can darken it, so no hard floor on brightness beyond 100).
blueish() { local r g b; read -r r g b <<< "$1"; [ "$b" -ge 100 ] && [ "$b" -ge $((r + 40)) ] && [ "$b" -ge $((g + 40)) ]; }
redish() { local r g b; read -r r g b <<< "$1"; [ "$r" -ge 100 ] && [ "$r" -ge $((g + 40)) ] && [ "$r" -ge $((b + 40)) ]; }
# One pair of opposite edges is red and blue (either way round: the angle convention is #64's). Prints which.
red_blue_edges() {
  local l r t b; l="$(edge_mean "$1" left)"; r="$(edge_mean "$1" right)"; t="$(edge_mean "$1" top)"; b="$(edge_mean "$1" bottom)"
  note "$1 edges: left $l, right $r, top $t, bottom $b"
  { redish "$l" && blueish "$r"; } || { redish "$r" && blueish "$l"; } || { redish "$t" && blueish "$b"; } || { redish "$b" && blueish "$t"; }
}
edges_differ() { [ "$(edge_raw "$1" left) $(edge_raw "$1" right)" != "$(edge_raw "$2" left) $(edge_raw "$2" right)" ]; }
edges_same() { ! edges_differ "$1" "$2"; }
both_shots() { shot "$1"; [ -n "$TOP_PHYSICAL" ] && top_shot "$1-top"; }

log_has_start() { log_has "$START_RE"; }
log_has_stop() { log_has "$STOP_RE"; }
no_start() { ! log_has "$START_RE"; }
# fps from the last stop line (frames= ms=), as the GIF player logs it.
stop_fps() {
  log_read
  echo "$LOG" | grep -E "$STOP_RE" | tail -1 | sed -nE 's/.*frames=([0-9]+) ms=([0-9]+).*/\1 \2/p' |
    awk '$2 > 0 { printf "%.1f", $1 * 1000 / $2 }'
}
# App CPU and SurfaceFlinger CPU over one 5 s sample each (second sample of top).
app_cpu() { local pid; pid="$(sh_dev pidof "$PKG")"; [ -n "$pid" ] && sh_dev top -b -q -n 2 -d 5 -p "$pid" -o %CPU | tail -1 | xargs; }
sf_cpu() { local sf; sf="$(sh_dev pidof surfaceflinger)"; [ -n "$sf" ] && sh_dev top -b -q -n 2 -d 5 -p "$sf" -o %CPU | tail -1 | xargs; }
# A panel window frame cap: 5 s at 30 fps plus room for the clock and readouts.
frames_capped() { [ "${1:-999}" -le 177 ]; }

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
top="$(sh_dev dumpsys display | grep -oE "type=INTERNAL, valid=true, isActive=true, displayId=0, uniqueId='local:[0-9]+'" | head -1)"
TOP_PHYSICAL="$(echo "$top" | grep -oE "local:[0-9]+" | cut -d: -f2)"
echo "  bottom: display $BOTTOM_ID ($BOTTOM_PHYSICAL), top: ${TOP_PHYSICAL:-?}"

saved_prefs="$OUT_DIR/prefs-before.xml"
sh_dev run-as "$PKG" cat "$PREFS" > "$saved_prefs"
saved_look="$(pref overlay_look)"
echo "  look before: ${saved_look:-<default>}, source before: $(pref background_source)"
cleanup() {
  step "Restore"
  broadcast HIDE_OVERLAY
  local id; for id in $(printf '%s\n' "${GRADIENT_IDS[@]}" | sort -u); do broadcast DEBUG_BACKGROUND --es gradient_delete "$id"; done
  broadcast DEBUG_BACKGROUND --ez clear true --es look "$saved_look"
  sleep 2
  sh_dev am force-stop "$PKG"
  adb push "$saved_prefs" /data/local/tmp/verify-grad-prefs.xml >/dev/null 2>&1
  sh_dev run-as "$PKG" cp /data/local/tmp/verify-grad-prefs.xml "$PREFS"
  sh_dev rm -rf /data/local/tmp/verify-grad-prefs.xml "$DEV_DIR"
  start_service && echo "  service restarted, look: $(pref overlay_look), source: $(pref background_source)"
  echo; echo "$passes passed, $fails failed, $skips skipped (output: $OUT_DIR)"
}
trap cleanup EXIT
hide_panel
# The red PNG (for the tile and the baseline) and the 50 fps GIF (baseline).
check "test images made and pushed" make_images
broadcast DEBUG_BACKGROUND --es look AURORA_GLASS
sleep 1
check "a built-in look is applied" test "$(pref overlay_look)" = AURORA_GLASS
# The tile must be on for any background to show (it's put back at the end).
broadcast DEBUG_BACKGROUND --ez clear true; sleep 1
if [ "$(tile_pref)" = false ]; then seed red.png >/dev/null 2>&1; press_tile; broadcast DEBUG_BACKGROUND --ez clear true; sleep 1; fi
hook_present || { fail "the DEBUG_BACKGROUND gradient extra didn't take the probe spec as sent: stopping"; exit 1; }
pass "the debug hook decodes a gradient spec as sent"
check "background_source is gradient:debug-probe ($(pref background_source))" test "$(pref background_source)" = gradient:debug-probe

# --- checks ---------------------------------------------------------------------------------------------------------

if $checks; then
  step "Still gradient (speed 0)"
  check "still spec accepted" set_gradient debug-still linear drift 0
  log_clear
  show_panel; check "panel shown on the bottom screen" test "$(panel_windows)" -ge 1
  sleep 1
  both_shots still-a; sleep 1; both_shots still-b
  check "opposite edges are red and blue" red_blue_edges still-a
  check "a still gradient doesn't change (edges match a second apart)" edges_same still-a still-b
  check "no player for a still gradient (no \"$START_RE\")" no_start
  measure "gradient still"
  check "the panel window is idle (${LAST_FRAMES:-?} frames in 5 s)" test "${LAST_FRAMES:-999}" -le 20
  hide_panel

  step "Moving gradient (DRIFT)"
  check "DRIFT spec accepted" set_gradient debug-drift linear drift 1
  log_clear
  show_panel
  check "animation started when the panel showed" wait_for 5 log_has_start
  sleep 1
  both_shots drift-a; sleep 1; both_shots drift-b
  check "the edges change while it moves" edges_differ drift-a drift-b
  [ -n "$TOP_PHYSICAL" ] && note "top screen while moving: $(differs drift-a-top drift-b-top && echo changed || echo unchanged) (look at drift-a-top.png by eye)"
  measure "gradient DRIFT shown"
  check "window frames within a 30 fps cap (${LAST_FRAMES:-?} in 5 s)" frames_capped "$LAST_FRAMES"
  threads "gradient DRIFT shown"
  note "gradient DRIFT shown: app CPU = $(app_cpu)%, SurfaceFlinger CPU = $(sf_cpu)%"
  log_clear
  hide_panel
  check "animation stopped when the panel hid" wait_for 5 log_has_stop
  fps="$(stop_fps)"
  note "gradient DRIFT playback: ${fps:-?} fps"
  check "playback capped at 30 fps (${fps:-?} fps)" fps_capped "$fps"
  log_clear
  measure "gradient hidden"
  check "no frames while hidden (${LAST_FRAMES:-?} in 5 s)" test "${LAST_FRAMES:-999}" -le 2
  check "no new start while hidden" no_start
  check "no surface layer left while hidden" test "$(surface_layers)" -eq 0

  step "Every motion and style"
  for combo in linear:rotate linear:breathe linear:hue_cycle linear:wave radial:drift aurora:drift; do
    style="${combo%:*}"; motion="${combo#*:}"; id="debug-$(echo "$combo" | tr ':_' '--')"
    set_gradient "$id" "$style" "$motion" 1 on 45 "FFFF0000@0,FF00FF00@0.5,FF0000FF@1" || continue
    log_clear
    show_panel
    started=false; wait_for 5 log_has_start && started=true
    check "$style $motion: starts" $started
    shot "$id-a"; sleep 1; shot "$id-b"
    check "$style $motion: moves" edges_differ "$id-a" "$id-b"
    log_clear
    hide_panel
    wait_for 5 log_has_stop
    fps="$(stop_fps)"; note "$style $motion: ${fps:-?} fps"
    check "$style $motion: capped at 30 fps (${fps:-?})" fps_capped "$fps"
  done

  step "Animated backgrounds off"
  check "DRIFT spec accepted with the switch off" set_gradient debug-drift linear drift 1 off
  check "the switch is off" test "$(anim_pref)" = false
  log_clear
  show_panel
  sleep 2
  check "off: no player (no \"$START_RE\")" no_start
  shot off-a; sleep 1; shot off-b
  check "off: the gradient shows still (red and blue edges)" red_blue_edges off-a
  check "off: it doesn't move" edges_same off-a off-b
  measure "gradient, switch off"
  check "off: the panel window is idle (${LAST_FRAMES:-?} frames in 5 s)" test "${LAST_FRAMES:-999}" -le 20
  log_clear
  animations true
  check "turning it on starts it with the panel open" wait_for 5 log_has_start
  log_clear
  animations false
  check "turning it off stops it at once" wait_for 3 log_has_stop
  hide_panel

  if $sleep_test; then
    step "Gradient stops with the screen off (--sleep)"
    set_gradient debug-drift linear drift 1 >/dev/null
    show_panel; wait_for 5 log_has_start; log_clear
    guard_focus
    sh_dev input keyevent KEYCODE_SLEEP >/dev/null
    check "animation stopped on sleep" wait_for 8 log_has_stop
    sleep 2
    log_clear
    f="$(frames_over 5)"; check "no frames with the screen off ($f in 5 s)" test "${f:-999}" -le 2
    check "no start with the screen off" no_start
    guard_focus
    sh_dev input keyevent KEYCODE_WAKEUP >/dev/null
    sleep 2
    if keyguard_up; then
      skip "lock screen is up after wake: not bypassed (unlock by hand)"
    elif [ "$(panel_windows)" -ge 1 ]; then
      check "animation started again on wake" wait_for 8 log_has_start
    else
      skip "panel didn't come back after wake"
    fi
    hide_panel
  fi
fi

# --- baseline: still PNG vs 50 fps GIF vs gradient -----------------------------------------------------------------

if $baseline; then
  step "Baseline ($RUNS runs each, same look)"
  tsv="$OUT_DIR/baseline.tsv"
  printf 'source\trun\twindow_frames_5s\tapp_cpu_pct\tsf_cpu_pct\tpss_kb\tplayer_fps\n' > "$tsv"
  sample() {
    local label="$1" run="$2" f c s m fps
    log_clear
    show_panel; sleep 2
    f="$(frames_over 5)"; c="$(app_cpu)"; s="$(sf_cpu)"; m="$(pss_kb)"
    [ "$run" -eq 1 ] && threads "baseline $label"
    log_clear; hide_panel; wait_for 5 log_has_stop; fps="$(stop_fps)"
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$label" "$run" "${f:-}" "${c:-}" "${s:-}" "${m:-}" "${fps:--}" >> "$tsv"
    echo "  $label run $run: frames $f, app $c%, SurfaceFlinger $s%, PSS $m kB, fps ${fps:--}"
  }
  for run in $(seq 1 "$RUNS"); do
    name="$(seed red.png)"; check "PNG set ($name)" test -n "$name"; sample png "$run"
    name="$(seed anim.gif)"; check "GIF set ($name)" test -n "$name"; sample gif "$run"
    check "gradient set" set_gradient debug-drift linear drift 1; sample gradient "$run"
  done
  # Mean of column $2 for source $1.
  mean() { awk -F'\t' -v s="$1" -v c="$2" 'NR > 1 && $1 == s && $c != "" { t += $c; n++ } END { if (n) printf "%.1f", t / n }' "$tsv"; }
  for src in png gif gradient; do
    note "baseline $src (mean of $RUNS): frames $(mean $src 3), app CPU $(mean $src 4)%, SurfaceFlinger $(mean $src 5)%, PSS $(mean $src 6) kB, fps $(mean $src 7)"
  done
  le() { awk -v a="$1" -v b="$2" -v k="$3" 'BEGIN { exit !(a != "" && b != "" && a <= b + k) }'; }
  gc="$(mean gradient 4)"; ic="$(mean gif 4)"; gs="$(mean gradient 5)"; is="$(mean gif 5)"
  gp="$(mean gradient 6)"; pp="$(mean png 6)"
  check "budget: gradient app CPU at or below the GIF ($gc% vs $ic%, +$CPU_SLACK)" le "$gc" "$ic" "$CPU_SLACK"
  check "budget: gradient SurfaceFlinger at or below the GIF ($gs% vs $is%, +$CPU_SLACK)" le "$gs" "$is" "$CPU_SLACK"
  pss_room="$(awk -v p="$pp" -v k="$PSS_SLACK_PCT" 'BEGIN { if (p != "") printf "%d", p * k / 100 }')"
  check "budget: gradient PSS near the PNG ($gp kB vs $pp kB, +$PSS_SLACK_PCT%)" le "$gp" "$pp" "${pss_room:-0}"
  check "budget: gradient capped at 30 fps ($(mean gradient 7))" fps_capped "$(mean gradient 7)"
  echo "  rows: $tsv"
fi

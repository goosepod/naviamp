#!/usr/bin/env bash
# Run under dbus-run-session and an X11 display, in a fresh test user/profile.
set -euo pipefail
repo=$(realpath "${1:?signed repository}")
work=$(realpath -m "${2:?fresh evidence directory}")
root=$(cd "$(dirname "$0")/../.." && pwd)
public_key=${3:-"$root/packaging/flatpak/naviamp.asc"}
app=io.github.goosepod.Naviamp
if flatpak info --user "$app" >/dev/null 2>&1 || [[ -e "$HOME/.var/app/$app" ]]; then
  echo 'Use a fresh test user: refusing to touch an existing Flatpak profile' >&2
  exit 1
fi
mkdir -p "$work"
remote=naviamp-acceptance-$$
app_pid=
keyring_pid=
cleanup() {
  if [[ -n "$app_pid" ]]; then flatpak kill "$app" 2>/dev/null || true; fi
  if [[ -n "$keyring_pid" ]]; then kill "$keyring_pid" 2>/dev/null || true; fi
  flatpak uninstall --user --assumeyes "$app" >/dev/null 2>&1 || true
  flatpak remote-delete --user "$remote" >/dev/null 2>&1 || true
}
trap cleanup EXIT
flatpak remote-add --user --gpg-import="$public_key" "$remote" "file://$repo"
flatpak install --user --noninteractive --assumeyes "$remote" "$app"
flatpak info --user --show-permissions "$app" > "$work/permissions.txt"
mkdir -p "$work/keyring-data"
printf naviamp-ci-test-only | env XDG_DATA_HOME="$work/keyring-data" \
  gnome-keyring-daemon --foreground --unlock --components=secrets > "$work/keyring.log" 2>&1 &
keyring_pid=$!
gdbus wait --session --timeout 10 org.freedesktop.secrets
flatpak run --command=sh "$app" -c '
  printf test-only | secret-tool store --label=Flatpak-test naviamp-flatpak test
  test "$(secret-tool lookup naviamp-flatpak test)" = test-only
  secret-tool clear naviamp-flatpak test
  test -x /app/lib/naviamp/bin/Naviamp
  test -d /app/lib/naviamp/lib/runtime
' > "$work/runtime-check.txt" 2>&1
flatpak run "$app" > "$work/application.log" 2>&1 &
app_pid=$!
window=
for ((attempt=0; attempt<120; attempt++)); do
  window=$(xdotool search --onlyvisible --name '^Naviamp$' 2>/dev/null | head -n 1 || true)
  if [[ -n "$window" ]]; then break; fi
  kill -0 "$app_pid"
  sleep .25
done
test -n "$window"
sleep 2
import -window "$window" "$work/application.png"
colors=$(identify -format '%k' "$work/application.png")
test "$colors" -gt 200
gdbus call --session --dest org.mpris.MediaPlayer2.naviamp --object-path /org/mpris/MediaPlayer2 \
  --method org.freedesktop.DBus.Properties.Get org.mpris.MediaPlayer2.Player PlaybackStatus \
  > "$work/mpris.txt"
cp "$root/scripts/flatpak/audio-probe.c" "$work/audio-probe.c"
flatpak run --devel --filesystem="$work" --command=cc "$app" -O2 "$work/audio-probe.c" -ldl -o "$work/audio-probe"
python3 - "$work/tone.wav" <<'PY'
import math,struct,sys,wave
with wave.open(sys.argv[1], 'wb') as output:
    output.setparams((2,2,44100,0,'NONE','not compressed'))
    output.writeframes(b''.join(struct.pack('<hh',value,value) for i in range(44100*6)
                                if (value:=int(5000*math.sin(2*math.pi*440*i/44100))) is not None))
PY
flac --silent --force "$work/tone.wav" -o "$work/tone.flac"
flatpak run --filesystem="$work:ro" --command="$work/audio-probe" "$app" "$work/tone.flac" | tee "$work/native-audio.txt"
printf 'FLATPAK_SMOKE_OK visible_colors=%s\n' "$colors"

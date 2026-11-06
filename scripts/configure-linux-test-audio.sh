#!/usr/bin/env bash
# BASS needs a clocked output. ALSA's unclocked type=null can drain the mixer and
# report Stalled while its source advances, invalidating native clock acceptance.
set -euo pipefail

case "${1:-start}" in
  start)
    : "${RUNNER_TEMP:?RUNNER_TEMP is required}"
    : "${GITHUB_ENV:?GITHUB_ENV is required}"
    runtime=$(mktemp -d "$RUNNER_TEMP/naviamp-test-audio.XXXXXX")
    chmod 700 "$runtime"
    echo "NAVIAMP_TEST_AUDIO_RUNTIME=$runtime" >> "$GITHUB_ENV"
    export PULSE_RUNTIME_PATH="$runtime"
    pulseaudio --daemonize=yes --exit-idle-time=-1 --use-pid-file=yes -n \
      --load="module-native-protocol-unix socket=$runtime/native auth-anonymous=1" \
      --load="module-null-sink sink_name=naviamp_test rate=48000" \
      --log-target="file:$runtime/server.log"
    pactl --server="unix:$runtime/native" set-default-sink naviamp_test
    printf 'pcm.!default { type pulse server "unix:%s/native" }\nctl.!default { type pulse server "unix:%s/native" }\n' \
      "$runtime" "$runtime" > "$runtime/asound.conf"
    {
      echo "ALSA_CONFIG_PATH=$runtime/asound.conf"
      echo "PULSE_SERVER=unix:$runtime/native"
    } >> "$GITHUB_ENV"
    pactl --server="unix:$runtime/native" info
    ;;
  stop)
    runtime=${NAVIAMP_TEST_AUDIO_RUNTIME:-}
    if [[ -z "$runtime" ]]; then exit 0; fi
    # Never terminate a user's or another job's PulseAudio daemon.
    [[ "$runtime" == "${RUNNER_TEMP:?RUNNER_TEMP is required}/naviamp-test-audio."* ]]
    if [[ -f "$runtime/pid" ]]; then
      PULSE_RUNTIME_PATH="$runtime" pulseaudio --kill
      for attempt in {1..30}; do
        if [[ ! -f "$runtime/pid" ]]; then break; fi
        sleep 0.1
      done
      [[ ! -f "$runtime/pid" ]]
    fi
    ;;
  *) echo "Usage: $0 start|stop" >&2; exit 2 ;;
esac

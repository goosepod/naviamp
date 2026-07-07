#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_HOME="${ANDROID_HOME:-/Users/jbmcmichael/Library/Android/sdk}"
ADB="${ADB:-$ANDROID_HOME/platform-tools/adb}"
APP_ID="${APP_ID:-app.naviamp.android}"
DURATION_SECONDS=180
INTERVAL_SECONDS=2
MEMORY_INTERVAL_SECONDS=30
OUT_DIR=""
SERIAL="${ANDROID_SERIAL:-}"
LAUNCH_APP=0
CLEAR_LOGCAT=0
CAPTURE_LOGS=1
RESET_BATTERYSTATS=0
SAMPLE_THRESHOLD_CPU=80

usage() {
    cat <<EOF
Usage: $(basename "$0") [options]

Observe the Android Naviamp app on a connected device while you use it manually.

Options:
  --serial SERIAL          Android device serial. Defaults to ANDROID_SERIAL or the first physical device.
  --app-id ID              Android package id. Defaults to $APP_ID.
  --launch                 Launch the app before observing.
  --duration SECONDS       Observation duration. Defaults to $DURATION_SECONDS.
  --interval SECONDS       Metrics interval. Defaults to $INTERVAL_SECONDS.
  --memory-interval SECONDS
                           Detailed memory sampling interval. Defaults to $MEMORY_INTERVAL_SECONDS.
                           Use 0 to disable routine dumpsys meminfo samples.
  --out DIR                Output directory. Defaults to build/diagnostics/android-observe-<timestamp>.
  --sample-threshold N     Capture thread/mem samples when parsed CPU is at least N percent. Defaults to $SAMPLE_THRESHOLD_CPU.
  --clear-logcat           Clear device logcat before observing.
  --reset-batterystats     Reset Android batterystats before observing. Best used unplugged for a dedicated run.
  --no-logs                Do not run logcat capture.
  --help                   Show this help.

Outputs:
  metrics.csv              Timestamped CPU, PSS/RSS memory, and battery samples.
  app-logcat.log           Naviamp-focused logcat output.
  battery-start.txt        Battery state at the beginning of the run.
  battery-end.txt          Battery state at the end of the run.
  batterystats-*.txt       Android batterystats snapshots, when available.
  samples/*.txt            Top/thread/mem snapshots captured when CPU crosses the threshold.
  summary.txt              Quick average/max CPU and memory summary.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --serial)
            SERIAL="$2"
            shift 2
            ;;
        --app-id)
            APP_ID="$2"
            shift 2
            ;;
        --launch)
            LAUNCH_APP=1
            shift
            ;;
        --duration)
            DURATION_SECONDS="$2"
            shift 2
            ;;
        --interval)
            INTERVAL_SECONDS="$2"
            shift 2
            ;;
        --memory-interval)
            MEMORY_INTERVAL_SECONDS="$2"
            shift 2
            ;;
        --out)
            OUT_DIR="$2"
            shift 2
            ;;
        --sample-threshold)
            SAMPLE_THRESHOLD_CPU="$2"
            shift 2
            ;;
        --clear-logcat)
            CLEAR_LOGCAT=1
            shift
            ;;
        --reset-batterystats)
            RESET_BATTERYSTATS=1
            shift
            ;;
        --no-logs)
            CAPTURE_LOGS=0
            shift
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            usage >&2
            exit 2
            ;;
    esac
done

if [[ ! -x "$ADB" ]]; then
    echo "adb not found or not executable: $ADB" >&2
    echo "Set ANDROID_HOME or ADB to the Android SDK platform-tools path." >&2
    exit 1
fi

select_device() {
    if [[ -n "$SERIAL" ]]; then
        return 0
    fi

    mapfile -t PHYSICAL_DEVICES < <("$ADB" devices | awk 'NR > 1 && $2 == "device" && $1 !~ /^emulator-/ { print $1 }')
    if [[ "${#PHYSICAL_DEVICES[@]}" -eq 1 ]]; then
        SERIAL="${PHYSICAL_DEVICES[0]}"
        return 0
    fi

    mapfile -t ALL_DEVICES < <("$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
    if [[ "${#ALL_DEVICES[@]}" -eq 1 ]]; then
        SERIAL="${ALL_DEVICES[0]}"
        return 0
    fi

    echo "Could not choose a device automatically." >&2
    echo "Connected devices:" >&2
    "$ADB" devices >&2
    echo "Pass --serial SERIAL or set ANDROID_SERIAL." >&2
    exit 1
}

adb_device() {
    "$ADB" -s "$SERIAL" "$@"
}

device_shell() {
    adb_device shell "$@" | tr -d '\r'
}

extract_meminfo_value() {
    local file="$1"
    local label="$2"
    awk -v label="$label" '
        $0 ~ label ":" {
            for (i = 1; i <= NF; i++) {
                if ($i == label || $i == label ":") {
                    print $(i + 1)
                    exit
                }
            }
        }
        $1 == "TOTAL" && label == "TOTAL_PSS_ROW" {
            print $2
            exit
        }
        $1 == "TOTAL" && label == "TOTAL_RSS_ROW" {
            print $6
            exit
        }
        $1 == "TOTAL" && label == "TOTAL_SWAP_PSS_ROW" {
            print $5
            exit
        }
    ' "$file"
}

extract_battery_value() {
    local file="$1"
    local label="$2"
    awk -v label="$label" -F: '$1 ~ label { gsub(/^[ \t]+/, "", $2); print $2; exit }' "$file"
}

parse_cpu_from_top() {
    local file="$1"
    awk -v app_id="$APP_ID" '
        index($0, app_id) {
            for (i = 1; i <= NF; i++) {
                if ($i ~ /%$/) {
                    value = $i
                    gsub(/%/, "", value)
                    if (value ~ /^[0-9.]+$/) {
                        print value
                        exit
                    }
                }
            }
            for (i = 1; i < NF; i++) {
                if ($i ~ /^[RSDZTIX]$/ && $(i + 1) ~ /^[0-9.]+$/) {
                    print $(i + 1)
                    exit
                }
            }
        }
    ' "$file"
}

if [[ -z "$OUT_DIR" ]]; then
    OUT_DIR="$ROOT_DIR/build/diagnostics/android-observe-$(date +%Y%m%d-%H%M%S)"
fi

select_device
mkdir -p "$OUT_DIR/samples" "$OUT_DIR/top" "$OUT_DIR/meminfo"

METRICS_FILE="$OUT_DIR/metrics.csv"
LOG_FILE="$OUT_DIR/app-logcat.log"
SUMMARY_FILE="$OUT_DIR/summary.txt"
BATTERY_START_FILE="$OUT_DIR/battery-start.txt"
BATTERY_END_FILE="$OUT_DIR/battery-end.txt"

echo "Observing $APP_ID on Android device $SERIAL"
echo "Output: $OUT_DIR"

if [[ "$CLEAR_LOGCAT" -eq 1 ]]; then
    adb_device logcat -c || true
fi

if [[ "$RESET_BATTERYSTATS" -eq 1 ]]; then
    adb_device shell dumpsys batterystats --reset >/dev/null 2>&1 || true
fi

device_shell dumpsys battery > "$BATTERY_START_FILE" || true
adb_device shell dumpsys batterystats --charged "$APP_ID" > "$OUT_DIR/batterystats-start.txt" 2>&1 || true

if [[ "$LAUNCH_APP" -eq 1 ]]; then
    adb_device shell monkey -p "$APP_ID" -c android.intent.category.LAUNCHER 1 >/dev/null
fi

PID=""
wait_for_pid() {
    local attempts=30
    while [[ -z "$PID" && "$attempts" -gt 0 ]]; do
        PID="$(device_shell pidof "$APP_ID" | awk '{ print $1; exit }' || true)"
        if [[ -n "$PID" ]]; then
            return 0
        fi
        attempts=$((attempts - 1))
        sleep 1
    done
    if [[ -z "$PID" ]]; then
        echo "Could not find a running process for $APP_ID." >&2
        echo "Start the app or pass --launch." >&2
        exit 1
    fi
}

wait_for_pid
echo "App PID: $PID"
APP_USER="$(device_shell ps -p "$PID" -o USER= 2>/dev/null | awk '{ print $1; exit }' || true)"
BATTERYSTATS_UID="${APP_USER/_/}"

LOG_PID=""
STOP_REQUESTED=0
if [[ "$CAPTURE_LOGS" -eq 1 ]]; then
    adb_device logcat --pid "$PID" -v time '*:V' \
        > "$LOG_FILE" 2>&1 &
    LOG_PID="$!"
else
    : > "$LOG_FILE"
fi

cleanup() {
    if [[ -n "$LOG_PID" ]]; then
        kill "$LOG_PID" >/dev/null 2>&1 || true
        wait "$LOG_PID" >/dev/null 2>&1 || true
    fi
}
request_stop() {
    STOP_REQUESTED=1
}
trap request_stop INT TERM
trap cleanup EXIT

echo "timestamp_epoch,elapsed_seconds,serial,pid,cpu_percent,pss_kb,rss_kb,swap_pss_kb,battery_level,battery_temp_tenths_c,battery_voltage_mv,battery_current_now_ua" > "$METRICS_FILE"

START_EPOCH="$(date +%s)"
END_EPOCH=$((START_EPOCH + DURATION_SECONDS))
LAST_SAMPLE_EPOCH=0
LAST_MEMORY_EPOCH=0
LAST_PSS=""
LAST_RSS=""
LAST_SWAP_PSS=""

while [[ "$STOP_REQUESTED" -eq 0 && "$(date +%s)" -lt "$END_EPOCH" ]]; do
    if [[ -z "$(device_shell pidof "$APP_ID" | awk '{ print $1; exit }' || true)" ]]; then
        echo "Process $APP_ID exited before observation completed." | tee -a "$SUMMARY_FILE"
        break
    fi

    NOW_EPOCH="$(date +%s)"
    ELAPSED=$((NOW_EPOCH - START_EPOCH))
    TOP_FILE="$OUT_DIR/top/top-$NOW_EPOCH.txt"
    MEM_FILE="$OUT_DIR/meminfo/meminfo-$NOW_EPOCH.txt"
    BATTERY_FILE="$OUT_DIR/battery-$NOW_EPOCH.txt"

    adb_device shell top -b -n 1 -p "$PID" > "$TOP_FILE" 2>&1 || true
    device_shell dumpsys battery > "$BATTERY_FILE" || true

    CPU="$(parse_cpu_from_top "$TOP_FILE")"
    if [[ "$MEMORY_INTERVAL_SECONDS" -gt 0 ]] &&
        [[ $((NOW_EPOCH - LAST_MEMORY_EPOCH)) -ge "$MEMORY_INTERVAL_SECONDS" ]]; then
        LAST_MEMORY_EPOCH="$NOW_EPOCH"
        adb_device shell dumpsys meminfo "$APP_ID" > "$MEM_FILE" 2>&1 || true
        LAST_PSS="$(extract_meminfo_value "$MEM_FILE" "TOTAL PSS")"
        LAST_RSS="$(extract_meminfo_value "$MEM_FILE" "TOTAL RSS")"
        LAST_SWAP_PSS="$(extract_meminfo_value "$MEM_FILE" "TOTAL SWAP PSS")"
        if [[ -z "$LAST_PSS" ]]; then
            LAST_PSS="$(extract_meminfo_value "$MEM_FILE" "TOTAL_PSS_ROW")"
        fi
        if [[ -z "$LAST_RSS" ]]; then
            LAST_RSS="$(extract_meminfo_value "$MEM_FILE" "TOTAL_RSS_ROW")"
        fi
        if [[ -z "$LAST_SWAP_PSS" ]]; then
            LAST_SWAP_PSS="$(extract_meminfo_value "$MEM_FILE" "TOTAL_SWAP_PSS_ROW")"
        fi
    fi
    BATTERY_LEVEL="$(extract_battery_value "$BATTERY_FILE" "level")"
    BATTERY_TEMP="$(extract_battery_value "$BATTERY_FILE" "temperature")"
    BATTERY_VOLTAGE="$(extract_battery_value "$BATTERY_FILE" "voltage")"
    BATTERY_CURRENT="$(device_shell cat /sys/class/power_supply/battery/current_now 2>/dev/null | awk '{ print $1; exit }' || true)"

    echo "$NOW_EPOCH,$ELAPSED,$SERIAL,$PID,${CPU:-},${LAST_PSS:-},${LAST_RSS:-},${LAST_SWAP_PSS:-},${BATTERY_LEVEL:-},${BATTERY_TEMP:-},${BATTERY_VOLTAGE:-},${BATTERY_CURRENT:-}" >> "$METRICS_FILE"

    CPU_INT="${CPU%.*}"
    if [[ "${CPU_INT:-}" =~ ^[0-9]+$ ]] &&
        [[ "$CPU_INT" -ge "$SAMPLE_THRESHOLD_CPU" ]] &&
        [[ $((NOW_EPOCH - LAST_SAMPLE_EPOCH)) -ge 30 ]]; then
        LAST_SAMPLE_EPOCH="$NOW_EPOCH"
        adb_device shell top -H -b -n 1 -p "$PID" > "$OUT_DIR/samples/top-threads-$NOW_EPOCH.txt" 2>&1 || true
        if [[ "$MEMORY_INTERVAL_SECONDS" -gt 0 ]]; then
            adb_device shell dumpsys meminfo "$APP_ID" > "$OUT_DIR/samples/meminfo-$NOW_EPOCH.txt" 2>&1 || true
        fi
        adb_device shell dumpsys cpuinfo > "$OUT_DIR/samples/cpuinfo-$NOW_EPOCH.txt" 2>&1 || true
    fi

    sleep "$INTERVAL_SECONDS" || true
done

device_shell dumpsys battery > "$BATTERY_END_FILE" || true
adb_device shell dumpsys batterystats --charged "$APP_ID" > "$OUT_DIR/batterystats-end.txt" 2>&1 || true

awk -F, '
    NR == 1 { next }
    {
        count += 1
        if ($5 != "") {
            cpu_count += 1
            cpu += $5
            if ($5 > max_cpu) max_cpu = $5
        }
        if ($6 != "") {
            pss_count += 1
            pss += $6
            if ($6 > max_pss) max_pss = $6
        }
        if ($7 != "") {
            rss_count += 1
            rss += $7
            if ($7 > max_rss) max_rss = $7
        }
    }
    END {
        if (count == 0) {
            print "No metrics samples collected."
            exit
        }
        printf "Samples: %d\n", count
        if (cpu_count > 0) {
            printf "Average CPU: %.2f%%\n", cpu / cpu_count
            printf "Max CPU: %.2f%%\n", max_cpu
        } else {
            print "Average CPU: unavailable"
            print "Max CPU: unavailable"
        }
        if (pss_count > 0) {
            printf "Average PSS: %.0f KB\n", pss / pss_count
            printf "Max PSS: %.0f KB\n", max_pss
        } else {
            print "Average PSS: unavailable"
            print "Max PSS: unavailable"
        }
        if (rss_count > 0) {
            printf "Average RSS: %.0f KB\n", rss / rss_count
            printf "Max RSS: %.0f KB\n", max_rss
        } else {
            print "Average RSS: unavailable"
            print "Max RSS: unavailable"
        }
    }
' "$METRICS_FILE" | tee "$SUMMARY_FILE"

{
    echo
    echo "Battery start:"
    awk -F: '/level|temperature|voltage|status|AC powered|USB powered|Wireless powered/ { gsub(/^[ \t]+/, "", $2); print $1 ": " $2 }' "$BATTERY_START_FILE"
    echo
    echo "Battery end:"
    awk -F: '/level|temperature|voltage|status|AC powered|USB powered|Wireless powered/ { gsub(/^[ \t]+/, "", $2); print $1 ": " $2 }' "$BATTERY_END_FILE"
    if [[ -n "$BATTERYSTATS_UID" ]]; then
        echo
        echo "Batterystats estimated app use:"
        awk -v uid="UID $BATTERYSTATS_UID:" 'index($0, uid) { sub(/^[ \t]+/, ""); print; found = 1; exit } END { if (!found) print "No UID estimate found for " uid }' "$OUT_DIR/batterystats-end.txt"
    fi
} >> "$SUMMARY_FILE"

echo
echo "Metrics: $METRICS_FILE"
echo "Logs: $LOG_FILE"
echo "Battery: $BATTERY_START_FILE -> $BATTERY_END_FILE"
echo "Summary: $SUMMARY_FILE"

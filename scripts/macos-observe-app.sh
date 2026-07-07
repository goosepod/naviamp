#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_PATH="$ROOT_DIR/build/local-test/Naviamp.app"
PROCESS_MATCH="$APP_PATH/Contents/MacOS/Naviamp"
DURATION_SECONDS=180
INTERVAL_SECONDS=2
OUT_DIR=""
OPEN_APP=0
CAPTURE_LOGS=1
SAMPLE_THRESHOLD_CPU=80
SAMPLE_SECONDS=5

usage() {
    cat <<EOF
Usage: $(basename "$0") [options]

Observe the local macOS Naviamp app while you use it manually.

Options:
  --open                 Open build/local-test/Naviamp.app before observing.
  --app PATH             App bundle path. Defaults to build/local-test/Naviamp.app.
  --pid PID              Attach to a specific running process.
  --duration SECONDS     Observation duration. Defaults to $DURATION_SECONDS.
  --interval SECONDS     Metrics interval. Defaults to $INTERVAL_SECONDS.
  --out DIR              Output directory. Defaults to build/diagnostics/macos-observe-<timestamp>.
  --sample-threshold N   Run a stack sample when CPU is at least N percent. Defaults to $SAMPLE_THRESHOLD_CPU.
  --sample-seconds N     Duration for each stack sample. Defaults to $SAMPLE_SECONDS.
  --no-logs              Do not run macOS unified-log capture.
  --help                 Show this help.

Outputs:
  metrics.csv            Timestamped CPU, memory, RSS, VSZ, and elapsed time samples.
  app-warnings.log       macOS unified-log warning/error lines for the Naviamp process.
  samples/*.txt          Stack samples captured when CPU crosses the threshold.
  summary.txt            Quick average/max CPU and memory summary.
EOF
}

PID=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --open)
            OPEN_APP=1
            shift
            ;;
        --app)
            APP_PATH="$2"
            PROCESS_MATCH="$APP_PATH/Contents/MacOS/Naviamp"
            shift 2
            ;;
        --pid)
            PID="$2"
            shift 2
            ;;
        --duration)
            DURATION_SECONDS="$2"
            shift 2
            ;;
        --interval)
            INTERVAL_SECONDS="$2"
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
        --sample-seconds)
            SAMPLE_SECONDS="$2"
            shift 2
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

if [[ -z "$OUT_DIR" ]]; then
    OUT_DIR="$ROOT_DIR/build/diagnostics/macos-observe-$(date +%Y%m%d-%H%M%S)"
fi

mkdir -p "$OUT_DIR/samples"
METRICS_FILE="$OUT_DIR/metrics.csv"
LOG_FILE="$OUT_DIR/app-warnings.log"
SUMMARY_FILE="$OUT_DIR/summary.txt"

if [[ "$OPEN_APP" -eq 1 ]]; then
    if [[ ! -d "$APP_PATH" ]]; then
        echo "App bundle not found: $APP_PATH" >&2
        echo "Build it first with: make macos-test" >&2
        exit 1
    fi
    open -n "$APP_PATH"
fi

find_pid() {
    ps -axo pid=,command= |
        awk -v needle="$PROCESS_MATCH" 'index($0, needle) { print $1; exit }'
}

wait_for_pid() {
    local attempts=30
    while [[ -z "$PID" && "$attempts" -gt 0 ]]; do
        PID="$(find_pid || true)"
        if [[ -n "$PID" ]]; then
            return 0
        fi
        attempts=$((attempts - 1))
        sleep 1
    done
    if [[ -z "$PID" ]]; then
        echo "Could not find a Naviamp process matching: $PROCESS_MATCH" >&2
        echo "Start the app or pass --open / --pid." >&2
        exit 1
    fi
}

wait_for_pid

echo "Observing Naviamp PID $PID"
echo "Output: $OUT_DIR"
echo "timestamp_epoch,elapsed_seconds,pid,cpu_percent,mem_percent,rss_kb,vsz_kb,process_elapsed" > "$METRICS_FILE"

LOG_PID=""
STOP_REQUESTED=0
if [[ "$CAPTURE_LOGS" -eq 1 ]] && command -v log >/dev/null 2>&1; then
    log stream \
        --style compact \
        --level info \
        --predicate 'process == "Naviamp" AND (eventMessage CONTAINS[c] "warn" OR eventMessage CONTAINS[c] "error" OR eventMessage CONTAINS[c] "exception" OR eventMessage CONTAINS[c] "fatal")' \
        > "$LOG_FILE" 2>&1 &
    LOG_PID="$!"
else
    : > "$LOG_FILE"
fi

cleanup() {
    if [[ -n "$LOG_PID" ]]; then
        kill "$LOG_PID" >/dev/null 2>&1 || true
    fi
}
request_stop() {
    STOP_REQUESTED=1
}
trap request_stop INT TERM
trap cleanup EXIT

START_EPOCH="$(date +%s)"
END_EPOCH=$((START_EPOCH + DURATION_SECONDS))
LAST_SAMPLE_EPOCH=0

while [[ "$STOP_REQUESTED" -eq 0 && "$(date +%s)" -lt "$END_EPOCH" ]]; do
    if ! ps -p "$PID" >/dev/null 2>&1; then
        echo "Process $PID exited before observation completed." | tee -a "$SUMMARY_FILE"
        break
    fi

    NOW_EPOCH="$(date +%s)"
    ELAPSED=$((NOW_EPOCH - START_EPOCH))
    ROW="$(ps -p "$PID" -o pid=,pcpu=,pmem=,rss=,vsz=,etime= | awk '{ print $1 "," $2 "," $3 "," $4 "," $5 "," $6 }')"
    if [[ -n "$ROW" ]]; then
        echo "$NOW_EPOCH,$ELAPSED,$ROW" >> "$METRICS_FILE"
        CPU="$(echo "$ROW" | cut -d, -f2)"
        CPU_INT="${CPU%.*}"
        if [[ "$CPU_INT" =~ ^[0-9]+$ ]] &&
            [[ "$CPU_INT" -ge "$SAMPLE_THRESHOLD_CPU" ]] &&
            [[ $((NOW_EPOCH - LAST_SAMPLE_EPOCH)) -ge 30 ]] &&
            command -v sample >/dev/null 2>&1; then
            LAST_SAMPLE_EPOCH="$NOW_EPOCH"
            sample "$PID" "$SAMPLE_SECONDS" -file "$OUT_DIR/samples/sample-$NOW_EPOCH.txt" >/dev/null 2>&1 || true
        fi
    fi
    sleep "$INTERVAL_SECONDS" || true
done

awk -F, '
    NR == 1 { next }
    {
        count += 1
        cpu += $4
        mem += $5
        rss += $6
        if ($4 > max_cpu) max_cpu = $4
        if ($5 > max_mem) max_mem = $5
        if ($6 > max_rss) max_rss = $6
    }
    END {
        if (count == 0) {
            print "No metrics samples collected."
            exit
        }
        printf "Samples: %d\n", count
        printf "Average CPU: %.2f%%\n", cpu / count
        printf "Max CPU: %.2f%%\n", max_cpu
        printf "Average memory: %.2f%%\n", mem / count
        printf "Max memory: %.2f%%\n", max_mem
        printf "Average RSS: %.0f KB\n", rss / count
        printf "Max RSS: %.0f KB\n", max_rss
    }
' "$METRICS_FILE" | tee "$SUMMARY_FILE"

echo
echo "Metrics: $METRICS_FILE"
echo "Warnings/errors: $LOG_FILE"
echo "Summary: $SUMMARY_FILE"

#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
readonly DEFAULT_PACKAGE="org.nigao.zhihuLite.perfetto"
readonly CONFIG_FILE="$PROJECT_DIR/perfetto/business-methods.pbtxt"

package_name="$DEFAULT_PACKAGE"
capture_mode="manual"
duration_seconds=""
manual_option_seen=false
duration_option_seen=false
output_file=""
serial="${ANDROID_SERIAL:-}"
install_app=false
launch_app=false
remote_perfetto_pid=""
generated_config=""
device_trace=""

usage() {
    cat <<'EOF'
Usage:
  ./scripts/capture-perfetto.sh [options]

Options:
  --install              Build and install the dedicated perfetto variant first.
  --launch               Force-stop and launch the app after tracing starts.
  --manual               Wait for Enter to stop and save the trace (default).
  -d, --duration SEC     Stop automatically after SEC seconds (minimum: 2).
  -o, --output FILE      Host output path. Relative paths are resolved from the repo root.
  -p, --package NAME     Package to enable for app Trace events.
                         Default: org.nigao.zhihuLite.perfetto
  -s, --serial SERIAL    ADB device serial. ANDROID_SERIAL is also supported.
  -h, --help             Show this help.

Examples:
  ./scripts/capture-perfetto.sh --install --launch
  ./scripts/capture-perfetto.sh --manual --launch
  ./scripts/capture-perfetto.sh --duration 20 --output captures/feed.perfetto-trace
EOF
}

fail() {
    echo "Error: $*" >&2
    exit 1
}

cleanup() {
    if [[ -n "$remote_perfetto_pid" ]] && [[ -n "${serial:-}" ]]; then
        adb -s "$serial" shell kill -TERM "$remote_perfetto_pid" >/dev/null 2>&1 || true
    fi
    if [[ -n "$generated_config" ]]; then
        rm -f "$generated_config"
    fi
}

trap cleanup EXIT INT TERM

wait_for_perfetto_exit() {
    local attempts=0

    while (( attempts < 300 )); do
        if ! "${adb_command[@]}" shell kill -0 "$remote_perfetto_pid" >/dev/null 2>&1; then
            return 0
        fi
        sleep 0.1
        attempts=$((attempts + 1))
    done

    return 1
}

stop_perfetto() {
    [[ -n "$remote_perfetto_pid" ]] || return 0

    local pid="$remote_perfetto_pid"
    echo "Stopping Perfetto (pid $pid) and flushing the trace..."
    "${adb_command[@]}" shell kill -TERM "$pid" >/dev/null 2>&1 || true

    if ! wait_for_perfetto_exit; then
        fail "Perfetto did not stop within 30 seconds"
    fi
    remote_perfetto_pid=""
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --install)
            install_app=true
            shift
            ;;
        --launch)
            launch_app=true
            shift
            ;;
        --manual)
            [[ "$duration_option_seen" == false ]] \
                || fail "--manual cannot be combined with --duration"
            capture_mode="manual"
            manual_option_seen=true
            shift
            ;;
        -d|--duration)
            [[ $# -ge 2 ]] || fail "$1 requires a value"
            [[ "$manual_option_seen" == false ]] \
                || fail "--duration cannot be combined with --manual"
            capture_mode="timed"
            duration_seconds="$2"
            duration_option_seen=true
            shift 2
            ;;
        -o|--output)
            [[ $# -ge 2 ]] || fail "$1 requires a value"
            output_file="$2"
            shift 2
            ;;
        -p|--package)
            [[ $# -ge 2 ]] || fail "$1 requires a value"
            package_name="$2"
            shift 2
            ;;
        -s|--serial)
            [[ $# -ge 2 ]] || fail "$1 requires a value"
            serial="$2"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            fail "unknown option: $1"
            ;;
    esac
done

command -v adb >/dev/null 2>&1 || fail "adb is not available in PATH"
[[ -f "$CONFIG_FILE" ]] || fail "missing Perfetto config: $CONFIG_FILE"
if [[ "$capture_mode" == "timed" ]]; then
    [[ "$duration_seconds" =~ ^[0-9]+$ ]] || fail "duration must be an integer"
    (( duration_seconds >= 2 )) || fail "duration must be at least 2 seconds"
fi
[[ "$package_name" =~ ^[A-Za-z0-9._]+$ ]] || fail "invalid package name: $package_name"

if [[ -z "$serial" ]]; then
    devices="$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')"
    device_count="$(printf '%s\n' "$devices" | sed '/^$/d' | wc -l | tr -d ' ')"
    if [[ "$device_count" == "0" ]]; then
        fail "no authorized Android device is connected"
    fi
    if [[ "$device_count" != "1" ]]; then
        fail "multiple devices are connected; pass --serial or set ANDROID_SERIAL"
    fi
    serial="$devices"
fi

adb_command=(adb -s "$serial")
"${adb_command[@]}" get-state >/dev/null 2>&1 || fail "device is not available: $serial"

sdk_level="$("${adb_command[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
[[ "$sdk_level" =~ ^[0-9]+$ ]] || fail "could not read the device API level"
(( sdk_level >= 28 )) || fail "on-device Perfetto requires Android 9 / API 28 or newer"

if ! "${adb_command[@]}" shell perfetto --version >/dev/null 2>&1; then
    fail "the selected device does not expose the perfetto command"
fi

if [[ "$install_app" == true ]]; then
    if [[ "$package_name" != "$DEFAULT_PACKAGE" ]]; then
        fail "--install builds $DEFAULT_PACKAGE; remove --package or install the custom package yourself"
    fi
    echo "Building and installing the perfetto variant on $serial..."
    if ! (
        cd "$PROJECT_DIR"
        ANDROID_SERIAL="$serial" ./gradlew :app:installPerfetto
    ); then
        fail "installation failed; approve the device prompt or enable the OEM 'Install via USB' option, then retry"
    fi
fi

if ! "${adb_command[@]}" shell pm path "$package_name" 2>/dev/null \
    | tr -d '\r' \
    | grep -q '^package:'; then
    fail "$package_name is not installed; run again with --install"
fi

timestamp="$(date '+%Y%m%d-%H%M%S')"
if [[ -z "$output_file" ]]; then
    output_file="$PROJECT_DIR/captures/zhihulite-$timestamp.perfetto-trace"
elif [[ "$output_file" != /* ]]; then
    output_file="$PROJECT_DIR/$output_file"
fi
mkdir -p "$(dirname "$output_file")"

duration_ms=0
if [[ "$capture_mode" == "timed" ]]; then
    duration_ms=$((duration_seconds * 1000))
fi

generated_config="$(mktemp "${TMPDIR:-/tmp}/zhihulite-perfetto.XXXXXX")"
awk \
    -v capture_mode="$capture_mode" \
    -v duration_ms="$duration_ms" \
    -v package_name="$package_name" '
    /^[[:space:]]*duration_ms:/ {
        if (capture_mode == "timed") {
            print "duration_ms: " duration_ms
        }
        next
    }
    /^[[:space:]]*atrace_apps:/ {
        match($0, /^[[:space:]]*/)
        indentation = substr($0, 1, RLENGTH)
        print indentation "atrace_apps: \"" package_name "\""
        next
    }
    { print }
    END {
        if (capture_mode == "manual") {
            # Periodically drain the in-memory ring buffer into the output file.
            # This preserves the beginning of captures that run longer than the buffer.
            print ""
            print "write_into_file: true"
            print "file_write_period_ms: 1000"
            print "flush_period_ms: 5000"
        }
    }
' "$CONFIG_FILE" > "$generated_config"

device_trace="/data/misc/perfetto-traces/zhihulite-$timestamp.perfetto-trace"
"${adb_command[@]}" shell rm -f "$device_trace"

if [[ "$capture_mode" == "manual" ]]; then
    echo "Starting manual capture for $package_name on $serial..."
else
    echo "Recording $package_name for ${duration_seconds}s on $serial..."
fi

if ! perfetto_start_output="$(
    "${adb_command[@]}" shell perfetto \
        --background-wait \
        --txt \
        -c - \
        -o "$device_trace" \
        < "$generated_config"
)"; then
    fail "Perfetto failed to start"
fi
remote_perfetto_pid="$(
    printf '%s\n' "$perfetto_start_output" \
        | tr -d '\r' \
        | awk '/^[0-9]+$/ { pid = $0 } END { print pid }'
)"
[[ "$remote_perfetto_pid" =~ ^[0-9]+$ ]] || fail "Perfetto did not return a background process id"

if [[ "$launch_app" == true ]]; then
    "${adb_command[@]}" shell am force-stop "$package_name"
    "${adb_command[@]}" shell monkey \
        -p "$package_name" \
        -c android.intent.category.LAUNCHER \
        1 >/dev/null
fi

if [[ "$capture_mode" == "manual" ]]; then
    echo
    echo "Perfetto is recording. Interact with the app now."
    echo "Press Enter to stop, flush, and save the trace."
    IFS= read -r _ || true
    stop_perfetto
else
    echo "Interact with the app now. Waiting for Perfetto to finish..."
    sleep "$duration_seconds"
    if ! wait_for_perfetto_exit; then
        fail "Perfetto did not finish within 30 seconds after the configured duration"
    fi
    remote_perfetto_pid=""
fi

"${adb_command[@]}" pull "$device_trace" "$output_file" >/dev/null
"${adb_command[@]}" shell rm -f "$device_trace"

trace_size="$(du -h "$output_file" | awk '{ print $1 }')"
echo
echo "Trace saved: $output_file ($trace_size)"
echo "Open https://perfetto.dev/#viewer and drag this file into the page."
echo "Search for BM: in the app process tracks to inspect business-method durations."

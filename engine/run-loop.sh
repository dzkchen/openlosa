#!/bin/sh

set -u

interval_seconds="${ENGINE_INTERVAL_SECONDS:-14400}"

case "${interval_seconds}" in
    ''|*[!0-9]*|0)
        echo "ENGINE_INTERVAL_SECONDS must be a positive integer" >&2
        exit 64
        ;;
esac

child_pid=

terminate() {
    trap - INT TERM
    if [ -n "${child_pid}" ]; then
        kill -TERM "${child_pid}" 2>/dev/null || true
        wait "${child_pid}" 2>/dev/null || true
    fi
    exit 0
}

trap terminate INT TERM

while true; do
    echo "Starting intern-engine cycle"
    python run.py all &
    child_pid=$!

    if wait "${child_pid}"; then
        echo "intern-engine cycle completed"
    else
        status=$?
        echo "intern-engine cycle failed with exit code ${status}; retrying after ${interval_seconds} seconds" >&2
    fi
    child_pid=

    sleep "${interval_seconds}" &
    child_pid=$!
    wait "${child_pid}" || true
    child_pid=
done

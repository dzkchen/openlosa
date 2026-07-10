#!/bin/sh

set -u

interval_seconds="${ENGINE_INTERVAL_SECONDS:-14400}"
export_dir="${ENGINE_EXPORT_DIR:-}"

case "${interval_seconds}" in
    ''|*[!0-9]*|0)
        echo "ENGINE_INTERVAL_SECONDS must be a positive integer" >&2
        exit 64
        ;;
esac

child_pid=

export_outputs() {
    [ -n "${export_dir}" ] || return 0
    mkdir -p "${export_dir}"

    export_status=0
    for filename in jobs.json health.json; do
        [ -f "data/${filename}" ] || continue
        temporary="${export_dir}/.${filename}.$$"
        if ! cp "data/${filename}" "${temporary}" \
            || ! mv "${temporary}" "${export_dir}/${filename}"; then
            rm -f "${temporary}"
            echo "Failed to export ${filename}" >&2
            export_status=1
        fi
    done
    return "${export_status}"
}

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
        if export_outputs; then
            echo "intern-engine cycle completed"
        else
            echo "intern-engine cycle completed but output export failed; retrying after ${interval_seconds} seconds" >&2
        fi
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

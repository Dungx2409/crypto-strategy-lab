#!/usr/bin/env bash
set -euo pipefail

api_url="${API_URL:-http://localhost:8080}"
cookie_file="${COOKIE_FILE:-/tmp/crypto-lab-soak-cookie.txt}"
hours="${SOAK_HOURS:-24}"
interval="${SOAK_SAMPLE_SECONDS:-300}"
report_file="${SOAK_REPORT_FILE:-}"
username="${SOAK_USERNAME:-}"
password="${SOAK_PASSWORD:-}"

schedule_id="${SCHEDULE_ID:?Set SCHEDULE_ID to an account-owned discovery schedule}"
samples=$((hours * 3600 / interval))

if [[ -n "$report_file" ]]; then
    : > "$report_file"
fi

login() {
    [[ -n "$username" && -n "$password" ]] || return 1
    body=$(jq -nc --arg username "$username" --arg password "$password" \
        '{username:$username,password:$password}')
    curl --fail --silent --show-error --cookie-jar "$cookie_file" \
        --header "Content-Type: application/json" --data "$body" \
        "$api_url/api/v1/auth/login" >/dev/null
}

read_schedule() {
    curl --fail --silent --show-error --cookie "$cookie_file" \
        "$api_url/api/v1/discovery-schedules/$schedule_id"
}

for ((sample = 1; sample <= samples; sample++)); do
    if ! body=$(read_schedule); then
        login
        body=$(read_schedule)
    fi
    status=$(jq -r '.status' <<<"$body")
    active=$(jq -r '.activeSearchRunId // "none"' <<<"$body")
    completed=$(jq -r '.completedRuns' <<<"$body")
    error=$(jq -r '.lastError // "none"' <<<"$body")
    line=$(printf '%s sample=%d status=%s active=%s completed=%s error=%s' \
        "$(date -u +%FT%TZ)" "$sample" "$status" "$active" "$completed" "$error")
    printf '%s\n' "$line"
    if [[ -n "$report_file" ]]; then
        printf '%s\n' "$line" >> "$report_file"
    fi
    [[ "$status" == "ACTIVE" ]]
    sleep "$interval"
done

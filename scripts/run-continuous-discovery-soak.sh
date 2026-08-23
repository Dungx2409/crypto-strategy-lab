#!/usr/bin/env bash
set -euo pipefail

api_url="${API_URL:-http://localhost:8080}"
cookie_file="${COOKIE_FILE:-/tmp/crypto-lab-soak-cookie.txt}"
hours="${SOAK_HOURS:-24}"
interval="${SOAK_SAMPLE_SECONDS:-300}"

schedule_id="${SCHEDULE_ID:?Set SCHEDULE_ID to an account-owned discovery schedule}"
samples=$((hours * 3600 / interval))

for ((sample = 1; sample <= samples; sample++)); do
    body=$(curl --fail --silent --show-error --cookie "$cookie_file" \
        "$api_url/api/v1/discovery-schedules/$schedule_id")
    status=$(jq -r '.status' <<<"$body")
    active=$(jq -r '.activeSearchRunId // "none"' <<<"$body")
    completed=$(jq -r '.completedRuns' <<<"$body")
    error=$(jq -r '.lastError // "none"' <<<"$body")
    printf '%s sample=%d status=%s active=%s completed=%s error=%s\n' \
        "$(date -u +%FT%TZ)" "$sample" "$status" "$active" "$completed" "$error"
    [[ "$status" == "ACTIVE" ]]
    sleep "$interval"
done

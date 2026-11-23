#!/usr/bin/env bash
# Retry only dependency-repository failures, not Kotlin, resource, or test failures.
set -euo pipefail

variant="${1:-}"
case "$variant" in
  Debug|Release) ;;
  *)
    echo "Usage: bash ci-assemble.sh Debug|Release" >&2
    exit 2
    ;;
esac

log="$(mktemp)"
trap 'rm -f "$log"' EXIT

# Preserve the actual Gradle exit status even though tee records the output.
set +e
bash ./gradlew --no-daemon ":app:assemble${variant}" 2>&1 | tee "$log"
status=${PIPESTATUS[0]}
set -e

if (( status == 0 )); then
  exit 0
fi

# The plugin portal/Maven repositories occasionally fail to resolve a published
# plugin on a clean GitHub runner. Do not retry actual source-code build failures.
if ! grep -Eqi \
  'Plugin \[id: .* was not found|could not resolve plugin artifact|Could not resolve |Could not (GET|HEAD) |Temporary failure in name resolution|Connection reset|Read timed out|Connect to .* failed|HTTP (429|502|503|504)|503 Service Unavailable|502 Bad Gateway' \
  "$log"; then
  exit "$status"
fi

echo "::warning::Gradle dependency resolution failed; retrying once after 10 seconds."
sleep 10
bash ./gradlew --no-daemon --refresh-dependencies ":app:assemble${variant}"

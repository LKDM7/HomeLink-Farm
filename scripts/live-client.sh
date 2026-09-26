#!/usr/bin/env bash
# Sends one command batch to a running live smoke client and waits until it is played.
# Start the client first: ./gradlew runClientSmoke -PsmokeScenario=live
# Usage: scripts/live-client.sh "reload" "hold 2" "camera front" "screenshot connector_front"
# Commands are documented in src/gametest/java/.../client/LiveSession.java.
set -euo pipefail
cd "$(dirname "$0")/.."
live=build/client-smoke/live
mkdir -p "$live"
# "reload" picks up the edited assets: copy them where the running client loads resources from.
for command in "$@"; do
    [ "$command" = reload ] && cp -r src/main/resources/assets build/resources/main/
done
rm -f "$live/ack.txt"
printf '%s\n' "$@" > "$live/commands.tmp"
mv "$live/commands.tmp" "$live/commands.txt"
for _ in $(seq 1 600); do
    if [ -f "$live/ack.txt" ]; then
        ack=$(cat "$live/ack.txt")
        echo "ack: $ack"
        case "$ack" in *failed*) exit 1 ;; esac
        exit 0
    fi
    sleep 0.5
done
echo "no answer from the live client after 5 minutes" >&2
exit 1

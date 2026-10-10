#!/usr/bin/env bash
# Run a command with a private virtual display, then tear the display down.
#   scripts/headless.sh ./gradlew runClientGameTest
#   scripts/headless.sh ./gradlew runClient        # poke at the game; screenshots via F2
# CI uses ./gradlew runProductionClientGameTest, which manages Xvfb itself.
set -euo pipefail
PATH="$HOME/.local/bin:$PATH"

if ! command -v xvfb-run >/dev/null 2>&1; then
	echo "xvfb-run not found; run scripts/setup-headless-host.sh first" >&2
	exit 2
fi

exec xvfb-run -a -s "-screen 0 ${HEADLESS_RESOLUTION:-1920x1080}x24" "$@"

#!/usr/bin/env bash
# A dedicated Fabric server on a copy of a world, driven from the shell: for what only a real world
# shows (a save that won't load, cards in a case's slots, chunk loading). The world is copied, never
# used in place; the server runs in fabric/run (git-ignored), its console fed through a fifo.
#   scripts/devserver.sh start <world folder> [chunks]   copy it in, start, force-load chunks around 0,0 (default 2: a 5x5; at most 7)
#   scripts/devserver.sh cmd <server command...>         run a console command (forceload, say, stop...)
#   scripts/devserver.sh log [lines]                     the server's log, its end
#   scripts/devserver.sh stop                            save and stop
#   scripts/devserver.sh dump                            the server thread's stack (a hang)
# A computer's disks are folders under fabric/run/world/modularcomputers/disks/<id>: put a script on
# one before start, read what it wrote after. An empty server keeps ticking (pause-when-empty off).
set -euo pipefail
cd "$(dirname "$0")/.."
RUN=fabric/run
FIFO=$RUN/devserver.in
LOG=$RUN/devserver.log
START_SECONDS=300 # how long the first start may take (Gradle compiles first)
MAX_CHUNKS=7 # the widest force-load around 0,0: the game takes at most 256 chunks in one (15x15)

wait_for() {
	for _ in $(seq 1 "$2"); do
		grep -q "$1" "$LOG" 2>/dev/null && return 0
		sleep 1
	done
	return 1
}

property() { # key value: set in server.properties
	touch "$RUN/server.properties"
	if grep -q "^$1=" "$RUN/server.properties"; then
		sed -i "s/^$1=.*/$1=$2/" "$RUN/server.properties"
	else
		echo "$1=$2" >> "$RUN/server.properties"
	fi
}

case "${1:-}" in
start)
	world=${2:?world folder}
	chunks=${3:-2}
	if [ "$chunks" -gt "$MAX_CHUNKS" ]; then
		chunks=$MAX_CHUNKS
	fi
	mkdir -p "$RUN"
	rm -rf "$RUN/world"
	cp -a "$world" "$RUN/world"
	rm -f "$RUN/world/session.lock"
	echo "eula=true" > "$RUN/eula.txt"
	property level-name world
	property online-mode false
	property pause-when-empty-seconds -1
	rm -f "$FIFO" "$LOG"
	mkfifo "$FIFO"
	nohup bash -c "exec 3<>'$FIFO'; exec ./gradlew :fabric:runServer --console=plain <&3" > "$LOG" 2>&1 &
	if ! wait_for "Done (" "$START_SECONDS"; then
		echo "the server didn't start; see $LOG" >&2
		exit 1
	fi
	echo "forceload add $((-16 * chunks)) $((-16 * chunks)) $((16 * chunks)) $((16 * chunks))" > "$FIFO"
	grep "Done (" "$LOG"
	;;
cmd)
	shift
	echo "$*" > "$FIFO"
	;;
log)
	tail -n "${2:-30}" "$LOG"
	;;
stop)
	echo stop > "$FIFO"
	wait_for "All dimensions are saved\|BUILD" 60 || true
	tail -n 3 "$LOG"
	;;
dump)
	pid=$(pgrep -f "net.fabricmc.devlaunchinjector.Main" | head -1)
	"$(dirname "$(readlink "/proc/$pid/exe")")/jstack" "$pid" | sed -n '/"Server thread"/,/^$/p' # (the server's own JDK's: Gradle provisions it)
	;;
*)
	sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//'
	exit 2
	;;
esac

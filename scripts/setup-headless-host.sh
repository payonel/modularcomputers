#!/usr/bin/env bash
# One-time host setup so the full client can be launched by tests on a machine with no monitor.
#
# What the tests need:
#   - an X server with no display hardware (Xvfb)
#   - an OpenGL 3.3+ core driver; Mesa llvmpipe (software) is enough and needs no GPU
# No window manager is needed: client game tests drive the game programmatically.
#
# The JDK is NOT installed here -- Gradle provisions JDK 25 itself (gradle/gradle-daemon-jvm.properties).
set -euo pipefail

have() { command -v "$1" >/dev/null 2>&1; }

if ! have Xvfb; then
	if sudo -n true 2>/dev/null; then
		sudo apt-get install -y xvfb xauth x11-xkb-utils libgl1-mesa-dri mesa-utils
	else
		# No passwordless sudo: unpack the xvfb package into ~/.local (its dependencies are
		# common X libraries that are normally already present).
		echo "No passwordless sudo; installing Xvfb under ~/.local" >&2
		dest="$HOME/.local/opt/xvfb"
		mkdir -p "$dest" "$HOME/.local/bin"
		(cd "$dest" && apt-get download xvfb && dpkg -x xvfb_*.deb root)
		ln -sf "$dest/root/usr/bin/Xvfb" "$HOME/.local/bin/Xvfb"
		ln -sf "$dest/root/usr/bin/xvfb-run" "$HOME/.local/bin/xvfb-run"
		case ":$PATH:" in *":$HOME/.local/bin:"*) ;; *) echo "NOTE: add ~/.local/bin to PATH" >&2 ;; esac
	fi
fi

echo "Checking OpenGL under Xvfb..."
PATH="$HOME/.local/bin:$PATH" xvfb-run -a -s '-screen 0 1280x720x24' glxinfo -B \
	| grep -E 'OpenGL renderer|OpenGL core profile version'

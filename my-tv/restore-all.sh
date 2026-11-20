#!/usr/bin/env bash
# Undo the whole cleanup on the TCL 55" (G03_4K_US_NF): re-enable every batch, newest first, give Home back
# to Google TV's launcherx and put the original screensaver back. Needs netmy-tv/USB debugging on again.
# Usage (from the repo root): TV_HOST=<tv ip> my-tv/restore-all.sh
set -euo pipefail
cd "$(dirname "$0")/.."
: "${TV_HOST:?set TV_HOST=<tv ip>}"
for b in b7-dreamx b6-setupwraith b5-voice b4-streaming b3-extras b2-ads b1-telemetry; do
  tools/restore.sh "my-tv/$b.txt"
done
tools/venv/bin/python -I tools/tv.py 'pm enable --user 0 com.google.android.apps.tv.launcherx'
tools/venv/bin/python -I tools/tv.py "cmd package set-home-activity --user 0 com.google.android.apps.tv.launcherx/.home.HomeActivity"
tools/venv/bin/python -I tools/tv.py "settings put secure screensaver_components $(cat my-tv/screensaver-before.txt)"
echo "Restored. Optionally remove the new launcher: tools/tv.py 'pm uninstall com.gathin.tvhome'"

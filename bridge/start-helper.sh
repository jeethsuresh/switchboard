#!/bin/bash
set -euo pipefail
task_serial="${1:?Usage: bridge/start-helper.sh TV_IP:5555}"
task_root="$(cd "$(dirname "$0")" && pwd)"
task_adb="${ADB:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}/platform-tools/adb}"
task_package="$("$task_adb" -s "$task_serial" shell pm list packages -U ca.jeeth.switchboard)"
if [[ "$task_package" =~ uid:([0-9]+) ]]; then task_uid="${BASH_REMATCH[1]}"; else echo 'Switchboard is not installed.' >&2; exit 1; fi
"$task_adb" -s "$task_serial" push "$task_root/switchboard-shell.jar" /data/local/tmp/switchboard-shell.jar
"$task_adb" -s "$task_serial" shell "run-as ca.jeeth.switchboard sh -c 'mkdir -p files; umask 077; if [ ! -s files/bridge-key ]; then head -c 32 /dev/urandom >files/bridge-key; fi'"
task_old_pid="$("$task_adb" -s "$task_serial" shell pidof switchboard-shell || true)"
if [ -n "$task_old_pid" ]; then
    "$task_adb" -s "$task_serial" shell "CLASSPATH=/data/local/tmp/switchboard-shell.jar app_process /system/bin ca.jeeth.switchboard.bridge.ShellBridge --request '{\"command\":\"restoreWindows\"}'" || true
    "$task_adb" -s "$task_serial" shell 'task_pid=$(pidof switchboard-shell); if [ -n "$task_pid" ]; then kill "$task_pid"; fi'
fi
"$task_adb" -s "$task_serial" shell "CLASSPATH=/data/local/tmp/switchboard-shell.jar nohup app_process /system/bin --nice-name=switchboard-shell ca.jeeth.switchboard.bridge.ShellBridge $task_uid >/data/local/tmp/switchboard-shell.log 2>&1 </dev/null &"
sleep 1
task_running="$("$task_adb" -s "$task_serial" shell pidof switchboard-shell)"
if [ -z "$task_running" ]; then "$task_adb" -s "$task_serial" shell cat /data/local/tmp/switchboard-shell.log; exit 1; fi
task_health="$("$task_adb" -s "$task_serial" shell "CLASSPATH=/data/local/tmp/switchboard-shell.jar app_process /system/bin ca.jeeth.switchboard.bridge.ShellBridge --request '{\"command\":\"status\"}'")"
if [[ "$task_health" != *'"ok":true'* ]]; then echo "Helper health check failed: $task_health" >&2; exit 1; fi
echo "Helper authenticated and responding: $task_health"
echo 'Check status in App-window experiment → App-window setup / helper status. Restart this helper after a TV reboot or launcher update.'

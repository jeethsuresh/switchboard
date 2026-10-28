#!/bin/bash
set -euo pipefail
task_root="$(cd "$(dirname "$0")" && pwd)"
task_sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
task_jdk="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
task_build="$task_root/build"
mkdir -p "$task_build/classes" "$task_build/dex"
"$task_jdk/bin/javac" -source 8 -target 8 -bootclasspath "$task_sdk/platforms/android-36/android.jar" -d "$task_build/classes" "$task_root/ShellBridge.java"
JAVA_HOME="$task_jdk" "$task_sdk/build-tools/35.0.0/d8" --min-api 26 --lib "$task_sdk/platforms/android-36/android.jar" --output "$task_build/dex" "$task_build/classes/ca/jeeth/switchboard/bridge/ShellBridge.class"
"$task_jdk/bin/jar" cf "$task_root/switchboard-shell.jar" -C "$task_build/dex" classes.dex
echo "Built $task_root/switchboard-shell.jar"

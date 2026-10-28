#!/bin/bash
set -euo pipefail
task_root="$(cd "$(dirname "$0")/.." && pwd)"
task_jdk="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
task_build="$(mktemp -d)"
trap 'rm -rf "$task_build"' EXIT
"$task_jdk/bin/javac" -d "$task_build" "$task_root/app/src/main/java/ca/jeeth/switchboard/HoldGate.java" "$task_root/app/src/main/java/ca/jeeth/switchboard/HomeNavigation.java" "$task_root/app/src/main/java/ca/jeeth/switchboard/WatchEntry.java" "$task_root/tests/HoldGateTest.java" "$task_root/tests/HomeNavigationTest.java" "$task_root/tests/WatchEntryTest.java"
"$task_jdk/bin/java" -cp "$task_build" ca.jeeth.switchboard.HoldGateTest
"$task_jdk/bin/java" -cp "$task_build" ca.jeeth.switchboard.HomeNavigationTest
"$task_jdk/bin/java" -cp "$task_build" ca.jeeth.switchboard.WatchEntryTest

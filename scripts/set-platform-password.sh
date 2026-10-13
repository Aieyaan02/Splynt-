#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
./mvnw -q dependency:build-classpath -Dmdep.outputFile=target/platform-password-classpath.txt
java --class-path "$(cat target/platform-password-classpath.txt)" scripts/SetPlatformPassword.java

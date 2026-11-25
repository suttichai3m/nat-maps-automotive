#!/bin/bash
set -e
cd "$(dirname "$0")"
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
bash gradlew --no-daemon :patches:generatePatchesList
bash gradlew --no-daemon buildAndroid
open patches/build/libs

#!/bin/bash
# Lint (warnings fail), unit tests and a debug build. Run before every commit.
set -eo pipefail
cd "$(dirname "$0")/.."
./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug -q

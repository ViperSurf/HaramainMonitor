#!/usr/bin/env bash
set -euo pipefail

echo "1/3 Naming rule check..."
if grep -rnEi "[c]hurch|[c]athedral" app/ README.md CHANGELOG.md docs/ fastlane/; then
  echo "FAIL: banned naming word found"
  exit 1
fi
echo "PASS: 0 banned-word occurrences"

echo "2/3 Host C++ DSP tests..."
g++ -std=c++17 -O2 -I app/src/main/cpp app/src/main/cpp/test/dsp_chain_test.cpp -o /tmp/dsp_test
/tmp/dsp_test

echo "3/3 JVM unit tests..."
./gradlew testReleaseUnitTest --no-daemon

echo "ALL LOCAL CHECKS PASSED"

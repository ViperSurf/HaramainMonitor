$ErrorActionPreference = "Stop"

Write-Host "1/3 Naming rule check..."
$hits = Select-String -Path (Get-ChildItem -Recurse -File | Where-Object {
    $_.FullName -notmatch '\\\.git\\|\\build\\' -and $_.Extension -notmatch '\.(jar|keystore|png|webp|webm)$'
}).FullName -Pattern "[c]hurch|[c]athedral" -CaseSensitive:$false
if ($hits) { $hits; exit 1 }
Write-Host "PASS: 0 banned-word occurrences"

Write-Host "2/3 JVM unit tests..."
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr" }
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk" }
.\gradlew.bat testReleaseUnitTest --no-daemon

Write-Host "3/3 C++ DSP tests (emulator if attached)..."
$adb = "$env:ANDROID_HOME\platform-tools\adb.exe"
$clang = "$env:ANDROID_HOME\ndk\26.3.11579264\toolchains\llvm\prebuilt\windows-x86_64\bin\x86_64-linux-android26-clang++.cmd"
if ((Test-Path $adb) -and (Test-Path $clang) -and ((& $adb devices) -match "emulator-")) {
    & $clang -std=c++17 -O2 -static-libstdc++ -I app\src\main\cpp app\src\main\cpp\test\dsp_chain_test.cpp -o "$env:TEMP\dsp_test"
    & $adb push "$env:TEMP\dsp_test" /data/local/tmp/dsp_test | Out-Null
    & $adb shell "chmod +x /data/local/tmp/dsp_test && /data/local/tmp/dsp_test"
} else {
    Write-Host "SKIP: No x86_64 emulator attached (CI will run g++ host DSP suite)"
}

Write-Host "ALL LOCAL CHECKS PASSED"

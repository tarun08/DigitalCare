# build-apk.ps1
# Runs the scheduler tests, builds the web app, syncs it into Android and copies the debug APK here.
#
# Usage: .\build-apk.ps1
#
# Every step is exit-code checked so a failed step can never fall through to copying a stale APK.

Write-Host "Running scheduler tests..." -ForegroundColor Cyan
Push-Location android
try {
    .\gradlew.bat test
    $testExit = $LASTEXITCODE
} finally {
    Pop-Location
}
if ($testExit -ne 0) { Write-Host "Tests failed - APK NOT built." -ForegroundColor Red; exit 1 }

Write-Host "Building web app..." -ForegroundColor Cyan
npm run build
if ($LASTEXITCODE -ne 0) { Write-Host "Web build failed, aborting." -ForegroundColor Red; exit 1 }

Write-Host "Syncing to Android..." -ForegroundColor Cyan
npx cap sync android
if ($LASTEXITCODE -ne 0) { Write-Host "Capacitor sync failed, aborting." -ForegroundColor Red; exit 1 }

Write-Host "Building APK..." -ForegroundColor Cyan
Push-Location android
try {
    .\gradlew.bat assembleDebug
    $gradleExit = $LASTEXITCODE
} finally {
    # Pop in a finally so a gradle crash can't strand the shell in android/.
    Pop-Location
}
if ($gradleExit -ne 0) { Write-Host "Gradle build failed, aborting." -ForegroundColor Red; exit 1 }

$src = "android\app\build\outputs\apk\debug\app-debug.apk"
$dst = "digitalcare.apk"

# Guard against copying a leftover from an earlier run if gradle reported success without output.
if (-not (Test-Path $src)) {
    Write-Host "Gradle reported success but $src does not exist. Not copying." -ForegroundColor Red
    exit 1
}

Copy-Item $src $dst -Force
Write-Host "Done! APK at: $((Get-Location).Path)\$dst" -ForegroundColor Green

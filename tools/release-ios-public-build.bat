@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem ============================================================
rem KKKeyboard iOS TestFlight public-build helper
rem
rem Purpose:
rem   - Keep the real KKKeyboard repository private.
rem   - Copy only the current HEAD snapshot into a dedicated build repo.
rem   - Make the build repo public only while GitHub Actions macOS runs.
rem   - Upload to TestFlight through the existing ios-testflight.yml workflow.
rem   - Return the build repo to private whether the build succeeds or fails.
rem
rem One-time requirements:
rem   1) Git for Windows
rem   2) GitHub CLI (gh) + "gh auth login"
rem   3) Run this BAT from inside the real KKKeyboard repository.
rem   4) The build repository needs the TestFlight secrets/variables once.
rem      The script can create KKKeyboard-build automatically, but GitHub
rem      does NOT allow copying secret values from another repository.
rem ============================================================

rem ---------- USER CONFIG ----------
set "BUILD_REPO=ghtnql/KKKeyboard-build"
set "WORKFLOW=ios-testflight.yml"
set "BUILD_BRANCH=main"

rem Normal production/TestFlight build.
rem Set to true only when intentionally building without App Group.
set "PREVIEW_WITHOUT_APP_GROUP=false"
rem ---------------------------------

title KKKeyboard iOS TestFlight Release

echo.
echo ============================================================
echo   KKKeyboard - iOS TestFlight release
echo ============================================================
echo.

where git >nul 2>nul
if errorlevel 1 (
    echo [ERROR] git was not found.
    echo Install Git for Windows first.
    goto :fail_no_cleanup
)

where gh >nul 2>nul
if errorlevel 1 (
    echo [ERROR] GitHub CLI ^(gh^) was not found.
    echo Install GitHub CLI first: winget install GitHub.cli
    goto :fail_no_cleanup
)

gh auth status >nul 2>nul
if errorlevel 1 (
    echo [ERROR] GitHub CLI is not logged in.
    echo Run: gh auth login
    goto :fail_no_cleanup
)

git rev-parse --is-inside-work-tree >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Run this BAT from inside the real KKKeyboard Git repository.
    goto :fail_no_cleanup
)

for /f "delims=" %%A in ('git rev-parse --show-toplevel') do set "SOURCE_ROOT=%%A"
for /f "delims=" %%A in ('git rev-parse HEAD') do set "SOURCE_SHA=%%A"

echo Source : %SOURCE_ROOT%
echo HEAD   : %SOURCE_SHA%
echo Build  : %BUILD_REPO%
echo.

rem Require a clean tree so the released snapshot is exactly HEAD.
for /f "delims=" %%A in ('git status --porcelain') do (
    echo [ERROR] Working tree is not clean.
    echo Commit or stash changes before releasing.
    goto :fail_no_cleanup
)

rem Verify the workflow exists in the source snapshot.
if not exist "%SOURCE_ROOT%\.github\workflows\%WORKFLOW%" (
    echo [ERROR] Missing workflow:
    echo %SOURCE_ROOT%\.github\workflows\%WORKFLOW%
    goto :fail_no_cleanup
)

rem Make sure GitHub CLI can provide credentials to git.
gh auth setup-git >nul 2>nul

rem ------------------------------------------------------------
rem Ensure the dedicated build repository exists.
rem ------------------------------------------------------------
gh repo view "%BUILD_REPO%" >nul 2>nul
if errorlevel 1 (
    echo [INFO] %BUILD_REPO% does not exist. Creating it as PRIVATE...
    gh repo create "%BUILD_REPO%" --private --disable-issues --disable-wiki
    if errorlevel 1 (
        echo [ERROR] Failed to create %BUILD_REPO%.
        goto :fail_no_cleanup
    )

    echo.
    echo ============================================================
    echo [ONE-TIME SETUP REQUIRED]
    echo The build repository was created successfully.
    echo.
    echo GitHub does not expose existing secret VALUES, so they cannot
    echo be copied automatically from the real KKKeyboard repository.
    echo Configure the TestFlight environment/repository secrets and
    echo variables in %BUILD_REPO% once, then run this BAT again.
    echo.
    echo Required secrets used by the current workflow:
    echo   APPSTORE_API_PRIVATE_KEY
    echo   IOS_DISTRIBUTION_P12_BASE64
    echo   IOS_DISTRIBUTION_P12_PASSWORD
    echo   IOS_APP_PROVISIONING_PROFILE_BASE64
    echo   IOS_EXTENSION_PROVISIONING_PROFILE_BASE64
    echo.
    echo Required variables used by the current workflow:
    echo   APPSTORE_ISSUER_ID
    echo   APPSTORE_API_KEY_ID
    echo   APPLE_TEAM_ID
    echo   IOS_APP_PROFILE_UUID
    echo   IOS_EXTENSION_PROFILE_UUID
    echo.
    echo The workflow uses the "testflight" environment.
    echo ============================================================
    echo.
    pause
    exit /b 2
)

rem ------------------------------------------------------------
rem Create a history-free snapshot of SOURCE_SHA.
rem ------------------------------------------------------------
set "STAMP=%RANDOM%_%RANDOM%_%RANDOM%"
set "TEMP_ROOT=%TEMP%\kkkeyboard-ios-build_%STAMP%"
set "ZIP_PATH=%TEMP_ROOT%.zip"
set "BUILD_DIR=%TEMP_ROOT%\snapshot"

if exist "%TEMP_ROOT%" rmdir /s /q "%TEMP_ROOT%"
if exist "%ZIP_PATH%" del /q "%ZIP_PATH%"
mkdir "%BUILD_DIR%" >nul 2>nul

echo [1/7] Creating clean snapshot of HEAD...
git -C "%SOURCE_ROOT%" archive --format=zip --output="%ZIP_PATH%" HEAD
if errorlevel 1 (
    echo [ERROR] git archive failed.
    goto :fail_no_cleanup
)

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "Expand-Archive -LiteralPath '%ZIP_PATH%' -DestinationPath '%BUILD_DIR%' -Force"
if errorlevel 1 (
    echo [ERROR] Failed to unpack snapshot.
    goto :fail_no_cleanup
)

rem Add traceability without exposing source history.
> "%BUILD_DIR%\BUILD_SOURCE.txt" (
    echo Source repository: ghtnql/KKKeyboard
    echo Source commit: %SOURCE_SHA%
    echo Snapshot generated by release-ios-public-build.bat
)

echo [2/7] Preparing single-commit build repository...
git -C "%BUILD_DIR%" init -b "%BUILD_BRANCH%" >nul
if errorlevel 1 goto :snapshot_fail

git -C "%BUILD_DIR%" config user.name "KKKeyboard Build Bot"
git -C "%BUILD_DIR%" config user.email "build@kkkeyboard.local"
git -C "%BUILD_DIR%" add -A
git -C "%BUILD_DIR%" commit -m "Build snapshot from %SOURCE_SHA%"
if errorlevel 1 goto :snapshot_fail

git -C "%BUILD_DIR%" remote add origin "https://github.com/%BUILD_REPO%.git"
git -C "%BUILD_DIR%" push --force --set-upstream origin "%BUILD_BRANCH%"
if errorlevel 1 (
    echo [ERROR] Failed to push snapshot to %BUILD_REPO%.
    goto :fail_no_cleanup
)

for /f "delims=" %%A in ('git -C "%BUILD_DIR%" rev-parse HEAD') do set "BUILD_SHA=%%A"
echo       Build snapshot SHA: %BUILD_SHA%

rem ------------------------------------------------------------
rem Public only for the actual macOS Actions run.
rem ------------------------------------------------------------
echo [3/7] Switching build repository to PUBLIC...
gh repo edit "%BUILD_REPO%" --visibility public --accept-visibility-change-consequences
if errorlevel 1 (
    echo [ERROR] Could not switch %BUILD_REPO% to public.
    goto :fail_no_cleanup
)
set "MADE_PUBLIC=1"

echo [4/7] Starting iOS TestFlight workflow...
gh workflow run "%WORKFLOW%" ^
    --repo "%BUILD_REPO%" ^
    --ref "%BUILD_BRANCH%" ^
    -f apk_test_complete=true ^
    -f tested_commit="%BUILD_SHA%" ^
    -f preview_without_app_group="%PREVIEW_WITHOUT_APP_GROUP%" ^
    -f status_only=false
if errorlevel 1 (
    echo [ERROR] Failed to dispatch workflow.
    goto :cleanup_failure
)

rem Wait until GitHub exposes the newly-created run for this exact commit.
echo [5/7] Waiting for workflow run to appear...
set "RUN_ID="
for /L %%N in (1,1,30) do (
    for /f "delims=" %%A in ('gh run list --repo "%BUILD_REPO%" --workflow "%WORKFLOW%" --commit "%BUILD_SHA%" --event workflow_dispatch --limit 1 --json databaseId --jq ".[0].databaseId" 2^>nul') do set "RUN_ID=%%A"
    if defined RUN_ID goto :run_found
    timeout /t 2 /nobreak >nul
)

echo [ERROR] Workflow was dispatched, but its run ID did not appear within 60 seconds.
goto :cleanup_failure

:run_found
echo       Run ID: %RUN_ID%
echo.
gh run view "%RUN_ID%" --repo "%BUILD_REPO%" --web >nul 2>nul

echo [6/7] Waiting for GitHub Actions + TestFlight upload...
echo       The repository stays PUBLIC only while this command is running.
echo.
gh run watch "%RUN_ID%" --repo "%BUILD_REPO%" --exit-status
set "RUN_RESULT=%ERRORLEVEL%"

if not "%RUN_RESULT%"=="0" (
    echo.
    echo [ERROR] iOS/TestFlight workflow failed.
    gh run view "%RUN_ID%" --repo "%BUILD_REPO%"
    goto :cleanup_failure
)

echo.
echo [SUCCESS] GitHub Actions workflow completed successfully.
goto :cleanup_success

:snapshot_fail
echo [ERROR] Failed while creating the build snapshot repository.
goto :fail_no_cleanup

:cleanup_success
echo [7/7] Returning build repository to PRIVATE...
gh repo edit "%BUILD_REPO%" --visibility private --accept-visibility-change-consequences
if errorlevel 1 (
    echo.
    echo [CRITICAL] Build succeeded, but automatic PRIVATE restoration FAILED.
    echo Immediately make this repository private manually:
    echo https://github.com/%BUILD_REPO%/settings
    goto :final_fail
)
set "MADE_PUBLIC="
goto :clean_temp_success

:cleanup_failure
echo.
echo [CLEANUP] Returning build repository to PRIVATE...
if defined MADE_PUBLIC (
    gh repo edit "%BUILD_REPO%" --visibility private --accept-visibility-change-consequences
    if errorlevel 1 (
        echo.
        echo [CRITICAL] Automatic PRIVATE restoration FAILED.
        echo Immediately make this repository private manually:
        echo https://github.com/%BUILD_REPO%/settings
    ) else (
        set "MADE_PUBLIC="
    )
)
goto :final_fail

:clean_temp_success
if exist "%ZIP_PATH%" del /q "%ZIP_PATH%" >nul 2>nul
if exist "%TEMP_ROOT%" rmdir /s /q "%TEMP_ROOT%" >nul 2>nul

echo.
echo ============================================================
echo   DONE
echo   Source SHA : %SOURCE_SHA%
echo   Build SHA  : %BUILD_SHA%
echo   TestFlight workflow completed.
echo   %BUILD_REPO% is PRIVATE again.
echo ============================================================
echo.
pause
exit /b 0

:fail_no_cleanup
if exist "%ZIP_PATH%" del /q "%ZIP_PATH%" >nul 2>nul
if exist "%TEMP_ROOT%" rmdir /s /q "%TEMP_ROOT%" >nul 2>nul
goto :final_fail

:final_fail
echo.
echo ============================================================
echo   RELEASE FAILED
echo ============================================================
echo.
pause
exit /b 1

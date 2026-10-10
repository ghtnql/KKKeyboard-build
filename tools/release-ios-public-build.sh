#!/usr/bin/env bash
set -Eeuo pipefail

# ============================================================
# KKKeyboard iOS TestFlight public-build helper (Linux)
#
# Purpose:
#   - Keep the real KKKeyboard repository private.
#   - Copy only the current HEAD snapshot into a dedicated build repo.
#   - Make the build repo public only while GitHub Actions macOS runs.
#   - Upload to TestFlight through ios-testflight.yml.
#   - Return the build repo to private on success or failure.
#
# One-time requirements:
#   1) git
#   2) GitHub CLI (gh) + "gh auth login"
#   3) Run this script from inside the real KKKeyboard repository.
#   4) Configure TestFlight secrets/variables once in the build repo.
# ============================================================

# ---------- USER CONFIG ----------
BUILD_REPO="ghtnql/KKKeyboard-build"
WORKFLOW="ios-testflight.yml"
BUILD_BRANCH="main"

# Normal production/TestFlight build.
# Set to true only when intentionally building without App Group.
PREVIEW_WITHOUT_APP_GROUP="false"
RELEASE_APPROVED="${KK_RELEASE_APPROVED:-false}"
APK_TEST_COMPLETE="true"
if [[ "$RELEASE_APPROVED" == "true" ]]; then APK_TEST_COMPLETE="false"; fi
# ---------------------------------

SOURCE_ROOT=""
SOURCE_SHA=""
BUILD_SHA=""
RUN_ID=""
TEMP_ROOT=""
ZIP_PATH=""
BUILD_DIR=""
MADE_PUBLIC=0

cleanup_temp() {
  if [[ -n "${ZIP_PATH:-}" && -e "$ZIP_PATH" ]]; then
    rm -f -- "$ZIP_PATH" || true
  fi
  if [[ -n "${TEMP_ROOT:-}" && -d "$TEMP_ROOT" ]]; then
    rm -rf -- "$TEMP_ROOT" || true
  fi
}

restore_private() {
  if [[ "${MADE_PUBLIC:-0}" -eq 1 ]]; then
    echo
    echo "[CLEANUP] Returning build repository to PRIVATE..."
    if gh api --method PATCH "repos/$BUILD_REPO" -f visibility=private >/dev/null; then
      MADE_PUBLIC=0
    else
      echo
      echo "[CRITICAL] Automatic PRIVATE restoration FAILED."
      echo "Immediately make this repository private manually:"
      echo "https://github.com/$BUILD_REPO/settings"
      return 1
    fi
  fi
}

on_exit() {
  local exit_code=$?
  trap - EXIT INT TERM
  restore_private || exit_code=1
  cleanup_temp
  exit "$exit_code"
}

trap on_exit EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

echo
echo "============================================================"
echo "  KKKeyboard - iOS TestFlight release (Linux)"
echo "============================================================"
echo

command -v git >/dev/null 2>&1 || {
  echo "[ERROR] git was not found."
  exit 1
}

command -v gh >/dev/null 2>&1 || {
  echo "[ERROR] GitHub CLI (gh) was not found."
  echo "Ubuntu/Debian example:"
  echo "  sudo apt install gh"
  exit 1
}

gh auth status >/dev/null 2>&1 || {
  echo "[ERROR] GitHub CLI is not logged in."
  echo "Run: gh auth login"
  exit 1
}

git rev-parse --is-inside-work-tree >/dev/null 2>&1 || {
  echo "[ERROR] Run this script from inside the real KKKeyboard Git repository."
  exit 1
}

SOURCE_ROOT="$(git rev-parse --show-toplevel)"
SOURCE_SHA="$(git rev-parse HEAD)"

echo "Source : $SOURCE_ROOT"
echo "HEAD   : $SOURCE_SHA"
echo "Build  : $BUILD_REPO"
echo

if [[ -n "$(git -C "$SOURCE_ROOT" status --porcelain)" ]]; then
  echo "[ERROR] Working tree is not clean."
  echo "Commit or stash changes before releasing."
  exit 1
fi

if [[ ! -f "$SOURCE_ROOT/.github/workflows/$WORKFLOW" ]]; then
  echo "[ERROR] Missing workflow:"
  echo "$SOURCE_ROOT/.github/workflows/$WORKFLOW"
  exit 1
fi

gh auth setup-git >/dev/null 2>&1 || true

# ------------------------------------------------------------
# Ensure the dedicated build repository exists.
# ------------------------------------------------------------
if ! gh repo view "$BUILD_REPO" >/dev/null 2>&1; then
  echo "[INFO] $BUILD_REPO does not exist. Creating it as PRIVATE..."
  gh repo create "$BUILD_REPO" --private --disable-issues --disable-wiki

  cat <<EOF

============================================================
[ONE-TIME SETUP REQUIRED]

The build repository was created successfully.

GitHub does not expose existing secret VALUES, so they cannot
be copied automatically from the real KKKeyboard repository.
Configure the TestFlight environment/repository secrets and
variables in $BUILD_REPO once, then run this script again.

Required secrets:
  APPSTORE_API_PRIVATE_KEY
  IOS_DISTRIBUTION_P12_BASE64
  IOS_DISTRIBUTION_P12_PASSWORD
  IOS_APP_PROVISIONING_PROFILE_BASE64
  IOS_EXTENSION_PROVISIONING_PROFILE_BASE64

Required variables:
  APPSTORE_ISSUER_ID
  APPSTORE_API_KEY_ID
  APPLE_TEAM_ID
  IOS_APP_PROFILE_UUID
  IOS_EXTENSION_PROFILE_UUID

The workflow uses the "testflight" environment.
============================================================
EOF
  exit 2
fi

# ------------------------------------------------------------
# Create a history-free snapshot of SOURCE_SHA.
# ------------------------------------------------------------
TEMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/kkkeyboard-ios-build.XXXXXX")"
ZIP_PATH="$TEMP_ROOT/snapshot.zip"
BUILD_DIR="$TEMP_ROOT/snapshot"
mkdir -p "$BUILD_DIR"

echo "[1/7] Creating clean snapshot of HEAD..."
git -C "$SOURCE_ROOT" archive --format=zip --output="$ZIP_PATH" HEAD

python3 - "$ZIP_PATH" "$BUILD_DIR" <<'PY'
import sys, zipfile
src, dst = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(src) as z:
    z.extractall(dst)
PY

# git archive/zip extraction loses unix exec bits; the Xcode build phase
# runs ./gradlew, so restore executability for any gradle wrapper here.
find "$BUILD_DIR" -name gradlew -type f -exec chmod +x {} +

cat > "$BUILD_DIR/BUILD_SOURCE.txt" <<EOF
Source repository: ghtnql/KKKeyboard
Source commit: $SOURCE_SHA
Snapshot generated by release-ios-public-build.sh
EOF

echo "[2/7] Preparing single-commit build repository..."
git -C "$BUILD_DIR" init -b "$BUILD_BRANCH" >/dev/null
git -C "$BUILD_DIR" config user.name "KKKeyboard Build Bot"
git -C "$BUILD_DIR" config user.email "build@kkkeyboard.local"
git -C "$BUILD_DIR" add -A
git -C "$BUILD_DIR" commit -m "Build snapshot from $SOURCE_SHA" >/dev/null
git -C "$BUILD_DIR" remote add origin "https://github.com/$BUILD_REPO.git"
git -C "$BUILD_DIR" push --force --set-upstream origin "$BUILD_BRANCH"

BUILD_SHA="$(git -C "$BUILD_DIR" rev-parse HEAD)"
echo "      Build snapshot SHA: $BUILD_SHA"

# ------------------------------------------------------------
# Public only for the actual macOS Actions run.
# ------------------------------------------------------------
echo "[3/7] Switching build repository to PUBLIC..."
MADE_PUBLIC=1
gh api --method PATCH "repos/$BUILD_REPO" -f visibility=public >/dev/null

echo "[4/7] Starting iOS TestFlight workflow..."
gh workflow run "$WORKFLOW" \
  --repo "$BUILD_REPO" \
  --ref "$BUILD_BRANCH" \
  -f apk_test_complete="$APK_TEST_COMPLETE" \
  -f release_approved="$RELEASE_APPROVED" \
  -f skip_tests="${KK_SKIP_IOS_TESTS:-false}" \
  -f tested_commit="$BUILD_SHA" \
  -f preview_without_app_group="$PREVIEW_WITHOUT_APP_GROUP" \
  -f status_only=false

echo "[5/7] Waiting for workflow run to appear..."
for _ in $(seq 1 30); do
  RUN_ID="$(
    gh run list \
      --repo "$BUILD_REPO" \
      --workflow "$WORKFLOW" \
      --commit "$BUILD_SHA" \
      --event workflow_dispatch \
      --limit 1 \
      --json databaseId \
      --jq '.[0].databaseId // empty' 2>/dev/null || true
  )"

  if [[ -n "$RUN_ID" ]]; then
    break
  fi
  sleep 2
done

if [[ -z "$RUN_ID" ]]; then
  echo "[ERROR] Workflow was dispatched, but its run ID did not appear within 60 seconds."
  exit 1
fi

echo "      Run ID: $RUN_ID"
echo
gh run view "$RUN_ID" --repo "$BUILD_REPO" --web >/dev/null 2>&1 || true

echo "[6/7] Waiting for GitHub Actions + TestFlight upload..."
echo "      The repository stays PUBLIC only while this command is running."
echo

if ! gh run watch "$RUN_ID" --repo "$BUILD_REPO" --exit-status; then
  echo
  echo "[ERROR] iOS/TestFlight workflow failed."
  gh run view "$RUN_ID" --repo "$BUILD_REPO" || true
  exit 1
fi

echo
echo "[SUCCESS] GitHub Actions workflow completed successfully."

echo "[7/7] Returning build repository to PRIVATE..."
restore_private

cleanup_temp
trap - EXIT INT TERM

echo
echo "============================================================"
echo "  DONE"
echo "  Source SHA : $SOURCE_SHA"
echo "  Build SHA  : $BUILD_SHA"
echo "  TestFlight workflow completed."
echo "  $BUILD_REPO is PRIVATE again."
echo "============================================================"
echo

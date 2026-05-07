#!/usr/bin/env bash
set -euo pipefail

APP_ID="com.tiberiptv.fire"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

AVD_NAME="${AVD_NAME:-}"
DEVICE_SERIAL="${DEVICE_SERIAL:-}"
SKIP_EMULATOR="${SKIP_EMULATOR:-0}"
SHOW_LOGS="0"
CLEAN_BUILD="0"
BUILD_TYPE="${BUILD_TYPE:-debug}"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/debug-local.sh [options]

Build debug, demarre un emulateur Android TV/Fire TV si besoin, installe l'APK,
puis lance l'app.

Options:
  --debug            Build/install debug (defaut).
  --release          Build/install release signee Fire Stick.
  --avd NAME          Force un AVD precis.
  --device SERIAL     Force un appareil/emulateur ADB precis.
  --no-emulator       N'essaie pas de demarrer un emulateur.
  --clean             Lance un clean avant le build.
  --logs              Affiche le logcat de l'app apres lancement.
  -h, --help          Affiche cette aide.

Exemples:
  ./scripts/debug-local.sh
  ./scripts/debug-local.sh --release --no-emulator --device 192.168.1.42:5555
  ./scripts/debug-local.sh --avd Android_TV_1080p_API_35
  ./scripts/debug-local.sh --device emulator-5554
  ./scripts/debug-local.sh --no-emulator --device 192.168.1.42:5555
  ./scripts/debug-local.sh --logs

Variables d'environnement equivalentes:
  BUILD_TYPE=release ./scripts/debug-local.sh --no-emulator --device 192.168.1.42:5555
  AVD_NAME=Android_TV_1080p_API_35 ./scripts/debug-local.sh
  DEVICE_SERIAL=192.168.1.42:5555 SKIP_EMULATOR=1 ./scripts/debug-local.sh
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --debug)
      BUILD_TYPE="debug"
      shift
      ;;
    --release)
      BUILD_TYPE="release"
      shift
      ;;
    --avd)
      AVD_NAME="${2:-}"
      shift 2
      ;;
    --device)
      DEVICE_SERIAL="${2:-}"
      shift 2
      ;;
    --no-emulator)
      SKIP_EMULATOR="1"
      shift
      ;;
    --clean)
      CLEAN_BUILD="1"
      shift
      ;;
    --logs)
      SHOW_LOGS="1"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Option inconnue: $1"
      usage
      exit 1
      ;;
  esac
done

case "$BUILD_TYPE" in
  debug|release) ;;
  *)
    echo "BUILD_TYPE invalide: $BUILD_TYPE (attendu: debug ou release)"
    exit 1
    ;;
esac

sdk_dir() {
  if [[ -n "${ANDROID_HOME:-}" && -d "$ANDROID_HOME" ]]; then
    printf '%s\n' "$ANDROID_HOME"
  elif [[ -n "${ANDROID_SDK_ROOT:-}" && -d "$ANDROID_SDK_ROOT" ]]; then
    printf '%s\n' "$ANDROID_SDK_ROOT"
  elif [[ -d "$HOME/Library/Android/sdk" ]]; then
    printf '%s\n' "$HOME/Library/Android/sdk"
  else
    return 1
  fi
}

SDK_DIR="$(sdk_dir || true)"
if [[ -z "$SDK_DIR" ]]; then
  echo "Android SDK introuvable. Ouvre Android Studio une fois, ou exporte ANDROID_HOME."
  exit 1
fi

ADB="$SDK_DIR/platform-tools/adb"
EMULATOR="$SDK_DIR/emulator/emulator"

if [[ ! -x "$ADB" ]]; then
  echo "adb introuvable: $ADB"
  exit 1
fi

if [[ ! -x "$EMULATOR" && "$SKIP_EMULATOR" != "1" ]]; then
  echo "emulator introuvable: $EMULATOR"
  exit 1
fi

adb_target() {
  if [[ -n "$DEVICE_SERIAL" ]]; then
    "$ADB" -s "$DEVICE_SERIAL" "$@"
  else
    "$ADB" "$@"
  fi
}

device_state() {
  "$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1; exit }'
}

wait_for_device() {
  local timeout_seconds="${1:-240}"
  local start
  start="$(date +%s)"

  while true; do
    if [[ -n "$DEVICE_SERIAL" ]]; then
      if "$ADB" -s "$DEVICE_SERIAL" get-state >/dev/null 2>&1; then
        printf '%s\n' "$DEVICE_SERIAL"
        return 0
      fi
    else
      local found
      found="$(device_state)"
      if [[ -n "$found" ]]; then
        printf '%s\n' "$found"
        return 0
      fi
    fi

    if (( "$(date +%s)" - start > timeout_seconds )); then
      return 1
    fi
    sleep 2
  done
}

pick_avd() {
  if [[ -n "$AVD_NAME" ]]; then
    printf '%s\n' "$AVD_NAME"
    return 0
  fi

  local avds
  avds="$("$EMULATOR" -list-avds)"

  local tv_avd
  tv_avd="$(printf '%s\n' "$avds" | awk 'tolower($0) ~ /(fire|tv|television|leanback)/ { print; exit }')"
  if [[ -n "$tv_avd" ]]; then
    printf '%s\n' "$tv_avd"
    return 0
  fi

  printf '%s\n' "$avds" | awk 'NF { print; exit }'
}

launch_emulator_if_needed() {
  if [[ -n "$DEVICE_SERIAL" || "$SKIP_EMULATOR" == "1" ]]; then
    return 0
  fi

  if [[ -n "$(device_state)" ]]; then
    return 0
  fi

  local avd
  avd="$(pick_avd)"
  if [[ -z "$avd" ]]; then
    echo "Aucun AVD trouve. Cree un Android TV Virtual Device dans Android Studio > Device Manager."
    exit 1
  fi

  echo "Demarrage de l'emulateur: $avd"
  nohup "$EMULATOR" -avd "$avd" -netdelay none -netspeed full >/tmp/tiber-iptv-emulator.log 2>&1 &
}

resolve_serial() {
  local serial
  serial="$(wait_for_device 240 || true)"
  if [[ -z "$serial" ]]; then
    echo "Aucun appareil ADB disponible."
    echo "Lance un emulateur Android TV ou connecte ton Fire Stick avec: adb connect <ip>:5555"
    exit 1
  fi
  printf '%s\n' "$serial"
}

resolve_apk_path() {
  local variant="$1"
  local apk_dir="$ROOT_DIR/app/build/outputs/apk/$variant"
  local apk

  if [[ ! -d "$apk_dir" ]]; then
    echo "Dossier APK introuvable: $apk_dir" >&2
    return 1
  fi

  local candidates=("$apk_dir"/*.apk)
  if [[ ! -e "${candidates[0]}" ]]; then
    echo "Aucun APK trouve dans: $apk_dir" >&2
    return 1
  fi
  apk="$(ls -t "${candidates[@]}" | head -n 1)"

  printf '%s\n' "$apk"
}

build_apk() {
  if [[ "$BUILD_TYPE" == "release" ]]; then
    echo "Build release signee Fire Stick..."
    "$ROOT_DIR/scripts/build-firestick-release.sh"
  else
    echo "Build debug..."
    "$ROOT_DIR/gradlew" :app:assembleDebug
  fi
}

cd "$ROOT_DIR"

if [[ "$CLEAN_BUILD" == "1" ]]; then
  echo "Clean du projet..."
  "$ROOT_DIR/gradlew" :app:clean
fi

build_apk
APK_PATH="$(resolve_apk_path "$BUILD_TYPE")"

launch_emulator_if_needed
DEVICE_SERIAL="$(resolve_serial)"

echo "Appareil cible: $DEVICE_SERIAL"
"$ADB" -s "$DEVICE_SERIAL" wait-for-device

echo "Installation APK $BUILD_TYPE: $APK_PATH"
"$ADB" -s "$DEVICE_SERIAL" install -r "$APK_PATH" >/dev/null

echo "Lancement app..."
"$ADB" -s "$DEVICE_SERIAL" shell monkey -p "$APP_ID" 1 >/dev/null

echo "App lancee: $APP_ID"
echo "APK installe: $APK_PATH"

if [[ "$SHOW_LOGS" == "1" ]]; then
  echo "Logcat filtre sur $APP_ID. Ctrl+C pour quitter."
  PID="$("$ADB" -s "$DEVICE_SERIAL" shell pidof "$APP_ID" | tr -d '\r' || true)"
  if [[ -n "$PID" ]]; then
    "$ADB" -s "$DEVICE_SERIAL" logcat --pid="$PID"
  else
    "$ADB" -s "$DEVICE_SERIAL" logcat | grep --line-buffered "$APP_ID"
  fi
fi

#!/usr/bin/env bash
set -euo pipefail

APP_ID="com.tiberiptv.fire"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK_PATH="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/run-tv.sh

Options via environment variables:
  AVD_NAME=Nom_AVD          Force un emulateur precis.
  DEVICE_SERIAL=serial      Force un appareil/emulateur ADB precis.
  SKIP_EMULATOR=1           N'essaie pas de demarrer un emulateur.

Exemples:
  ./scripts/run-tv.sh
  AVD_NAME="Android_TV_1080p_API_35" ./scripts/run-tv.sh
  SKIP_EMULATOR=1 DEVICE_SERIAL=192.168.1.42:5555 ./scripts/run-tv.sh
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

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
  echo "Android SDK introuvable. Ouvre le projet dans Android Studio une fois, ou exporte ANDROID_HOME."
  exit 1
fi

ADB="$SDK_DIR/platform-tools/adb"
EMULATOR="$SDK_DIR/emulator/emulator"

if [[ ! -x "$ADB" ]]; then
  echo "adb introuvable: $ADB"
  exit 1
fi

if [[ ! -x "$EMULATOR" && "${SKIP_EMULATOR:-0}" != "1" ]]; then
  echo "emulator introuvable: $EMULATOR"
  exit 1
fi

device_state() {
  "$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1; exit }'
}

wait_for_device() {
  local timeout_seconds="${1:-180}"
  local start
  start="$(date +%s)"

  while true; do
    if [[ -n "${DEVICE_SERIAL:-}" ]]; then
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
  if [[ -n "${AVD_NAME:-}" ]]; then
    printf '%s\n' "$AVD_NAME"
    return 0
  fi

  local avds
  avds="$("$EMULATOR" -list-avds)"
  local tv_avd
  tv_avd="$(printf '%s\n' "$avds" | awk 'tolower($0) ~ /(tv|television|leanback)/ { print; exit }')"
  if [[ -n "$tv_avd" ]]; then
    printf '%s\n' "$tv_avd"
    return 0
  fi

  printf '%s\n' "$avds" | awk 'NF { print; exit }'
}

cd "$ROOT_DIR"

echo "Build de l'APK debug..."
"$ROOT_DIR/gradlew" :app:assembleDebug >/dev/null

SERIAL=""
if [[ -n "${DEVICE_SERIAL:-}" ]]; then
  SERIAL="$DEVICE_SERIAL"
elif [[ "${SKIP_EMULATOR:-0}" == "1" ]]; then
  SERIAL="$(wait_for_device 20 || true)"
else
  SERIAL="$(device_state)"
fi

if [[ -z "$SERIAL" && "${SKIP_EMULATOR:-0}" != "1" ]]; then
  AVD="$(pick_avd)"
  if [[ -z "$AVD" ]]; then
    echo "Aucun AVD trouve. Cree un Android TV Virtual Device dans Android Studio > Device Manager."
    exit 1
  fi

  echo "Demarrage de l'emulateur: $AVD"
  nohup "$EMULATOR" -avd "$AVD" -netdelay none -netspeed full >/tmp/tiber-iptv-emulator.log 2>&1 &
  SERIAL="$(wait_for_device 240 || true)"
fi

if [[ -z "$SERIAL" ]]; then
  echo "Aucun appareil ADB disponible."
  echo "Lance un emulateur Android TV ou connecte ton Fire Stick avec: adb connect <ip>:5555"
  exit 1
fi

echo "Appareil cible: $SERIAL"
"$ADB" -s "$SERIAL" wait-for-device
"$ADB" -s "$SERIAL" install -r "$APK_PATH" >/dev/null
"$ADB" -s "$SERIAL" shell monkey -p "$APP_ID" 1 >/dev/null

echo "App lancee: $APP_ID"

#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RELEASE_DIR="$ROOT_DIR/release"
KEYSTORE="$RELEASE_DIR/tiber-firestick.jks"
PROPS="$RELEASE_DIR/keystore.properties"
ALIAS="tiber-firestick"

mkdir -p "$RELEASE_DIR"

random_password() {
  local value
  value="$(dd if=/dev/urandom bs=96 count=1 2>/dev/null | base64 | tr -dc 'A-Za-z0-9')"
  printf '%s\n' "${value:0:28}"
}

if [[ ! -f "$PROPS" ]]; then
  STORE_PASSWORD="$(random_password)"
  cat >"$PROPS" <<EOF
TIBER_RELEASE_STORE_PASSWORD=$STORE_PASSWORD
TIBER_RELEASE_KEY_PASSWORD=$STORE_PASSWORD
EOF
  chmod 600 "$PROPS"
fi

set -a
source "$PROPS"
set +a
export TIBER_RELEASE_KEY_PASSWORD="$TIBER_RELEASE_STORE_PASSWORD"

if [[ ! -f "$KEYSTORE" ]]; then
  keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -storepass "$TIBER_RELEASE_STORE_PASSWORD" \
    -keypass "$TIBER_RELEASE_KEY_PASSWORD" \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Tiber IPTV Fire, OU=Local, O=Tiber, L=Paris, ST=IDF, C=FR" >/dev/null
fi

export TIBER_RELEASE_STORE_FILE="$KEYSTORE"
export TIBER_RELEASE_KEY_ALIAS="$ALIAS"

cd "$ROOT_DIR"
"$ROOT_DIR/gradlew" -PfirestickAbis=armeabi-v7a,arm64-v8a :app:assembleRelease

release_apks=("$ROOT_DIR"/app/build/outputs/apk/release/*.apk)
if [[ ! -e "${release_apks[0]}" ]]; then
  echo "Aucun APK release trouve."
  exit 1
fi
APK="$(ls -t "${release_apks[@]}" | head -n 1)"
echo "APK release signee: $APK"

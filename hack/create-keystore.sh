#!/usr/bin/env bash
#
# Creates the signing key this app is released with, and the keystore.properties
# that points at it.
#
# The key is deliberately kept outside the repository: it is the only thing that
# decides whether a new build installs over the one already on a phone, so it
# must not be regenerated casually and must not be committed. Back up the file
# this script writes -- losing it means every device has to uninstall before it
# can take an update.

set -euo pipefail

KEYSTORE="${SANDMAN_KEYSTORE_FILE:-$HOME/.config/hauke-cloud/sandman-android/release.jks}"
ALIAS="${SANDMAN_KEY_ALIAS:-sandman}"
VALIDITY_DAYS="${SANDMAN_KEY_VALIDITY:-10950}" # 30 years
DNAME="${SANDMAN_KEY_DNAME:-CN=sandman-android, O=hauke.cloud, C=DE}"

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
properties="${repo_root}/keystore.properties"

if [[ -f "${KEYSTORE}" ]]; then
  echo "keystore already exists: ${KEYSTORE}" >&2
  echo "delete it by hand if you really mean to replace it." >&2
  exit 1
fi

if [[ -n "${SANDMAN_KEYSTORE_PASSWORD:-}" ]]; then
  password="${SANDMAN_KEYSTORE_PASSWORD}"
elif command -v openssl >/dev/null 2>&1; then
  password="$(openssl rand -base64 24 | tr -d '\n/+=' | cut -c1-24)"
else
  echo "set SANDMAN_KEYSTORE_PASSWORD or install openssl" >&2
  exit 1
fi

mkdir -p "$(dirname "${KEYSTORE}")"

keytool -genkeypair \
  -keystore "${KEYSTORE}" \
  -storetype PKCS12 \
  -storepass "${password}" \
  -keypass "${password}" \
  -alias "${ALIAS}" \
  -keyalg RSA \
  -keysize 4096 \
  -validity "${VALIDITY_DAYS}" \
  -dname "${DNAME}"

chmod 600 "${KEYSTORE}"

umask 077
cat > "${properties}" <<PROPERTIES
# Written by hack/create-keystore.sh. Not for committing.
storeFile=${KEYSTORE}
storePassword=${password}
keyAlias=${ALIAS}
keyPassword=${password}
PROPERTIES

echo "keystore:   ${KEYSTORE}"
echo "properties: ${properties}"
echo
echo "Back up both. The key is what lets a new build replace an installed one."
keytool -list -v -keystore "${KEYSTORE}" -storepass "${password}" -alias "${ALIAS}" \
  | grep -E "SHA256:|Valid from" || true

#!/usr/bin/env bash
#
# Pushes the signing key to GitHub Actions, so that CI produces APKs that can
# update an app already installed from a local build.
#
# Reads keystore.properties -- the file hack/create-keystore.sh wrote -- and
# sets four secrets and one variable on the repository:
#
#   SANDMAN_KEYSTORE_BASE64    the keystore itself
#   SANDMAN_KEYSTORE_PASSWORD
#   SANDMAN_KEY_ALIAS
#   SANDMAN_KEY_PASSWORD
#   SANDMAN_SIGNING_SHA256     (a variable, not a secret: a certificate
#                               fingerprint is public, and CI fails if a
#                               release is ever signed with anything else)
#
# Run with --dry-run to see what would be set without setting it.

set -euo pipefail

dry_run=false
[[ "${1:-}" == "--dry-run" ]] && dry_run=true

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
properties="${repo_root}/keystore.properties"

if [[ ! -f "${properties}" ]]; then
  echo "no ${properties}; run hack/create-keystore.sh first" >&2
  exit 1
fi

command -v gh >/dev/null 2>&1 || { echo "the GitHub CLI (gh) is required" >&2; exit 1; }

property() {
  # Trailing whitespace and CRs in a properties file would otherwise end up
  # inside the secret, where they are invisible and break the build.
  sed -n "s/^$1=//p" "${properties}" | head -1 | tr -d '\r' | sed -e 's/[[:space:]]*$//'
}

store_file="$(property storeFile)"
store_password="$(property storePassword)"
key_alias="$(property keyAlias)"
key_password="$(property keyPassword)"

store_file="${store_file/#\~/${HOME}}"
[[ -f "${store_file}" ]] || { echo "keystore not found: ${store_file}" >&2; exit 1; }
[[ -n "${key_alias}" ]] || key_alias="sandman"
[[ -n "${key_password}" ]] || key_password="${store_password}"

fingerprint="$(
  keytool -list -v -keystore "${store_file}" -storepass "${store_password}" -alias "${key_alias}" \
    | sed -n 's/.*SHA256: //p' | head -1 | tr -d ': \r' | tr '[:upper:]' '[:lower:]'
)"

echo "keystore:    ${store_file}"
echo "alias:       ${key_alias}"
echo "fingerprint: ${fingerprint}"
echo

if ${dry_run}; then
  echo "--dry-run: nothing was set."
  exit 0
fi

base64 -w0 < "${store_file}" | gh secret set SANDMAN_KEYSTORE_BASE64
printf '%s' "${store_password}" | gh secret set SANDMAN_KEYSTORE_PASSWORD
printf '%s' "${key_alias}"      | gh secret set SANDMAN_KEY_ALIAS
printf '%s' "${key_password}"   | gh secret set SANDMAN_KEY_PASSWORD
gh variable set SANDMAN_SIGNING_SHA256 --body "${fingerprint}"

echo
echo "Set. Tag a release to use them:"
echo "  git tag -a v0.1.0 -m 'v0.1.0' && git push origin v0.1.0"

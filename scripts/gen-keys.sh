#!/usr/bin/env bash
# Generate a fresh RSA-2048 keypair for the auth-service in PKCS8/PEM format.
# Output goes to ./.secrets/keys/ (gitignored). NEVER commit the result.
#
# Usage:
#   ./scripts/gen-keys.sh                # writes .secrets/keys/{private,public}.pem
#   ./scripts/gen-keys.sh --kid my-kid   # also writes .secrets/keys/.kid

set -euo pipefail

KID="auth-key-$(date +%Y%m%d)"
if [[ "${1:-}" == "--kid" && -n "${2:-}" ]]; then
  KID="$2"
fi

OUT_DIR=".secrets/keys"
mkdir -p "$OUT_DIR"

PRIVATE_KEY="$OUT_DIR/private.pem"
PUBLIC_KEY="$OUT_DIR/public.pem"

if [[ -f "$PRIVATE_KEY" ]]; then
  echo "Refusing to overwrite existing $PRIVATE_KEY (delete it first if you really want to rotate)" >&2
  exit 1
fi

# PKCS#8 unencrypted private key.
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$PRIVATE_KEY"
openssl rsa -in "$PRIVATE_KEY" -pubout -out "$PUBLIC_KEY"
echo -n "$KID" > "$OUT_DIR/.kid"

chmod 600 "$PRIVATE_KEY"
chmod 644 "$PUBLIC_KEY"

cat <<EOF
Generated:
  $PRIVATE_KEY      (mode 600)
  $PUBLIC_KEY
  $OUT_DIR/.kid     (kid=$KID)

Set in your env / .env file:
  AUTH_RSA_PRIVATE_KEY_LOCATION=file:$(pwd)/$PRIVATE_KEY
  AUTH_RSA_PUBLIC_KEY_LOCATION=file:$(pwd)/$PUBLIC_KEY
  AUTH_RSA_KID=$KID
EOF

#!/usr/bin/env bash
# Regenerates the TLS material of the baseline profile loopback mock.
# Test-only and loopback-only: the leaf is valid for IP 127.0.0.1 and the names api.baseline.test and
# dns.baseline.test (RFC 6761 .test, served by the mock's DNS-over-HTTPS route) and the root is
# trusted solely by the instrumentation flavor. The root key is never kept.
# Both certificates are valid 2020-01-01 to 2051-01-01. Needs openssl 3. Writes:
#   app/src/instrumentation/res/raw/baseline_mock_ca.pem                      (root certificate)
#   baselineprofile/src/main/assets/baseline/tls/leaf-test-only.pem           (leaf certificate, then its PKCS#8 key)
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CA_OUT="$ROOT_DIR/app/src/instrumentation/res/raw/baseline_mock_ca.pem"
LEAF_OUT="$ROOT_DIR/baselineprofile/src/main/assets/baseline/tls/leaf-test-only.pem"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# Fixed validity: notBefore is years in the past so an emulator whose clock lags (a snapshot restored
# days later) still accepts the chain; `openssl x509 -req` cannot set it, `openssl ca` can.
NOT_BEFORE=20200101000000Z
NOT_AFTER=20510101000000Z

mkdir -p "$TMP/certs"
: > "$TMP/index.txt"
echo 01 > "$TMP/serial"
cat > "$TMP/ca.cnf" <<CNF
[ca]
default_ca = mock
[mock]
database = $TMP/index.txt
new_certs_dir = $TMP/certs
serial = $TMP/serial
default_md = sha256
policy = any
unique_subject = no
copy_extensions = none
[any]
commonName = supplied
[root_ext]
basicConstraints = critical,CA:TRUE
keyUsage = critical,keyCertSign,cRLSign
[leaf_ext]
subjectAltName = IP:127.0.0.1, DNS:api.baseline.test, DNS:dns.baseline.test
extendedKeyUsage = serverAuth
basicConstraints = CA:FALSE
CNF

openssl ecparam -name prime256v1 -genkey -noout -out "$TMP/root.key"
openssl req -new -key "$TMP/root.key" -subj "/CN=Puber baseline mock test-only root" -out "$TMP/root.csr"
openssl ca -batch -config "$TMP/ca.cnf" -selfsign -keyfile "$TMP/root.key" -in "$TMP/root.csr" \
  -startdate "$NOT_BEFORE" -enddate "$NOT_AFTER" -extensions root_ext -notext -out "$TMP/root.pem" 2>/dev/null

openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out "$TMP/leaf.key"
openssl req -new -key "$TMP/leaf.key" -subj "/CN=127.0.0.1" -out "$TMP/leaf.csr"
openssl ca -batch -config "$TMP/ca.cnf" -cert "$TMP/root.pem" -keyfile "$TMP/root.key" -in "$TMP/leaf.csr" \
  -startdate "$NOT_BEFORE" -enddate "$NOT_AFTER" -extensions leaf_ext -notext -out "$TMP/leaf.pem" 2>/dev/null

mkdir -p "$(dirname "$CA_OUT")" "$(dirname "$LEAF_OUT")"
cp "$TMP/root.pem" "$CA_OUT"
cat "$TMP/leaf.pem" "$TMP/leaf.key" > "$LEAF_OUT"

echo "validity: $NOT_BEFORE to $NOT_AFTER"
echo "root notAfter: $(openssl x509 -in "$CA_OUT" -noout -enddate)"
echo "leaf notAfter: $(openssl x509 -in "$TMP/leaf.pem" -noout -enddate)"

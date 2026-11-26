#!/bin/sh
# Create a PKCS12 self signed bundle in ${SERVICE_NAME}/certs/this.p12

set -eu
umask 077

TLS_KEY_STORE_PATH=/${SERVICE_NAME}/certs/this.p12

temporary_directory=$(mktemp -d "/tmp/tls.XXXXXX")
openssl req -quiet -x509 -newkey rsa:3072 -sha256 -noenc -days 3650 \
  -subj "/CN=localhost" \
  -keyout "$temporary_directory/this.key" \
  -out "$temporary_directory/this.crt"

openssl pkcs12 -export -name ${SERVICE_NAME} \
  -inkey "$temporary_directory/this.key" -in "$temporary_directory/this.crt" \
  -out "$TLS_KEY_STORE_PATH" -passout pass:

chmod 600 "$TLS_KEY_STORE_PATH"
rm -R "$temporary_directory"

exec "$@"

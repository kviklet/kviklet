# Upstream TLS test certificates

Test-only material for the proxy upstream-TLS integration tests: the database
testcontainers serve `server.crt`/`server.key`, clients verify against `ca.crt`,
and `wrong-ca.crt` is an unrelated CA used to assert that verification failures
are refused. The CA private keys are deliberately not kept.

Valid for 100 years. To regenerate:

```bash
openssl req -x509 -newkey rsa:2048 -nodes -keyout ca.key -out ca.crt -days 36500 \
  -subj "/CN=kviklet-upstream-test-ca"
openssl req -newkey rsa:2048 -nodes -keyout server.key -out server.csr -subj "/CN=localhost"
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out server.crt -days 36500 \
  -extfile <(printf "subjectAltName=DNS:localhost,IP:127.0.0.1,IP:::1")
openssl req -x509 -newkey rsa:2048 -nodes -keyout wrong-ca.key -out wrong-ca.crt -days 36500 \
  -subj "/CN=kviklet-wrong-test-ca"
rm server.csr ca.srl ca.key wrong-ca.key
```

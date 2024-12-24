#!/bin/bash

# Create root certificate
openssl req -new -x509 -days 365 -nodes -out certs/root.crt -keyout certs/root.key -subj "/CN=root-ca"

# Create server certificate configuration
cat > certs/server.conf << EOL
[req]
distinguished_name = req_distinguished_name
req_extensions = v3_req
prompt = no

[req_distinguished_name]
CN = postgres

[v3_req]
basicConstraints = CA:FALSE
keyUsage = nonRepudiation, digitalSignature, keyEncipherment
subjectAltName = @alt_names

[alt_names]
DNS.1 = localhost
DNS.2 = postgres
DNS.3 = kviklet-postgres
IP.1 = 127.0.0.1
IP.2 = ::1
EOL

# Generate server key and certificate request with config
openssl req -new -nodes -out certs/server.csr -keyout certs/server.key -config certs/server.conf

# Sign the server certificate with SAN extension
openssl x509 -req -in certs/server.csr -days 365 -CA certs/root.crt -CAkey certs/root.key \
    -CAcreateserial -out certs/server.crt -extfile certs/server.conf -extensions v3_req

# Generate client certificate request and key (in PKCS1 format initially)
openssl req -new -nodes -out certs/client.csr -keyout certs/client.key.pkcs1 -subj "/CN=postgres"

# Convert client key to PKCS8 format (what Java expects)
openssl pkcs8 -topk8 -inform PEM -outform DER -in certs/client.key.pkcs1 -out certs/client.key -nocrypt

# Sign the client certificate
openssl x509 -req -in certs/client.csr -days 365 -CA certs/root.crt -CAkey certs/root.key \
    -CAcreateserial -out certs/client.crt

# Clean up intermediate files
rm certs/client.key.pkcs1

# Set proper permissions
chmod 600 certs/*.key
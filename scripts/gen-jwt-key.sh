#!/usr/bin/env sh
# Génère une paire RSA et affiche la clé privée PEM encodée en base64,
# prête à coller dans .env comme JWT_PRIVATE_KEY=<valeur>
# (conversion en PKCS8 "BEGIN PRIVATE KEY" — genrsa sort du PKCS1)
set -e
openssl genrsa 4096 | openssl pkcs8 -topk8 -nocrypt | openssl base64 -A
echo ""

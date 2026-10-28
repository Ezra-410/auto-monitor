#!/usr/bin/env sh
# Génère une paire RSA et affiche la clé privée PEM encodée en base64,
# prête à coller dans .env comme JWT_PRIVATE_KEY=<valeur>
set -e
openssl genrsa 4096 | openssl base64 -A
echo ""

#!/usr/bin/env python3
"""M3 step B1: connect backend, ws-gateway and frontend to conote-iam-net and pass OIDC settings.
Safe to run twice: it skips anything already present. Keeps a backup: docker-compose.yml.bak-b1"""
import shutil, sys

PATH = "docker-compose.yml"
lines = open(PATH).read().split("\n")

ENV = {
    "backend":    ["OIDC_ISSUER: ${OIDC_ISSUER}", "OIDC_API_AUDIENCE: ${OIDC_API_AUDIENCE}"],
    "ws-gateway": ["OIDC_ISSUER: ${OIDC_ISSUER}", "OIDC_API_AUDIENCE: ${OIDC_API_AUDIENCE}"],
    "frontend":   ["NUXT_OIDC_ISSUER: ${OIDC_ISSUER}",
                   "NUXT_OIDC_CLIENT_ID: ${OIDC_CLIENT_ID}",
                   "NUXT_OIDC_CLIENT_SECRET: ${OIDC_CLIENT_SECRET}",
                   "NUXT_OIDC_REDIRECT_URI: ${OIDC_REDIRECT_URI}",
                   "NUXT_OIDC_POST_LOGOUT_REDIRECT_URI: ${OIDC_POST_LOGOUT_REDIRECT_URI}",
                   "NUXT_OIDC_DEBUG: ${OIDC_DEBUG:-false}",
                   "NUXT_SESSION_PASSWORD: ${NUXT_SESSION_PASSWORD}"],
}

def die(msg):
    sys.exit("ERROR: " + msg + " (file not changed)")

def block_end(start, indent):
    """index just after the last line indented deeper than `indent`, starting at start+1"""
    j = start + 1
    last = start
    while j < len(lines):
        l = lines[j]
        if l.strip() == "":
            j += 1
            continue
        if len(l) - len(l.lstrip(" ")) > indent:
            last = j
            j += 1
        else:
            break
    return last + 1

def service_range(name):
    try:
        s = lines.index(f"  {name}:")
    except ValueError:
        die(f"service '{name}' not found")
    return s, block_end(s, 2)

changes = []

# 1) top-level network declaration
try:
    top = lines.index("networks:")
except ValueError:
    die("top-level 'networks:' not found")
end = block_end(top, 0)
if not any(l.strip() == "name: conote-iam-net" for l in lines[top:end]):
    lines[end:end] = ["  iam:", "    external: true", "    name: conote-iam-net"]
    changes.append("networks: + iam (external conote-iam-net)")

# 2) per service: environment entries + network membership
for svc, envs in ENV.items():
    s, e = service_range(svc)
    sub = lines[s:e]
    if "    environment:" not in sub:
        die(f"{svc}: no 'environment:' section")
    ei = s + sub.index("    environment:")
    ee = block_end(ei, 4)
    existing = {l.strip().split(":")[0] for l in lines[ei:ee]}
    new = ["      " + v for v in envs if v.split(":")[0] not in existing]
    if new:
        lines[ee:ee] = new
        changes.append(f"{svc}: + {len(new)} environment variable(s)")
    s, e = service_range(svc)
    sub = lines[s:e]
    if "    networks:" not in sub:
        die(f"{svc}: no 'networks:' section")
    ni = s + sub.index("    networks:")
    ne = block_end(ni, 4)
    if not any(l.strip().startswith("iam:") for l in lines[ni:ne]):
        lines[ne:ne] = ["      iam: {}"]
        changes.append(f"{svc}: + network iam")

if not changes:
    print("Nothing to do: all B1 edits are already present.")
    sys.exit(0)
shutil.copy(PATH, PATH + ".bak-b1")
open(PATH, "w").write("\n".join(lines))
print("Backup saved as docker-compose.yml.bak-b1")
for c in changes:
    print("  changed:", c)

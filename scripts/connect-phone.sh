#!/usr/bin/env bash
set -euo pipefail

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Wireless ADB – Pair & Connect"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "On your Phone: Settings → Developer options → Wireless debugging"
echo "  → tap 'Pair device with pairing code'"
echo "    (The 'Pair with QR code' option is handled by Android Studio only;"
echo "     it requires an mDNS pairing server that adb CLI doesn't provide.)"
echo ""

# ── Step 1: Pair (one-time) ──────────────────────────────────────────────────
read -rp "Already paired before? [y/N] " paired
if [[ "$paired" != "y" && "$paired" != "Y" ]]; then
  echo ""
  read -rp  "Pairing IP:PORT shown on phone (e.g. 192.168.1.42:37123): " pair_addr
  read -rsp "6-digit code shown on phone: " pair_code
  echo ""
  "$ADB" pair "$pair_addr" "$pair_code"
  echo ""
fi

# ── Step 2: Connect ──────────────────────────────────────────────────────────
echo "▸ Trying mDNS auto-connect (no port needed for already-paired devices)…"
# adb mdns check confirms the daemon supports mDNS; then connect picks up the
# device automatically if it is advertising on the local network.
if "$ADB" mdns check &>/dev/null; then
  # Give the mdns service a moment to discover
  sleep 1
  mdns_devices=$("$ADB" mdns services 2>/dev/null || true)
  echo "$mdns_devices"
  # Try connecting to every advertised adb-tls-connect service
  connected=false
  while IFS= read -r line; do
    svc=$(echo "$line" | awk '{print $3}')
    [[ -z "$svc" ]] && continue
    result=$("$ADB" connect "$svc" 2>&1 || true)
    echo "$result"
    if echo "$result" | grep -q "connected to"; then
      connected=true
    fi
  done < <(echo "$mdns_devices" | grep "adb-tls-connect" || true)

  if $connected; then
    echo ""
    echo "▸ Connected devices:"
    "$ADB" devices
    echo ""
    echo "✔ Done. Now run: ./scripts/run-phone.sh"
    exit 0
  fi
  echo "⚠  mDNS discovery found no connectable devices."
fi

echo ""
echo "Falling back to manual entry."
echo "On the phone, check the current IP:PORT under 'Wireless debugging'"
echo "(the port changes each time debugging is toggled — grab a fresh one)."
echo ""
read -rp "Debug IP:PORT (e.g. 192.168.1.42:43567): " debug_addr

phone_ip="${debug_addr%%:*}"
echo ""
echo "▸ Checking network reachability to $phone_ip …"
if ! ping -c 2 -W 1000 "$phone_ip" &>/dev/null; then
  echo ""
  echo "✘ Cannot reach $phone_ip — direct device-to-device traffic is blocked."
  echo "  Likely cause: AP/client isolation on your router. Disable it, or"
  echo "  connect both devices to the same hotspot (e.g. Mac's Personal Hotspot)."
  echo ""
  exit 1
fi
echo "✔ Host reachable."
echo ""
# Check the specific port before handing off to adb, so we can give a
# clear error instead of adb's misleading "No route to host".
phone_port="${debug_addr##*:}"
if ! nc -z -w 2 "$phone_ip" "$phone_port" &>/dev/null; then
  echo "✘ Port $phone_port is not open on $phone_ip."
  echo ""
  echo "  Most likely: Wireless Debugging is toggled OFF on the phone."
  echo "  → Go to Settings → Developer options → Wireless debugging"
  echo "    and make sure the toggle is ON."
  echo "  The port also changes every time you toggle it — re-run this"
  echo "  script and enter the new port shown on that screen."
  echo ""
  exit 1
fi
echo "✔ Port open."
echo ""
# Restart the local adb daemon — a stale daemon causes spurious
# "No route to host" even when the phone is reachable.
echo "▸ Restarting adb server…"
"$ADB" kill-server
"$ADB" start-server
echo ""
"$ADB" connect "$debug_addr"

echo ""
echo "▸ Connected devices:"
"$ADB" devices
echo ""
echo "✔ Done. Now run: ./scripts/run-phone.sh"

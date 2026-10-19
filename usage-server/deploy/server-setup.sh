#!/usr/bin/env bash
# gzcompanion-usage - one-shot, stop-on-anything-unexpected server setup (Ubuntu, systemd,
# locally-managed Cloudflare Tunnel). Run MANUALLY, as root, INSIDE tmux:
#
#   sudo bash deploy/server-setup.sh --check-only   # read-only inspection, changes nothing
#   sudo bash deploy/server-setup.sh                # inspection, then asks for "DEPLOY"
#   sudo bash deploy/server-setup.sh --dns-done     # use if you created the DNS record yourself
#
# Why tmux: restarting cloudflared briefly drops SSH sessions that run through the tunnel
# (ssh.taren.se). The script keeps running, verifies every existing hostname and rolls the
# cloudflared config back automatically if anything changed. Re-attach with: tmux attach -t gz
#
# STATS_TOKEN is generated here and NEVER printed; it is passed to curl only via stdin.
# A full log (without the token) is written to /root/gzcompanion-usage-setup-<time>.log.
set -Eeuo pipefail
umask 077

APP=gzcompanion-usage
GZ_HOST=gzcompanion.taren.se
SRV=/srv/$APP
ETC=/etc/$APP
ENV_FILE=$ETC/env
STATE=/var/lib/$APP
DB=$STATE/usage.sqlite
UNIT=/etc/systemd/system/$APP.service
BACKUP_DIR=/var/backups/$APP
CRON_FILE=/etc/cron.d/$APP
LOCAL=http://127.0.0.1:8787
PUBLIC=https://$GZ_HOST
NODE=/usr/bin/node
MARKER="# gzcompanion-usage"
TS=$(date -u +%Y%m%dT%H%M%SZ)
START_TIME=$(date '+%Y-%m-%d %H:%M:%S')
SRC=$(cd "$(dirname "$(readlink -f "$0")")/.." && pwd)

CHECK_ONLY=0
DNS_DONE=0
for arg in "$@"; do
  case $arg in
    --check-only) CHECK_ONLY=1 ;;
    --dns-done) DNS_DONE=1 ;;
    *) echo "Unknown option: $arg"; exit 2 ;;
  esac
done

[ "$(id -u)" = 0 ] || { echo "Run as root: sudo bash $0"; exit 1; }
LOG=/root/$APP-setup-$TS.log
: > "$LOG"; chmod 600 "$LOG"
exec > >(tee -a "$LOG") 2>&1

WORK=$(mktemp -d)
TOKEN=""
WRONG_TOKEN="wrong-$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9')"
TEST_IDS=()      # synthetic ids not yet deleted
ALL_TEST_IDS=()  # every synthetic id used (for the log scan)
CODE=000

step() { printf '\n=== %s ===\n' "$*"; }
ok()   { printf '  OK   %s\n' "$*"; }
info() { printf '  ..   %s\n' "$*"; }
die()  { printf '\n  STOP %s\n\nNothing further was changed. Log: %s\n' "$*" "$LOG" >&2; exit 1; }
trap 'printf "\n  STOP unexpected error at line %s (exit %s)\n" "$LINENO" "$?"' ERR
redact() { sed -E 's/(--token[= ])[^ ]+/\1<redacted>/g; s/(TUNNEL_TOKEN=)[^ ]+/\1<redacted>/g'; }

# --- SQLite helpers (run as the service user; fixed SQL, values as bound parameters) ----------
db_count() {
  runuser -u "$APP" -- "$NODE" --disable-warning=ExperimentalWarning -e '
const { DatabaseSync } = require("node:sqlite");
const db = new DatabaseSync(process.argv[1], { readOnly: true });
console.log(db.prepare("SELECT COUNT(*) AS n FROM installations").get().n);
db.close();' "$DB"
}
db_delete_ids() {
  runuser -u "$APP" -- "$NODE" --disable-warning=ExperimentalWarning -e '
const { DatabaseSync } = require("node:sqlite");
const db = new DatabaseSync(process.argv[1]);
db.exec("PRAGMA busy_timeout = 5000; PRAGMA secure_delete = ON");
const del = db.prepare("DELETE FROM installations WHERE installation_id = ?");
let n = 0;
for (const id of process.argv.slice(2)) n += Number(del.run(id).changes);
db.exec("PRAGMA wal_checkpoint(TRUNCATE)");
console.log(n);
db.close();' "$DB" "$@"
}
cleanup() {
  local rc=$?
  if [ ${#TEST_IDS[@]} -gt 0 ] && [ -f "$DB" ]; then
    printf '  ..   removing %s leftover synthetic test row(s): ' "${#TEST_IDS[@]}"
    db_delete_ids "${TEST_IDS[@]}" || true
  fi
  TOKEN=""
  rm -rf "$WORK"
  exit "$rc"
}
trap cleanup EXIT

# --- HTTP helpers (auth header goes to curl via stdin, never on the command line) -------------
auth_header() {
  case $1 in
    bearer) printf 'Authorization: Bearer %s\n' "$TOKEN" ;;
    basic)  printf 'Authorization: Basic %s\n' "$(printf 'stats:%s' "$TOKEN" | base64 -w0)" ;;
    wrong)  printf 'Authorization: Bearer %s\n' "$WRONG_TOKEN" ;;
    *)      printf 'X-Setup-Check: 1\n' ;;
  esac
}
http() { # METHOD URL AUTH [BODY]
  local args=(-sS -o "$WORK/body" -D "$WORK/headers" -w '%{http_code}' --max-time 20 -X "$1" -H @-)
  [ -n "${4-}" ] && args+=(-H 'Content-Type: application/json' --data-binary "$4")
  CODE=$(auth_header "$3" | curl "${args[@]}" "$2" 2>/dev/null) || CODE=000
}
expect() { # CODE DESCRIPTION
  [ "$CODE" = "$1" ] || die "$2: expected HTTP $1, got $CODE. Body: $(head -c 300 "$WORK/body" 2>/dev/null)"
  if grep -Eq '    at |node:internal|Error: |Index of /' "$WORK/body" 2>/dev/null; then
    die "$2: response looks like a stack trace or directory listing"
  fi
  ok "$2 -> $CODE"
}
heartbeat() { http POST "$1/api/gzcompanion/heartbeat" none "{\"installationId\":\"$2\",\"version\":\"deploy-check\"}"; }
api_count() {
  http GET "$1/api/gzcompanion/stats" bearer
  [ "$CODE" = 200 ] || die "stats API ($1) with token: HTTP $CODE"
  for id in "${ALL_TEST_IDS[@]}"; do grep -qiF "$id" "$WORK/body" && die "stats API exposed an installation id"; done
  sed -n 's/.*"uniqueInstallations":\([0-9]*\).*/\1/p' "$WORK/body"
}
new_test_id() { ID=$(cat /proc/sys/kernel/random/uuid); TEST_IDS+=("$ID"); ALL_TEST_IDS+=("$ID"); }
remove_test_ids() {
  local n; n=$(db_delete_ids "${TEST_IDS[@]}"); TEST_IDS=()
  ok "deleted $n synthetic row(s)"
}
host_code() { curl -s -o /dev/null -w '%{http_code}' --max-time 10 "https://$1/" 2>/dev/null || true; }
ingress_rule() { # CONFIG URL  (drop the line naming the file so old/new outputs compare)
  cloudflared tunnel --config "$1" ingress rule "$2" 2>&1 | grep -vE '^(Using|Validating) rules from' || true
}

###############################################################################################
step "1/9 Inspect server (read-only)"
. /etc/os-release && info "OS: $PRETTY_NAME"
for t in systemctl tmux curl getent ss awk sed diff base64 runuser useradd; do
  command -v "$t" >/dev/null || die "required tool missing: $t"
done
IN_MUX=0; PID=$$   # sudo drops $TMUX, so look for a tmux/screen ancestor process instead
while [ "$PID" -gt 1 ]; do
  case $(cat "/proc/$PID/comm" 2>/dev/null) in tmux*|screen*|SCREEN*) IN_MUX=1; break ;; esac
  PID=$(awk '{print $4}' "/proc/$PID/stat" 2>/dev/null || echo 1)
done
[ "$IN_MUX" = 1 ] || die "not inside tmux/screen. Start 'tmux new -s gz' first (cloudflared restart drops SSH)."

[ -x "$NODE" ] || die "$NODE not found (the systemd unit uses /usr/bin/node; nvm installs are not supported)"
NODE_VERSION=$("$NODE" --version)
NODE_MAJOR=${NODE_VERSION#v}; NODE_MAJOR=${NODE_MAJOR%%.*}
[ "$NODE_MAJOR" -ge 24 ] || die "Node $NODE_VERSION is too old (need >= 24 for node:sqlite)"
ok "node $NODE_VERSION at $NODE"

command -v cloudflared >/dev/null || die "cloudflared not installed"
CF_VERSION=$(cloudflared --version 2>&1 | head -1)
ok "$CF_VERSION"

CF_UNITS=$(systemctl list-units --type=service --all --no-legend --plain 'cloudflared*' | awk '{print $1}' | grep -v -e '^$' -e '^cloudflared-update' || true)
[ "$(printf '%s\n' "$CF_UNITS" | grep -c .)" = 1 ] || die "expected exactly one cloudflared service, found: ${CF_UNITS:-none}"
CF_UNIT=$CF_UNITS
systemctl is-active --quiet "$CF_UNIT" || die "$CF_UNIT is not active"
systemctl status "$CF_UNIT" --no-pager -n 0 | redact
UNIT_TEXT=$(systemctl cat "$CF_UNIT" | redact)
printf '%s\n' "$UNIT_TEXT" | grep -E '^(ExecStart|Environment|User)' | sed 's/^/       /'
if printf '%s' "$UNIT_TEXT" | grep -qE -- '--token|TUNNEL_TOKEN'; then
  die "the tunnel is REMOTELY managed (token mode): its routes live in the Cloudflare dashboard, not in a local file.
       Add the public hostname there (Zero Trust > Networks > Tunnels > your tunnel > Public hostname):
         gzcompanion.taren.se  path ^/api/gzcompanion/heartbeat\$          -> http://127.0.0.1:8787
         gzcompanion.taren.se  path ^/(stats|api/gzcompanion/stats)\$      -> http://127.0.0.1:8787
       This script stops here; report this output."
fi

CFG=$(printf '%s' "$UNIT_TEXT" | grep -oE -- '--config[= ][^ ]+' | head -1 | sed -E 's/--config[= ]//' || true)
if [ -z "$CFG" ]; then
  CANDIDATES=()
  for f in /root/.cloudflared/config.yml /root/.cloudflared/config.yaml /etc/cloudflared/config.yml \
           /etc/cloudflared/config.yaml /usr/local/etc/cloudflared/config.yml /usr/local/etc/cloudflared/config.yaml; do
    [ -f "$f" ] && CANDIDATES+=("$f")
  done
  [ ${#CANDIDATES[@]} = 1 ] || die "cannot tell which cloudflared config is used (found: ${CANDIDATES[*]:-none})"
  CFG=${CANDIDATES[0]}
fi
[ -f "$CFG" ] || die "cloudflared config $CFG does not exist"
ok "cloudflared config: $CFG"
echo "------- $CFG -------"; cat "$CFG"; echo "-------"

TUNNEL=$(sed -nE 's/^tunnel:[[:space:]]*["'\'']?([^"'\'' ]+).*/\1/p' "$CFG" | head -1)
[ -n "$TUNNEL" ] || die "no 'tunnel:' entry in $CFG"
ok "tunnel: $TUNNEL"
grep -qE '^ingress:' "$CFG" || die "no 'ingress:' list in $CFG"
cloudflared tunnel --config "$CFG" ingress validate >/dev/null 2>&1 || die "current config does not pass 'cloudflared tunnel ingress validate'"
ok "current ingress validates"

LOGLEVEL=$( { printf '%s\n' "$UNIT_TEXT"; cat "$CFG"; } | grep -oiE -- '(--)?loglevel[=: ]+[a-z]+' | head -1 || true)
if printf '%s' "$LOGLEVEL" | grep -qiE 'debug|trace'; then die "cloudflared runs with debug/trace logging ($LOGLEVEL)"; fi
ok "cloudflared loglevel: ${LOGLEVEL:-default (info, no per-request logging)}"

TUNNEL_DONE=0
if grep -qF "$GZ_HOST" "$CFG"; then
  if grep -qF "$MARKER" "$CFG" && [ "$(grep -cF "hostname: $GZ_HOST" "$CFG")" = 3 ]; then
    TUNNEL_DONE=1; ok "$GZ_HOST already configured by this script earlier"
  else
    die "$GZ_HOST already appears in $CFG (not added by this script)"
  fi
fi

CATCH_LINE=$(grep -nE '^[[:space:]]*-[[:space:]]+service:[[:space:]]*["'\'']?http_status:[0-9]{3}["'\'']?[[:space:]]*$' "$CFG" | tail -1 | cut -d: -f1 || true)
[ -n "$CATCH_LINE" ] || die "no catch-all rule ('- service: http_status:NNN') found in ingress"
CATCH_INDENT=$(sed -n "${CATCH_LINE}p" "$CFG" | sed -E 's/^([[:space:]]*).*/\1/')
awk -v L="$CATCH_LINE" -v I="$CATCH_INDENT" 'NR > L && index($0, I "- ") == 1 { bad = 1 } END { exit bad }' "$CFG" \
  || die "the catch-all rule (line $CATCH_LINE) is not the last ingress entry"
ok "catch-all rule at line $CATCH_LINE (new rules go directly above it)"

mapfile -t HOSTS < <(sed -nE 's/^[[:space:]]*-?[[:space:]]*hostname:[[:space:]]*["'\'']?([^"'\'' ]+).*/\1/p' "$CFG" | grep -vF "$GZ_HOST" | sort -u)
info "existing hostnames (current HTTP status of https://<host>/):"
for h in "${HOSTS[@]}"; do
  case $h in *'*'*) printf '       %-40s (wildcard, not probed)\n' "$h" ;; *) printf '       %-40s %s\n' "$h" "$(host_code "$h")" ;; esac
done

GZ_RESOLVES=0
getent ahosts "$GZ_HOST" >/dev/null 2>&1 && GZ_RESOLVES=1
if [ "$GZ_RESOLVES" = 1 ] && [ "$TUNNEL_DONE" = 0 ] && [ "$DNS_DONE" = 0 ]; then
  die "$GZ_HOST already resolves in DNS (existing record or wildcard?) - check it in Cloudflare first"
fi
ORIGIN_CERT=""
for f in /root/.cloudflared/cert.pem /etc/cloudflared/cert.pem /usr/local/etc/cloudflared/cert.pem /home/*/.cloudflared/cert.pem; do
  [ -f "$f" ] && { ORIGIN_CERT=$f; break; }
done
if [ "$GZ_RESOLVES" = 0 ] && [ -z "$ORIGIN_CERT" ]; then
  die "no cloudflared origin cert (cert.pem) to create the DNS route. In the Cloudflare dashboard add:
         CNAME  gzcompanion  ->  $TUNNEL.cfargotunnel.com   (Proxied)
       then re-run with --dns-done"
fi
ok "DNS: $([ "$GZ_RESOLVES" = 1 ] && echo "$GZ_HOST resolves" || echo "will route via cloudflared using $ORIGIN_CERT")"

LISTENERS=$(ss -ltnH 'sport = :8787' || true)
if [ -n "$LISTENERS" ] && ! systemctl is-active --quiet "$APP"; then die "port 8787 is used by something else: $LISTENERS"; fi
ok "port 8787: $([ -n "$LISTENERS" ] && echo "used by existing $APP" || echo free)"

[ -f "$SRC/src/index.js" ] && grep -q '"name": "gzcompanion-usage"' "$SRC/package.json" \
  || die "$SRC does not look like the usage-server folder"
[ -f "$SRC/deploy/$APP.service" ] && [ -f "$SRC/deploy/backup.sh" ] || die "deploy files missing in $SRC"
FORBIDDEN=$(find "$SRC" \( -name '.env' -o -name '*.sqlite*' -o -name node_modules -o -name .git -o -name qa-backup \) -print)
[ -z "$FORBIDDEN" ] || die "source folder contains files that must not be deployed: $FORBIDDEN"
ok "source: $SRC"

if [ "$CHECK_ONLY" = 1 ]; then echo; echo "Check only - nothing was changed. Log: $LOG"; exit 0; fi
echo
read -r -p "Everything above as expected? Type DEPLOY to continue: " ANSWER </dev/tty
[ "$ANSWER" = DEPLOY ] || die "not confirmed"

###############################################################################################
step "2/9 Service account"
if id -u "$APP" >/dev/null 2>&1; then
  [ "$(getent passwd "$APP" | cut -d: -f7)" = /usr/sbin/nologin ] || die "existing user $APP has a login shell"
  ok "user $APP exists (nologin)"
else
  useradd --system --user-group --no-create-home --home-dir /nonexistent --shell /usr/sbin/nologin "$APP"
  ok "created system user $APP (nologin, no home)"
fi

step "3/9 Application files -> $SRV (root-owned, read-only for the service)"
NEW_DIR=$SRV.new-$TS
mkdir "$NEW_DIR"
cp -a "$SRC/package.json" "$SRC/src" "$SRC/deploy" "$NEW_DIR/"
[ -f "$SRC/README.md" ] && cp -a "$SRC/README.md" "$NEW_DIR/"
[ -f "$SRC/.env.example" ] && cp -a "$SRC/.env.example" "$NEW_DIR/"
chown -R root:root "$NEW_DIR"
find "$NEW_DIR" -type d -exec chmod 0755 {} +
find "$NEW_DIR" -type f -exec chmod 0644 {} +
chmod 0755 "$NEW_DIR/deploy/backup.sh" "$NEW_DIR/deploy/server-setup.sh"
if [ -d "$SRV" ]; then mv "$SRV" "$SRV.old-$TS"; info "previous version kept at $SRV.old-$TS"; fi
mv "$NEW_DIR" "$SRV"
if runuser -u "$APP" -- test -w "$SRV/src/index.js"; then die "service user can write application files"; fi
ok "installed; service user cannot modify it"

step "4/9 Secret configuration $ENV_FILE"
install -d -m 0750 -o root -g "$APP" "$ETC"
if [ -f "$ENV_FILE" ]; then
  EXISTING_LEN=$(sed -n 's/^STATS_TOKEN=//p' "$ENV_FILE" | tr -d '\n' | wc -c)
  [ "$EXISTING_LEN" -ge 64 ] || die "existing $ENV_FILE has no proper STATS_TOKEN"
  ok "keeping existing $ENV_FILE (token unchanged)"
else
  TMP_ENV=$(mktemp "$ETC/.env.XXXXXX")
  NEW_TOKEN=$("$NODE" -e 'process.stdout.write(require("crypto").randomBytes(32).toString("hex"))')
  {
    echo "# gzcompanion-usage - created $TS. Secret: never commit or share."
    echo "STATS_TOKEN=$NEW_TOKEN"
    echo "HOST=127.0.0.1"
    echo "PORT=8787"
    echo "DB_PATH=$DB"
    echo "HEARTBEATS_PER_MINUTE=600"
    echo "NEW_INSTALLATIONS_PER_HOUR=500"
  } > "$TMP_ENV"
  NEW_TOKEN=""
  chown root:"$APP" "$TMP_ENV"; chmod 0640 "$TMP_ENV"; mv "$TMP_ENV" "$ENV_FILE"
  ok "created $ENV_FILE (0640 root:$APP) with a new 32-byte random STATS_TOKEN (not shown)"
fi
TOKEN=$(sed -n 's/^STATS_TOKEN=//p' "$ENV_FILE")

step "5/9 systemd service"
if [ -f "$UNIT" ] && ! cmp -s "$UNIT" "$SRV/deploy/$APP.service"; then
  cp -a "$UNIT" "$UNIT.bak-$TS"; info "previous unit backed up to $UNIT.bak-$TS"
fi
install -m 0644 -o root -g root "$SRV/deploy/$APP.service" "$UNIT"
systemctl daemon-reload
systemctl enable "$APP" >/dev/null 2>&1
systemctl restart "$APP"
for _ in $(seq 1 40); do [ -n "$(ss -ltnH 'sport = :8787')" ] && break; sleep 0.25; done
systemctl status "$APP" --no-pager -n 5 || die "$APP is not running"
LISTENERS=$(ss -ltnH 'sport = :8787')
[ "$(printf '%s\n' "$LISTENERS" | grep -c .)" = 1 ] && printf '%s' "$LISTENERS" | grep -q ' 127\.0\.0\.1:8787 ' \
  || die "unexpected listeners on 8787: $LISTENERS"
ok "listening only on 127.0.0.1:8787"
[ -f "$DB" ] || die "database $DB was not created"
ok "database $DB ($(stat -c '%a %U' "$STATE") on $STATE)"

step "6/9 Local production test (synthetic heartbeat, then reset)"
BEFORE=$(db_count)
[ "$BEFORE" = 0 ] || die "database already holds $BEFORE installation(s) - refusing to test on real data"
new_test_id
heartbeat "$LOCAL" "$ID"; expect 200 "heartbeat without auth"
grep -qx '{"ok":true}' "$WORK/body" || die "unexpected heartbeat body: $(cat "$WORK/body")"
[ "$(db_count)" = 1 ] && [ "$(api_count "$LOCAL")" = 1 ] || die "expected exactly one installation"
ok "exactly 1 installation"
heartbeat "$LOCAL" "$ID"; expect 200 "repeat heartbeat, same id"
[ "$(db_count)" = 1 ] && [ "$(api_count "$LOCAL")" = 1 ] || die "repeat heartbeat changed the unique count"
ok "still exactly 1 unique installation"
http GET "$LOCAL/api/gzcompanion/stats" none;  expect 401 "stats API without token"
http GET "$LOCAL/api/gzcompanion/stats" wrong; expect 401 "stats API with wrong token"
http GET "$LOCAL/api/gzcompanion/stats" bearer; expect 200 "stats API with token"
http GET "$LOCAL/stats" none; expect 401 "dashboard without password"
grep -qi '^www-authenticate: basic' "$WORK/headers" || die "dashboard does not offer a password prompt"
http GET "$LOCAL/stats" basic; expect 200 "dashboard with password"
grep -q 'Unika installationer totalt' "$WORK/body" || die "dashboard content missing"
http GET "$LOCAL/api/gzcompanion/heartbeat" none; expect 405 "GET on heartbeat"
http POST "$LOCAL/api/gzcompanion/heartbeat" none '{"installationId":'; expect 400 "malformed JSON"
remove_test_ids
[ "$(db_count)" = 0 ] && [ "$(api_count "$LOCAL")" = 0 ] || die "database not back at zero"
ok "production database reset: 0 installations"

###############################################################################################
step "7/9 Cloudflare Tunnel: add $GZ_HOST to tunnel $TUNNEL"
if [ "$TUNNEL_DONE" = 1 ]; then
  ok "already configured - config left untouched"
else
  CFG_BACKUP=$CFG.bak-$APP-$TS
  cp -a "$CFG" "$CFG_BACKUP"
  ok "backup: $CFG_BACKUP"
  NEW_CFG=$(dirname "$CFG")/.$(basename "$CFG").new-$TS
  awk -v L="$CATCH_LINE" -v I="$CATCH_INDENT" -v Q="'" -v M="$MARKER" -v H="$GZ_HOST" -v T="$TS" '
    NR == L {
      print I M " (added " T "): only these paths reach 127.0.0.1:8787, rest of the host is 404"
      print I "- hostname: " H;  print I "  path: " Q "^/api/gzcompanion/heartbeat$" Q;      print I "  service: http://127.0.0.1:8787"
      print I "- hostname: " H;  print I "  path: " Q "^/(stats|api/gzcompanion/stats)$" Q; print I "  service: http://127.0.0.1:8787"
      print I "- hostname: " H;  print I "  service: http_status:404"
    }
    { print }' "$CFG" > "$NEW_CFG"
  chown --reference="$CFG" "$NEW_CFG"; chmod --reference="$CFG" "$NEW_CFG"
  ADDED=$(diff "$CFG" "$NEW_CFG" | grep -c '^>' || true)
  REMOVED=$(diff "$CFG" "$NEW_CFG" | grep -c '^<' || true)
  [ "$ADDED" = 9 ] && [ "$REMOVED" = 0 ] || { rm -f "$NEW_CFG"; die "config diff is not exactly 9 added lines (added=$ADDED removed=$REMOVED)"; }
  diff "$CFG" "$NEW_CFG" || true
  cloudflared tunnel --config "$NEW_CFG" ingress validate >/dev/null 2>&1 || { rm -f "$NEW_CFG"; die "new config fails validation"; }
  for h in "${HOSTS[@]}"; do
    case $h in *'*'*) continue ;; esac
    [ "$(ingress_rule "$CFG" "https://$h/")" = "$(ingress_rule "$NEW_CFG" "https://$h/")" ] \
      || { rm -f "$NEW_CFG"; die "routing for existing host $h would change"; }
  done
  ok "every existing hostname still matches the same rule"
  for p in /api/gzcompanion/heartbeat /stats /api/gzcompanion/stats; do
    ingress_rule "$NEW_CFG" "https://$GZ_HOST$p" | grep -q 'http://127.0.0.1:8787' \
      || { rm -f "$NEW_CFG"; die "$GZ_HOST$p would not reach the service (an earlier wildcard rule?)"; }
  done
  ingress_rule "$NEW_CFG" "https://$GZ_HOST/anything" | grep -q 'http_status:404' \
    || { rm -f "$NEW_CFG"; die "$GZ_HOST/anything would not be 404"; }
  ok "$GZ_HOST: only the three paths reach the service"

  declare -A BASE
  for h in "${HOSTS[@]}"; do case $h in *'*'*) ;; *) BASE[$h]=$(host_code "$h") ;; esac; done
  mv "$NEW_CFG" "$CFG"
  info "restarting $CF_UNIT (SSH through the tunnel may drop - reconnect and: tmux attach -t gz)"
  systemctl restart "$CF_UNIT"
  HEALTHY=0
  for _ in $(seq 1 18); do
    sleep 5
    systemctl is-active --quiet "$CF_UNIT" || continue
    SAME=1
    for h in "${!BASE[@]}"; do [ "$(host_code "$h")" = "${BASE[$h]}" ] || SAME=0; done
    [ "$SAME" = 1 ] && { HEALTHY=1; break; }
  done
  if [ "$HEALTHY" = 0 ]; then
    cp -a "$CFG_BACKUP" "$CFG"; systemctl restart "$CF_UNIT"
    die "existing hostnames did not come back unchanged - ROLLED BACK to $CFG_BACKUP and restarted $CF_UNIT"
  fi
  for h in "${!BASE[@]}"; do printf '  OK   %-40s %s (unchanged)\n' "$h" "${BASE[$h]}"; done
fi

if [ "$GZ_RESOLVES" = 0 ]; then
  cloudflared tunnel --origincert "$ORIGIN_CERT" route dns "$TUNNEL" "$GZ_HOST" || die "DNS route failed (tunnel config is live; re-run with --dns-done after adding the CNAME manually)"
  ok "DNS route created: $GZ_HOST -> tunnel $TUNNEL"
fi
command -v resolvectl >/dev/null && resolvectl flush-caches 2>/dev/null || true

step "8/9 External verification via $PUBLIC (then reset)"
new_test_id
for _ in $(seq 1 24); do heartbeat "$PUBLIC" "$ID"; [ "$CODE" = 200 ] && break; sleep 5; done
expect 200 "public heartbeat without auth"
[ "$(api_count "$PUBLIC")" = 1 ] || die "public stats do not show exactly 1 installation"
ok "stats API (token) shows exactly 1 installation"
http GET "$PUBLIC/api/gzcompanion/stats" none;  expect 401 "public stats API without token"
http GET "$PUBLIC/api/gzcompanion/stats" wrong; expect 401 "public stats API with wrong token"
http GET "$PUBLIC/stats" none;  expect 401 "public dashboard without password"
http GET "$PUBLIC/stats" basic; expect 200 "public dashboard with password"
grep -q 'Unika installationer totalt' "$WORK/body" || die "public dashboard content missing"
for p in / "/random-$RANDOM" /src/ /src/index.js /.env /package.json /deploy/ /stats/ /api/ /api/gzcompanion/; do
  http GET "$PUBLIC$p" none; expect 404 "GET $p"
done
http POST "$PUBLIC/api/gzcompanion/heartbeat" none '{"installationId":'; expect 400 "public malformed JSON"
grep -qi '^location:' "$WORK/headers" && die "unexpected redirect"
remove_test_ids
[ "$(db_count)" = 0 ] && [ "$(api_count "$PUBLIC")" = 0 ] || die "database not back at zero"
ok "production database reset: 0 installations"

###############################################################################################
step "9/9 Privacy check and backup"
journalctl -u "$APP" --no-pager -o cat > "$WORK/journal" || true
echo "------- journalctl -u $APP -------"; cat "$WORK/journal"; echo "-------"
for id in "${ALL_TEST_IDS[@]}"; do grep -qiF "$id" "$WORK/journal" && die "journal contains a test installation id"; done
grep -qF "$TOKEN" "$WORK/journal" && die "journal contains the STATS_TOKEN"
grep -qiE 'installationId|authorization|user-agent|deploy-check|\{' "$WORK/journal" && die "journal contains request data"
IPS=$(grep -Eo '([0-9]{1,3}\.){3}[0-9]{1,3}' "$WORK/journal" | grep -vx '127\.0\.0\.1' | sort -u || true)
[ -z "$IPS" ] || die "journal contains IP addresses: $IPS"
ok "app journal: no installation ids, payloads, tokens, headers or client IPs (only the 127.0.0.1 listen line)"
CF_HITS=$(journalctl -u "$CF_UNIT" --since "$START_TIME" --no-pager -o cat | grep -c 'gzcompanion' || true)
ok "cloudflared journal lines mentioning gzcompanion since start: $CF_HITS (no per-request logging at info level)"

"$SRV/deploy/backup.sh"
BACKUP_FILE=$(ls -1t "$BACKUP_DIR"/usage-*.sqlite | head -1)
[ "$(stat -c '%a %U' "$BACKUP_DIR")" = "700 root" ] || die "backup dir permissions: $(stat -c '%a %U' "$BACKUP_DIR")"
[ "$(stat -c '%a %U' "$BACKUP_FILE")" = "600 root" ] || die "backup file permissions: $(stat -c '%a %U' "$BACKUP_FILE")"
ok "backup $BACKUP_FILE (dir 700, file 600, root)"
printf '15 3 * * * root %s/deploy/backup.sh\n' "$SRV" > "$CRON_FILE.tmp"
chmod 0644 "$CRON_FILE.tmp"; mv "$CRON_FILE.tmp" "$CRON_FILE"
ok "daily backup at 03:15 via $CRON_FILE (30 days kept)"

step "DONE - production backend live (no Minecraft test, nothing committed)"
cat <<EOF
  Node:            $NODE_VERSION
  Service:         $APP $(systemctl is-active "$APP"), enabled=$(systemctl is-enabled "$APP")
  Code:            $SRV  (unit $UNIT)
  Config:          $ENV_FILE
  Database:        $DB  -> $(db_count) installations
  Hostname:        $PUBLIC
  Tunnel:          $TUNNEL  (config $CFG${CFG_BACKUP:+, backup $CFG_BACKUP})
  Backups:         $BACKUP_DIR
  Log of this run: $LOG

  Dashboard:       $PUBLIC/stats  (any user name; password = STATS_TOKEN)
  Show password:   sudo sed -n 's/^STATS_TOKEN=//p' $ENV_FILE
EOF

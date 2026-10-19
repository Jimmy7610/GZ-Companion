#!/bin/sh
# Consistent online backup of the gzcompanion-usage database (safe while the service runs, WAL mode).
# Example daily cron (root):  15 3 * * * /srv/gzcompanion-usage/deploy/backup.sh
set -eu

DB=/var/lib/gzcompanion-usage/usage.sqlite
DEST_DIR=/var/backups/gzcompanion-usage
KEEP_DAYS=30

mkdir -p "$DEST_DIR"
chmod 0700 "$DEST_DIR"
OUT="$DEST_DIR/usage-$(date -u +%Y%m%d).sqlite"
rm -f "$OUT"
# VACUUM INTO writes a compact, consistent copy; the path is a fixed value, never user input.
/usr/bin/node --disable-warning=ExperimentalWarning -e '
const { DatabaseSync } = require("node:sqlite");
const db = new DatabaseSync(process.argv[1], { readOnly: true });
db.prepare("VACUUM INTO ?").run(process.argv[2]);
db.close();
' "$DB" "$OUT"
chmod 0600 "$OUT"
find "$DEST_DIR" -name 'usage-*.sqlite' -mtime +"$KEEP_DAYS" -delete

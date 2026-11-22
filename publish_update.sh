#!/usr/bin/env bash
set -euo pipefail

MSG="${1:-Aggiornamento Spotify}"

git add -A
if git diff --cached --quiet; then
  echo "Nessuna nuova modifica da committare. Eseguo push dei commit esistenti..."
else
  git commit -m "$MSG"
fi

BRANCH="$(git rev-parse --abbrev-ref HEAD)"
git push -u origin "$BRANCH"
echo "Push completato su $BRANCH! GitHub Actions compilerà e pubblicherà la nuova release OTA."

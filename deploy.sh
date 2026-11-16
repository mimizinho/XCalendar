#!/data/data/com.termux/files/usr/bin/bash
set -e
cd ~/XCalendar
git add -A
git commit -m "${1:-atualizacao}" || echo "Nada novo para commitar"
git push
SHA=$(git rev-parse HEAD)
echo "Aguardando o build..."
ID=""
for i in $(seq 1 30); do
  ID=$(gh run list --commit "$SHA" --limit 1 --json databaseId -q '.[0].databaseId')
  [ -n "$ID" ] && break
  sleep 2
done
[ -z "$ID" ] && { echo "Build não encontrado"; exit 1; }
gh run watch "$ID" --exit-status
TMP=$HOME/apkdl; rm -rf "$TMP"; mkdir -p "$TMP"
gh run download "$ID" -n apk -D "$TMP"
NOME=XCalendar-$(date +%d%m-%H%M).apk
rm -f ~/storage/downloads/XCalendar-*.apk
cp -f "$TMP/app-debug.apk" ~/storage/downloads/$NOME
rm -rf "$TMP"
echo "Pronto: $NOME em Downloads"
termux-open ~/storage/downloads/$NOME

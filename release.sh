#!/usr/bin/env bash
# ============================================================
# release.sh — сборка и публикация KGPT
#
# Режимы:
#   ./release.sh "Заметки о фиксе"          — HOTFIX: версия не меняется,
#                                             APK грузится в существующий релиз
#   ./release.sh X.Y.Z "Заметки"            — НОВЫЙ релиз: тег vX.Y.Z + gh release create
#   ./release.sh --keep-old "Заметки"       — HOTFIX без перетирания старого APK
# ============================================================
set -euo pipefail

KEEP_OLD=0
ARGS=()
for a in "$@"; do
  case "$a" in
    --keep-old) KEEP_OLD=1 ;;
    *) ARGS+=("$a") ;;
  esac
done
NOTES="${ARGS[0]:-}"
MODE="${ARGS[1]:-}"

info()  { echo "ℹ️  $*"; }
ok()    { echo "✅ $*"; }
err()   { echo "❌ $*" >&2; exit 1; }

command -v git >/dev/null || err "git не найден"
command -v gh  >/dev/null || err "GitHub CLI (gh) не найден. Установи: sudo apt install gh && gh auth login"
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || err "Запускай из корня репозитория"

GRADLE_FILE="app/build.gradle"
[ -f "$GRADLE_FILE" ] || err "Не найден $GRADLE_FILE"

# ---------- Разбор версии (только чтение, никаких записей!) ----------
VN=$(grep -oP 'versionName\s*=\s*"\K[^"]+' "$GRADLE_FILE" | head -n1)
VC=$(grep -oP 'versionCode\s*=\s*\K[0-9]+' "$GRADLE_FILE" | head -n1)
[ -n "$VN" ] || err "Не удалось разобрать versionName из $GRADLE_FILE"
[ -n "$VC" ] || err "Не удалось разобрать versionCode из $GRADLE_FILE"

BASE="${VN%%(*}"          # "4.1.5(4152)" -> "4.1.5"
PAREN="${VN##*(}"         # "4.1.5(4152)" -> "4152)"
PAREN="${PAREN%)}"        # "4152)"

IFS='.' read -r VMAJ VMIN VPATCH <<< "$BASE"
if ! [[ "$VMAJ" =~ ^[0-9]+$ && "$VMIN" =~ ^[0-9]+$ && "$VPATCH" =~ ^[0-9]+$ && "$PAREN" =~ ^[0-9]+$ ]]; then
  err "Нечитаемая версия: versionName=\"$VN\" (ожидался вид \"X.Y.Z(BUILD)\")"
fi

if [ "$VC" != "$PAREN" ]; then
  echo "❌ versionCode ($VC) не совпадает с числом в скобках versionName ($PAREN)."
  echo "   Приведи $GRADLE_FILE к виду:"
  echo "     versionCode = $PAREN"
  echo "     versionName = \"$BASE($PAREN)\""
  exit 1
fi

NEW_RELEASE=0
if [[ "${NOTES:-}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  NEW_RELEASE=1
  if [ "$NOTES" != "$BASE" ]; then
    echo "❌ В аргументе указана версия $NOTES, а в build.gradle стоит $BASE."
    echo "   Сначала поправь versionName/versionCode руками, затем запусти снова."
    exit 1
  fi
  MODE="${ARGS[1]:-}"
fi

TAG="v$BASE"
APK_NAME="KGPT-release-v${BASE}.${PAREN}.apk"

echo "====================================="
echo "  versionName = $VN"
echo "  versionCode = $VC"
echo "  тег/релиз   = $TAG"
echo "  APK         = $APK_NAME"
[ "$NEW_RELEASE" = 1 ] && echo "  режим       = НОВЫЙ РЕЛИЗ"
[ "$NEW_RELEASE" = 0 ] && echo "  режим       = HOTFIX (обновление ассетов существующего релиза)"
echo "====================================="

read -r -p "Продолжить? [y/N] " ANSWER
[[ "$ANSWER" =~ ^[Yy]$ ]] || err "Отменено пользователем"

# ---------- Сборка ----------
info "Собираем release..."
./gradlew assembleRelease

# ---------- Поиск APK (глоб, любое имя от Gradle) ----------
APK_SRC=$(ls -t app/build/outputs/apk/release/*.apk 2>/dev/null | head -n1 || true)
if [ -z "$APK_SRC" ]; then
  echo "❌ В app/build/outputs/apk/release/ нет ни одного .apk. Содержимое каталога:"
  ls -la app/build/outputs/apk/release/ 2>/dev/null || true
  exit 1
fi
info "Gradle выдал: $APK_SRC"
cp "$APK_SRC" "$APK_NAME"

# ---------- Проверка подписи ----------
if command -v apksigner >/dev/null 2>&1; then
  if apksigner verify "$APK_NAME" >/dev/null 2>&1; then
    ok "APK подписан: $(apksigner verify --print-certs "$APK_NAME" 2>/dev/null | grep -m1 'SHA-256' || echo 'сертификат есть')"
  else
    echo "❌ APK НЕ подписан (скорее всего app-release-unsigned.apk)."
    echo "   Настрой signingConfig (kgpt-release.jks + keystore.properties) и пересобери."
    rm -f "$APK_NAME"
    exit 1
  fi
else
  info "apksigner не найден в PATH — подпись не проверена (продолжаем)"
fi

# ---------- Git ----------
DIRTY=$(git status --porcelain)
if [ -n "$DIRTY" ]; then
  echo "Незакоммиченные изменения:"
  git status --short
  if [ -t 0 ]; then
    git add -p || git add -A
  else
    info "Нет TTY — добавляю всё целиком (git add -A)"
    git add -A
  fi
  if ! git diff --cached --quiet; then
    git commit -m "chore: release $VN"
  fi
fi

BRANCH=$(git rev-parse --abbrev-ref HEAD)
git push origin "$BRANCH"
HASH=$(git rev-parse --short HEAD)
ok "Запушено: $BRANCH @ $HASH"

# ---------- Публикация ----------
if [ "$NEW_RELEASE" = 1 ]; then
  if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
    err "Тег $TAG уже существует. Для обновления сборки используй hotfix-режим: ./release.sh \"notes\""
  fi
  git tag -a "$TAG" -m "Release $VN"
  git push origin "$TAG"
  if [ -n "$MODE" ]; then
    gh release create "$TAG" "$APK_NAME" --title "$VN" --notes "$MODE"
  else
    gh release create "$TAG" "$APK_NAME" --title "$VN"
  fi
else
  # HOTFIX: загружаем APK в существующий релиз
  gh release view "$TAG" >/dev/null 2>&1 || err "Релиз $TAG не найден на GitHub. Создай его или используй режим нового релиза."

  if [ "$KEEP_OLD" = 1 ]; then
    gh release upload "$TAG" "$APK_NAME"
  else
    gh release upload "$TAG" "$APK_NAME" --clobber
  fi
  ok "APK загружен в релиз $TAG"

  # Дописываем хэш коммита в заметки (если его там ещё нет)
  MARKER="сборка: коммит $HASH"
  BODY=$(gh release view "$TAG" --json body -q .body)
  if ! grep -qF "$MARKER" <<< "$BODY"; then
    printf '%s\nИсходники этой сборки: коммит %s\n' "$BODY" "$HASH" > /tmp/kgpt_release_body.md
    gh release edit "$TAG" --notes-file /tmp/kgpt_release_body.md
    ok "В заметки релиза добавлен хэш коммита: $HASH"
  fi
fi

echo ""
ok "Готово. Помни: в hotfix-режиме тег $TAG остаётся на старом коммите —"
echo "   кнопка Source code в релизе отдаёт исходники до фикса (см. хэш $HASH в заметках)."

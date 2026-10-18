#!/usr/bin/env bash
# Screenshots of the demo build for the READMEs: tools/capture.sh <out-dir> en|ru
# Reseeds app.notch.demo in that language, then walks the main screens with guarded taps.
set -e
cd "$(dirname "$0")/.."
source tools/phone.sh
OUT="$1"; LANG_="${2:-en}"
PKG=app.notch.demo
mkdir -p "$OUT"
SHOTS="$OUT"
case "$PKG" in *.demo|*.shots) ;; *) echo "refusing to clear $PKG"; exit 1 ;; esac
"$ADB" shell pm clear $PKG >/dev/null
"$ADB" shell cmd locale set-app-locales $PKG --locales "$LANG_"
"$ADB" shell cmd appops set $PKG 10008 allow
"$ADB" shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 12
if [ "$LANG_" = ru ]; then T_RINGS="Кольца"; T_WORDS="Словарик"; T_FADING="Уходит"; T_FRAME="Косяк"; T_INT="Интервью на день рождения"; T_CARD="Открытка месяца"; T_HANDS="Ладошки"; T_SEC="Секунда в день"; T_BACK="Назад"
else T_RINGS="Rings"; T_WORDS="Words"; T_FADING="Fading"; T_FRAME="Door frame"; T_INT="Birthday interview"; T_CARD="The month's postcard"; T_HANDS="Hands"; T_SEC="A second a day"; T_BACK="Back"; fi
up() { guard $PKG && "$ADB" shell input swipe 540 1800 540 500 400; sleep 1; }
shot 1-frame
up; up; shot 2-frame-past
tap_text $PKG "$T_RINGS"; sleep 3; shot 3-rings
up; up; shot 4-made
tap_text $PKG "$T_HANDS"; sleep 3; shot 5-hands
tap_text $PKG "$T_BACK"; sleep 2; up; up
tap_text $PKG "$T_INT"; sleep 3; shot 6-interview
tap_text $PKG "$T_BACK"; sleep 2; up; up
tap_text $PKG "$T_CARD"; sleep 6; shot 7-postcard
tap_text $PKG "$T_BACK"; sleep 2
tap_text $PKG "$T_WORDS"; sleep 3; shot 8-words
tap_text $PKG "$T_FADING"; sleep 3; shot 9-fading
tap_text $PKG "$T_FRAME"; sleep 2
echo done

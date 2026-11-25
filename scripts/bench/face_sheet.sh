#!/bin/bash
# Contact sheet of the avatar's face for every mood or reaction, set through the face_set bridge tool and captured with screencap.
# usage: scripts/bench/face_sheet.sh moods|reactions [crop=360x340+180+70] [out-dir=build/bench/face]
# The crop is in screen pixels and depends on where the avatar's head is (default: a typical full-body framing on a 720 px wide
# screen). Needs: adb + root, ImageMagick (convert, montage) on this computer, the bridge token synced.
source "$(dirname "$0")/lib.sh"
KIND=${1:-moods}; CROP=${2:-360x340+180+70}; OUT=${3:-$ROOT/build/bench/face}; mkdir -p "$OUT"; push_helper
case "$KIND" in
  moods) NAMES="neutral happy sad angry surprised worried scared confused bored sleepy shy proud love focused disgusted";;
  reactions) NAMES="cry laugh giggle wink nod shakeno blink eyeroll startle sigh yawn celebrate dizzy hearteyes stareyes mindblown glance lookaround";;
  *) echo "usage: $0 moods|reactions [crop] [out-dir]"; exit 2;;
esac
command -v montage >/dev/null || { echo "FAIL ImageMagick not installed"; exit 1; }
for n in $NAMES; do
  if [ "$KIND" = moods ]; then call face_set "{\"mood\":\"$n\"}" >/dev/null; sleep 3
  else call face_set '{"mood":"neutral"}' >/dev/null; sleep 0.9; call face_set "{\"reaction\":\"$n\"}" >/dev/null; sleep 0.5; fi
  adb exec-out screencap -p | convert png:- -crop "$CROP" +repage "$OUT/$n.png" && echo "ok $n" || echo "FAIL $n"
  [ "$KIND" = reactions ] && sleep 1.6
done
(cd "$OUT" && montage -label '%t' -pointsize 18 $(for n in $NAMES; do echo "$n.png"; done) -tile 6x -geometry 330x312+3+3 -background '#101418' -fill white "sheet-$KIND.png") \
  && echo "ok sheet: $OUT/sheet-$KIND.png"
call face_set '{"mood":"neutral"}' >/dev/null

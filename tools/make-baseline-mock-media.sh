#!/usr/bin/env bash
# Regenerates the synthetic media of the baseline profile loopback mock: a 24-second HLS stream
# (six 4-second MPEG-TS segments) and two poster images. Needs ffmpeg with libx264.
# The committed media were made with a static ffmpeg 4.2.2 (libx264, aac); other versions give other bytes.
# Usage: tools/make-baseline-mock-media.sh <output-dir>
# Copy the result into baselineprofile/src/main/assets/baseline/media/.
set -euo pipefail

OUT="${1:?usage: make-baseline-mock-media.sh <output-dir>}"
mkdir -p "$OUT"

ffmpeg -y -f lavfi -i testsrc2=size=320x180:rate=25 -f lavfi -i sine=frequency=440:sample_rate=44100 \
  -t 24 -c:v libx264 -profile:v baseline -pix_fmt yuv420p -b:v 48k -g 100 -keyint_min 100 -sc_threshold 0 \
  -c:a aac -b:a 16k -ac 1 \
  -f hls -hls_time 4 -hls_playlist_type vod -hls_segment_filename "$OUT/seg%d.ts" "$OUT/stream.m3u8"
ffmpeg -y -f lavfi -i testsrc2=size=240x360 -frames:v 1 -q:v 10 "$OUT/poster.jpg"
ffmpeg -y -f lavfi -i testsrc2=size=640x360 -frames:v 1 -q:v 10 "$OUT/wide.jpg"

du -cb "$OUT"/stream.m3u8 "$OUT"/seg*.ts "$OUT"/poster.jpg "$OUT"/wide.jpg

#!/usr/bin/env bash

set -euo pipefail

if [[ $# -lt 2 ]]; then
    echo "Usage: CLEAR30_VIDEO_REVIEW_ROOT=/absolute/path $0 <batch-name> <r2-object-key>..." >&2
    exit 64
fi

if [[ -z "${CLEAR30_VIDEO_REVIEW_ROOT:-}" || "${CLEAR30_VIDEO_REVIEW_ROOT}" != /* ]]; then
    echo "CLEAR30_VIDEO_REVIEW_ROOT must be an absolute path." >&2
    exit 64
fi

batch_name="$1"
shift

if [[ ! "$batch_name" =~ ^[a-zA-Z0-9_-]+$ ]]; then
    echo "Invalid batch name: $batch_name" >&2
    exit 64
fi

review_root="${CLEAR30_VIDEO_REVIEW_ROOT}/${batch_name}"
mkdir -p "$review_root"

# Keep a comfortable safety margin on the volume. Inputs stream directly from
# m.clear30.org, so only the current H.264 output occupies local disk.
available_kb=$(df -Pk "$review_root" | awk 'NR == 2 { print $4 }')
minimum_kb=$((2 * 1024 * 1024))
if (( available_kb < minimum_kb )); then
    echo "Less than 2 GiB is free on the review volume; refusing to encode." >&2
    exit 75
fi

report_file="${review_root}/batch-report.tsv"
printf 'object_key\tsource_codec\tsource_profile\toutput_codec\toutput_profile\tpixel_format\tduration_seconds\tbytes\tsha256\n' > "$report_file"

for object_key in "$@"; do
    if [[ -z "$object_key" || "$object_key" == /* || "$object_key" == *".."* || "$object_key" != *.mp4 ]]; then
        echo "Unsafe or invalid R2 object key: $object_key" >&2
        exit 64
    fi

    source_url="https://m.clear30.org/${object_key}"
    destination_file="${review_root}/${object_key}"
    partial_file="${destination_file}.partial.mp4"
    mkdir -p "$(dirname "$destination_file")"

    source_codec=$(ffprobe -v error -select_streams v:0 \
        -show_entries stream=codec_name -of default=noprint_wrappers=1:nokey=1 \
        "$source_url")
    source_profile=$(ffprobe -v error -select_streams v:0 \
        -show_entries stream=profile -of default=noprint_wrappers=1:nokey=1 \
        "$source_url")

    if [[ -e "$destination_file" ]]; then
        existing_codec=$(ffprobe -v error -select_streams v:0 \
            -show_entries stream=codec_name -of default=noprint_wrappers=1:nokey=1 \
            "$destination_file")
        existing_pixel_format=$(ffprobe -v error -select_streams v:0 \
            -show_entries stream=pix_fmt -of default=noprint_wrappers=1:nokey=1 \
            "$destination_file")
        if [[ "$existing_codec" != "h264" || "$existing_pixel_format" != "yuv420p" ]]; then
            echo "Existing output failed validation; refusing to overwrite: $destination_file" >&2
            exit 65
        fi
        echo "Using previously validated output: $destination_file"
    else
        echo "Encoding $object_key ($source_codec / $source_profile)"
        rm -f -- "$partial_file"
        ffmpeg -hide_banner -nostdin -y \
            -i "$source_url" \
            -map 0:v:0 -map '0:a:0?' -map_metadata 0 \
            -c:v libx264 -preset medium -profile:v high -pix_fmt yuv420p -crf 20 \
            -c:a aac -b:a 128k \
            -movflags +faststart \
            "$partial_file"

        output_codec=$(ffprobe -v error -select_streams v:0 \
            -show_entries stream=codec_name -of default=noprint_wrappers=1:nokey=1 \
            "$partial_file")
        output_pixel_format=$(ffprobe -v error -select_streams v:0 \
            -show_entries stream=pix_fmt -of default=noprint_wrappers=1:nokey=1 \
            "$partial_file")
        if [[ "$output_codec" != "h264" || "$output_pixel_format" != "yuv420p" ]]; then
            echo "Encoded output failed validation: $partial_file" >&2
            exit 65
        fi
        mv "$partial_file" "$destination_file"
    fi

    output_codec=$(ffprobe -v error -select_streams v:0 \
        -show_entries stream=codec_name -of default=noprint_wrappers=1:nokey=1 \
        "$destination_file")
    output_profile=$(ffprobe -v error -select_streams v:0 \
        -show_entries stream=profile -of default=noprint_wrappers=1:nokey=1 \
        "$destination_file")
    output_pixel_format=$(ffprobe -v error -select_streams v:0 \
        -show_entries stream=pix_fmt -of default=noprint_wrappers=1:nokey=1 \
        "$destination_file")
    duration_seconds=$(ffprobe -v error \
        -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 \
        "$destination_file")
    output_bytes=$(stat -f '%z' "$destination_file")
    output_sha256=$(shasum -a 256 "$destination_file" | awk '{ print $1 }')

    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
        "$object_key" "$source_codec" "$source_profile" \
        "$output_codec" "$output_profile" "$output_pixel_format" \
        "$duration_seconds" "$output_bytes" "$output_sha256" >> "$report_file"
done

echo "Batch ready for local review: $review_root"
echo "Validation report: $report_file"

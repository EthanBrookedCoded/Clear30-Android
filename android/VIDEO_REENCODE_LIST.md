# Program videos to re-encode for Android (2026-07-22)

Android reliably decodes **H.264 (AVC)** in MP4. It does **not** reliably decode
**HEVC (H.265)** — and **10-bit HEVC (Main 10)** fails on a large fraction of
devices even when 8-bit HEVC works. The program's videos are a **mix**: the
numbered lesson videos are mostly HEVC (several 10-bit), while `day_0`/`day_30`,
the "Discomfort/Boredom/Persistence/…" lesson subset, and almost all tutorial +
Instagram clips are already H.264.

**This is the direct cause of "lesson videos don't play on Android"** (the
`video_url` MP4s played by ExoPlayer). It is separate from the YouTube-embed
issue (item G26), which is a WebView/IFrame concern.

Source of truth: `programs.program_messages.video_url` + `.instagram_videos`
(probed via `ffprobe` against `https://m.clear30.org/...` on 2026-07-22).

## Summary

| Codec | Count | Android? |
|---|---:|---|
| **HEVC 10-bit (Main 10)** | 8 | ❌ fails on most devices |
| **HEVC 8-bit (Main)** | 16 | ❌ unreliable |
| **H.264 (High)** | 28 | ✅ plays |
| **Total** | 52 | |

➡️ **24 videos need re-encoding to H.264.** (22 lesson + 1 tutorial + 1 Instagram.)

## Re-encode target

H.264 High/Main, 8-bit `yuv420p`, AAC audio, `+faststart` (moov atom up front so
it streams before fully downloaded):

```bash
ffmpeg -i in.mp4 -c:v libx264 -profile:v high -pix_fmt yuv420p -crf 20 \
  -c:a aac -b:a 128k -movflags +faststart out.mp4
```

(10-bit sources are downconverted to 8-bit by `-pix_fmt yuv420p`.)

## ❌ Need re-encode — lesson videos (`https://m.clear30.org/videos/<file>`)

| Day msg | File | Codec |
|---|---|---|
| 🏁 Starting Strong | `1.mp4` | HEVC **10-bit** |
| 🌊 Riding Out Cravings | `2.mp4` | HEVC |
| ⚓️ Finding Your "Why" | `3.mp4` | HEVC **10-bit** |
| 🚀 Boosting Your Break | `5.mp4` | HEVC |
| 🌿 Strength in Connection | `6.mp4` | HEVC |
| 🌈 Find Joy in New Moments | `7.mp4` | HEVC **10-bit** |
| 🌟 Celebrating Invisible Wins | `8.mp4` | HEVC |
| ⏳ Choosing the Long Game | `10.mp4` | HEVC |
| 🧘 Cannabis and Your Mental Health | `11.mp4` | HEVC |
| 💬 Navigating Friendships | `12.mp4` | HEVC **10-bit** |
| ☀️ The Quiet Power of Positivity | `13.mp4` | HEVC **10-bit** |
| 🪄 The Magic of Shaping Who You Are | `14.mp4` | HEVC |
| 🌿 Embracing a Growth Mindset | `15.mp4` | HEVC |
| 🗣️ Outsmarting Cravings | `17.mp4` | HEVC |
| 🌊 Mindfulness for Cravings | `19.mp4` | HEVC |
| 🪞 Reflecting on Your Past | `21.mp4` | HEVC **10-bit** |
| 🌳 Growth Takes Time | `23.mp4` | HEVC **10-bit** |
| ✨ Unlocking Curiosity and Creativity | `24.mp4` | HEVC |
| 🔑 Clear30 Beyond Weed | `25.mp4` | HEVC |
| 🔁 Navigating Life's Toughest Moments | `26.mp4` | HEVC **10-bit** |
| 🛤️ What's Next? | `28.mp4` | HEVC |
| 🫶 The Power of Acceptance | `29.mp4` | HEVC |

## ❌ Need re-encode — tutorial + Instagram

| File | Codec |
|---|---|
| `tutorial/videos/main_fred.mp4` (🎉 Clear30 Tutorial) | HEVC |
| `ig_vids/0.mp4` (🎉 Your Clear30 Starts Here) | HEVC |

## ✅ Already H.264 — no action (28 files)

- **Lesson:** `day_0.mp4`, `4.mp4`, `9.mp4`, `16.mp4`, `22.mp4`, `27.mp4`, `day_30.mp4`
- **Tutorial (17):** every `tutorial/videos/*.mp4` **except** `main_fred.mp4` —
  `explore_life, improve_health, improve_lungs, improve_relationships,
  improve_self_control, improve_sleep_quality, increase_motivation,
  increase_productivity, legal_obligations, mental_clarity, pass_drug_test,
  reduce_anxiety, reduce_dependency, reduce_depression, reduece_lonliness,
  save_money, stuck_in_my_head`
- **Instagram:** `ig_vids/0_0.mp4`, `ig_vids/1.mp4`, `ig_vids/2.mp4`, `ig_vids/3.mp4`

## Notes

- Videos are hosted on `m.clear30.org` (you control clear30.org) — re-encode in
  place and keep the same filenames so no DB/app change is needed.
- After re-encoding, spot-check playback on a low-end / older Android device
  (HEVC support is the most device-dependent codec).
- `20.mp4` and `18.mp4` are absent from `program_messages` (the numbering skips
  them); no `video_url` row references them.

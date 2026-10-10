# Measurements, 2026-10-10

Pixel 10 Pro XL. A 300 ms tone, the media stream, the phone's own
microphone. Fifteen trials, and the number kept is the median. The net is
the headset median minus the speaker median. Nothing else is subtracted.

The original result screen subtracted an estimated A2DP buffer (150–240 ms,
chosen from how much the trials scattered) and a fixed 35 ms, then clamped
at zero. A real earbud loop of about 124 ms therefore printed 0 ms. That
subtraction is gone. These figures are the measured difference.

## Runs

| | speaker | headset | net | middle half of the net |
|---|---|---|---|---|
| USB-Audio headphone adapter, 9:59 | 110.9 ms | 91.9 ms | −19.0 ms | −25.3 to −15.7 ms |
| Sony WF-1000XM6, 10:01 | 106.3 ms | 126.9 ms | +20.7 ms | +7.3 to +33.7 ms |

The two speaker baselines are 4.6 ms apart, so the nets are not on one
ruler. Comparing the headset figures: the Sony arrived **35 ms** later than
the USB adapter (126.9 − 91.9).

The wired result is negative because the USB adapter reached the microphone
sooner than the phone speaker. The middle half of that run is tight, so
−19 ms is the cluster and not one stray trial.

## The Sony link at the same minute

At 10:01:05 the WF-1000XM6 became the active A2DP device. The state machine
logged `AUDIO_DELAY_REPORTED: arg1=2000` and a codec change to LDAC at
96 kHz / 32-bit. LE Audio's active-device list was empty. Offload was on.
The 126.9 ms median was played on that link.

`arg1` is in tenths of a millisecond, so the headset declared a 200 ms
presentation delay. The microphone found 21 ms over the phone speaker and
35 ms over the USB adapter. The declared budget and the click that was
heard are different readings. A short tone can leave most of that budget
unused. A continuous LDAC stream is the case the 200 ms figure is for, and
this click did not measure that stream.

## What the app does with a result

- The headline is the measured net.
- Phase 1 is pinned to the built-in speaker. Phase 2 prefers a wired
  headset when one is plugged in, otherwise Bluetooth, and the log names
  the device.
- Recording stays on the built-in microphone. A plugged-in headset would
  otherwise become the input, and that microphone cannot hear the earcup.
- A tone the microphone misses is shown immediately, and the next click
  waits so the earcup can be moved. If the headset phase hears nothing,
  the speaker baseline is kept.
- A finished run is saved on the phone: time, route, both medians, trial
  count, and net. The last 40 are kept.
- The next session can measure a new speaker baseline, or use the average
  of the last five saved ones (fewer when fewer exist). After a new
  measurement it asks which of those two the headset test should subtract.

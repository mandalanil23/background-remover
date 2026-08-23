# Privacy Policy — Background Remover – PNG Maker

**Last updated: 1 January 2026**

Background Remover – PNG Maker ("the app") is designed so that your photos never leave
your phone. This policy explains exactly what the app does and does not do with your data.

## Information we collect

**None.** The app has no account system, no analytics, no advertising SDK and no
crash-reporting service. We do not collect your name, email address, device identifiers,
location or usage statistics.

## Your images

Images you select are read only to remove their background, and all processing happens on
your device using Google ML Kit's on-device segmentation models. Your images are never
uploaded to us or to any third party. Your original file is never modified, moved or
deleted.

## Where files are stored

- A temporary working copy is created in the app's private cache while an image is being
  processed, and is deleted automatically (stale files are purged on every app start, and
  the user can clear them at any time from Settings).
- Results are kept in the app's private folder so they can be shown on the Recent Images
  screen. This can be turned off in Settings, and individual results can be deleted.
- Only when you tap **Save PNG** is a copy written to `Pictures/Background Remover` in
  your gallery, where it is yours to manage.

## Permissions

The app uses the Android Photo Picker and the system file picker, which grant access to
only the single file you choose. No photo or media permission is requested.

On Android 9 and older, saving to the gallery requires the storage permission
(`WRITE_EXTERNAL_STORAGE`); it is requested only at the moment you save. On Android 10 and
newer, no permission is needed at all.

The app does **not** request internet access, and never reads contacts, SMS, call logs,
location or any unrelated files.

## Third-party components

The app uses Google ML Kit for image segmentation:

- The **person-segmentation model is bundled inside the app** and runs entirely offline.
- The **general subject-segmentation model** is delivered by Google Play services, which
  may download it once in the background. This transfers the model to your device; it does
  not send your images anywhere.

Google Play services is governed by
[Google's Privacy Policy](https://policies.google.com/privacy).

## Children

The app is a general-purpose photo utility. It collects no personal data from anyone,
including children.

## Data safety summary (for Google Play)

| Question | Answer |
| --- | --- |
| Does the app collect or share user data? | No |
| Is data processed ephemerally? | Yes — images are processed on device and working copies are deleted |
| Is data encrypted in transit? | Not applicable — no data is transmitted |
| Can users request data deletion? | Not applicable — no data is held |

## Changes to this policy

If this policy changes, the updated version will ship with a new release of the app and
the date above will change.

## Contact

Questions about this policy can be sent to the developer contact address listed on the
app's Google Play store page.

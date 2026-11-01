# CleanYourTV

Clean, ad-free Android TV launcher with an Apple TV-style home screen.

- Big clock, dark background, tiles that grow when focused
- Shows every app that has a TV or normal launcher icon (auto-updates)
- Hold OK on an app to add or remove it from Favourites
- No sponsored rows, no ads, no network access

## Build
Push to `main`. GitHub Actions builds the APK: open the **Actions** tab, pick the latest run, download **cleanyourtv-debug-apk**.

## Install on the TV
```
adb connect <TV_IP>:5555
adb install -r app-debug.apk
```
Then press Home on the remote and choose **CleanYourTV** > Always.

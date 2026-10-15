# Device Boost (v1.2)

Android app that replaces pasting commands into Termux. It talks to **Shizuku** (the same
thing `rish` uses) and gives you:

- **Your cheat sheet as buttons (each with Undo where it has one):** main fix (animations 0.5x +
  recompile SystemUI/launcher + restart SystemUI), keep background apps alive, adaptive battery
  off, clear cached background apps, full app recompile.
- **Animation speed:** presets and a slider, plus a Quick Settings tile ("Fast animations").
- **Your commands:** paste any shell command, run it, and save it as a one-tap button.
- An output log so you can see what each command printed.

## Get the APK (GitHub builds it)

1. Upload the files in this folder to your private GitHub repo (same way as before;
   same-named files are replaced). Keep `app/personal.jks`.
2. Actions → **Build APK** runs on every push to `main` (or Run workflow).
3. Open the finished run → **Artifacts** → `DeviceBoost-apk` → unzip → install the APK.

Every build is signed with the same key, so a new APK installs over the old one.

## Using it

1. Start Shizuku (Wireless debugging method) as you already do.
2. Open Device Boost → **Allow access** once.
3. Tap a task, or paste your own command under **Your commands**.

With Shizuku connected the app also grants itself the `WRITE_SECURE_SETTINGS` permission,
so the Quick Settings tile works afterwards without Termux.

## Notes

- Shizuku has to be running for the optimize tasks and your commands; after a reboot you
  start it again, exactly as with Termux/rish.
- `Shizuku.newProcess` is private in the current Shizuku API, so the app calls it by reflection.
- Some commands (for example `pm trim-caches`) behave differently between Android versions;
  the Output box shows the exact result.

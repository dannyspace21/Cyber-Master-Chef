# Cyber Master Chef

A CyberChef-style toolkit for Android: encode, decode, hash, encrypt, calculate and convert data by stacking operations into a recipe. Everything runs on the phone and works offline.

## Get the app

1. Open the **Actions** tab of this repository.
2. Click the latest **Build APK** run with a green check mark.
3. Under **Artifacts**, download **CyberMasterChef-apk** and unzip it.
4. Copy the `.apk` to your phone and open it. Android will ask you to allow installs from that app the first time.

Every push to this repository builds a fresh APK automatically.

## Where things live

- `app/src/main/assets/index.html`: the whole app (all operations and the screen layout)
- `app/src/main/java/.../MainActivity.java`: the small Android wrapper that shows the page, handles Copy/Paste and the back button, and accepts text shared from other apps

## Open in Android Studio

Choose **File > Open** and pick this folder. Android Studio will download what it needs; then press **Run**.

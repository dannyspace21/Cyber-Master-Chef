# Cyber Master Chef

CyberChef for Android. Encode, decode, hash, encrypt, compress, parse and analyse data by stacking operations into a recipe. It runs **CyberChef's own engine** (all 506 operations of CyberChef 11.6.0, including Magic), so results match CyberChef exactly. Everything runs on the phone and works offline.

## What it does

- **Every CyberChef operation**, grouped by CyberChef's categories, with search and a Favourites list you can edit (tap the star).
- **Magic**: as you work, the app looks at the output in the background and suggests the next steps, just like CyberChef's magic wand. The Magic operation is also available on its own.
- **Recipes**: pause a step, set a breakpoint, duplicate, reorder, reset its settings. Bake automatically or with the Bake button.
- **Saved recipes** on the phone, plus **import and export** in all of CyberChef's formats: CyberChef links, chef format and JSON. A CyberChef link with an input in it brings the input along too.
- **Files**: open any file (up to 64 MB) as input, save any output as a file, and send files to the app from other apps with Share.
- **QR scanner**: point the camera at a QR code to read it into the input.
- **Share** the output to any app, Copy and Paste with the phone's clipboard.
- Formatted results (images, tables, charts) are shown the way CyberChef shows them.

## Get the app

1. Open the **Actions** tab of this repository.
2. Click the latest **Build APK** run with a green check mark.
3. Under **Artifacts**, download **CyberMasterChef-apk** and unzip it.
4. Copy the `.apk` to your phone and open it. Android will ask you to allow installs from that app the first time.

Every push to this repository builds a fresh APK automatically.

## Where things live

- `app/src/main/assets/index.html`: the app's screen (recipe builder, input and output, saved recipes, scanner)
- `app/src/main/assets/cyberchef/`: CyberChef's engine, built from the official `cyberchef` npm package. Don't edit these files by hand.
- `app/src/main/java/.../MainActivity.java`: the Android wrapper. It shows the page and handles the clipboard, files, sharing, the camera and the back button.
- `engine/`: the script that builds the engine.

## Updating CyberChef

The engine is already built and committed, so you don't need Node to build the app. To move to a newer CyberChef release, run this with Node 22+ installed, then commit the changes:

```
engine/build.sh 11.6.0      # or a newer version number
```

## Open in Android Studio

Choose **File > Open** and pick this folder. Android Studio will download what it needs; then press **Run**.

## Licences

CyberChef is © Crown Copyright 2016 and licensed under the [Apache License 2.0](app/src/main/assets/cyberchef/LICENSE.txt). The app's **About & licences** screen shows the full licence and the notices for the open-source libraries CyberChef uses.

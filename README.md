<div align="center">
  <a href="https://github.com/Fabi019/hid-barcode-scanner/actions/workflows/android.yml"><img src="https://github.com/Fabi019/hid-barcode-scanner/actions/workflows/android.yml/badge.svg" /></a>
  <a href="https://github.com/Fabi019/hid-barcode-scanner/releases"><img src="https://img.shields.io/github/v/release/Fabi019/hid-barcode-scanner?include_prereleases" /></a>
  <a href="https://play.google.com/store/apps/details?id=dev.fabik.bluetoothhid&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1"><img src="https://img.shields.io/endpoint?color=brightgreen&logo=google-play&logoColor=white&url=https%3A%2F%2Fplay.cuzi.workers.dev%2Fplay%3Fi%3Ddev.fabik.bluetoothhid%26l%3DDownloads%26m%3D%24totalinstalls"></a>
  <a href="https://github.com/Fabi019/hid-barcode-scanner/actions/workflows/test.yml"><img src="https://github.com/Fabi019/hid-barcode-scanner/actions/workflows/test.yml/badge.svg" /></a>

  <br/>
  <br/>

  <img alt="App Logo" src="app/src/main/ic_launcher-playstore.png" width="100" />

  <h1>HID Barcode Scanner</h1>
</div>


Android app for scanning barcodes with the phone camera and sending them to a PC via bluetooth. No
special software is required on the PC as this app uses the BluetoothHID API available on devices
running Android 9 or greater.

## Features

- Supports a wide range of Linear and 2D-Codes thanks to
  the [zxing-cpp](https://github.com/zxing-cpp/zxing-cpp) library
- Doesn't require any internet connection
- History feature that allows exporting the session as text, JSON or CSV
- Multiple different keyboard layouts to choose from
- Large amount of customization for different use-cases
    - Extra keys like \n or \t
    - Template engine to send additional special keys including modifier combinations
    - Regex filtering of codes with support for capture group extraction
    - JavaScript engine to implement custom logic based on the value and type
    - Auto connect with last device
    - Auto send on detection
    - And much more

## Screenshots

Device list and Scanner screen. If you don't want to connect with any device now and just want to
try out the scanner, pressing the 'Skip'-Button at the bottom of the paired devices will bring you
directly to the scanner.

Otherwise the app tries to connect with the selected device and automatically sends you to the
scanner once connected.

<img alt="Devices" src="img/devices.png" width="200px" /> <img alt="Main" src="img/main.png" width="200px" />

All configurable Settings. *(Newer versions might contain more or less settings as shown in the
pictures)*

<img alt="Settings" src="img/settings1.png" width="200px" /> <img alt="Settings" src="img/settings2.png" width="200px" />

## Download

<a href='https://play.google.com/store/apps/details?id=dev.fabik.bluetoothhid&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1'><img alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png' width='200px'/></a>

> **Note**</br>
> Because the version on the Play Store is usually one or two versions behind the latest release,
> you can also download the latest APKs directly here on GitHub as explained in the next section

### Download as APK

Since this app uses the ML-Kit there are two app version to choose from. The *standard* version
doesn't contain the scanner library directly. Because of this the size much smaller than in the
*bundled* but requires the user to have the Play Store installed on their device.

You can either download the latest stable version from
the [Releases](https://github.com/Fabi019/hid-barcode-scanner/releases) tab or directly from
the [CI](https://github.com/Fabi019/hid-barcode-scanner/actions/workflows/test.yml) using the links
below. Please note that the CI version might be unstable and that the builds are not signed (debug
builds), thus requiring you to install them on your phone using *ADB*. The download links below
are using *nightly.link* to provide the files because GitHub doesn't allow to download files from
actions without being logged in.

- Latest APKs (Release): [here](https://github.com/Fabi019/hid-barcode-scanner/releases/latest)
- Latest
  APK (
  Debug): [here](https://nightly.link/Fabi019/hid-barcode-scanner/workflows/test/main/APK%28s%29%20debug%20generated.zip)
- Latest Bundled
  APK (
  Debug): [here](https://nightly.link/Fabi019/hid-barcode-scanner/workflows/test/main/APK%28s%29%20debug%20generated%20%28Bundled%29.zip)

## Troubleshooting

If you are unable to connect with a device you can try either of the steps below depending on how
the app behaves.
If these don't help, feel free to open a new issue and describe your problem in detail.

### Connection dialog visible, but no connection possible

This is most likely caused because the phone was paired with the PC previously and now doesn't
accept a new type of connection request.

***Solution:***

1. Make sure to first unpair the PC on the phone either from within the app or from the system
   Bluetooth settings
2. Remove the phone from the PC device list.
   On Windows you can either do this through the device manager (look under the Bluetooth category)
   and choose *Uninstall Device* or using the device list in the settings app.
3. In the app now search for new devices and click on the target PC (This step could be important so
   that the phone can tell the PC the new device type)
4. A pairing request should show up and you may need to confirm a pin on both sides
5. After that the connection should be successfully established

### Nothing happens when clicking on a device

If there is not even a connection dialog when clicking on a device. This means that the registered
Bluetooth proxy was interrupted. Normally it should be connected again right away but in some cases
this might not happen.

***Solution:***

Restart the app. When launching again, there should be a small message at the bottom of the screen
that says the Bluetooth proxy was successfully connected. Otherwise you may have to restart your
device. This could also mean that your device does not support the Bluetooth HID profile. To test
this, search for the app "Bluetooth HID Profile Tester" in the PlayStore and see what the result is.
If the test is not successful, unfortunately your device is not supported.

## Contributing

Contributions are what make the open source community such an amazing place to learn, inspire, and create. Any contributions you make are **greatly appreciated**.

If you have a suggestion that would make this better, please fork the repo and create a pull request. You can also simply open a new issue with the tag "enhancement".

1. Fork the Project
2. Clone/Open the Repository in Android Studio
3. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
4. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
5. Push to the Branch (`git push origin feature/AmazingFeature`)
6. Open a Pull Request

### Adding new keyboard layout

When the app sends a code to the connected device it doesn't send characters directly.
Instead the app works by sending raw HID-codes to the connected device (The same way a USB-keyboard
behaves).
It is then up to the connected device to interpret this HID-code depending on the layout that is
currently selected in the OS.
This means, that if a scanned barcode contains the character 'Z', the app needs to know the keyboard
layout that the host PC expects (QWERTZ/QWERTY) to send the right HID-code that result in that
character.
To solve this the layout in the app must match the selected layout in the PC.

The app already implements a basic set of keyboard layouts to choose from.
If you want to add a new keyboard layout the following steps might help you:

1. The first step is to create the actual keyboard layout file, for this a great guide already
   exists in the layout for the [polish keyboard](app/src/main/assets/keymaps/pl.layout).
   The layout file always consists of a list of characters with the hid code and modifier.
2. Add the name of the layout to the `<string-array name="keyboard_layout_values">` for every
   available language under `app/src/main/res/values-*/strings.xml`
3. Extend the switch case
   in [BluetoothController.kt#sendString](app/src/main/java/dev/fabik/bluetoothhid/bt/BluetoothController.kt#L285).
   The number is the index of the entry in the `keyboard_layout_values` and the value is the name of
   the layout file without extension (usually two letters)

## License

Copyright (C) 2023-2025 Fabi019

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

*Google Play and the Google Play logo are trademarks of Google LLC.*

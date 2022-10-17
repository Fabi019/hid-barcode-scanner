# Android HID Barcode Scanner

[![Android CI](https://github.com/Fabi019/hid-barcode-scanner/actions/workflows/android.yml/badge.svg)](https://github.com/Fabi019/hid-barcode-scanner/actions/workflows/android.yml)
[![CodeFactor](https://www.codefactor.io/repository/github/fabi019/hid-barcode-scanner/badge/main)](https://www.codefactor.io/repository/github/fabi019/hid-barcode-scanner/overview/main)

Android app for scanning barcodes with the phone camera and sending them to a PC via bluetooth. No
special software is required on the PC as this app uses the BluetoothHID API available on devices
running Android 9 or greater.

## Features

- Supports a wide range of Linear and 2D-Codes thanks to
  Googles [ML-Kit](https://developers.google.com/ml-kit/vision/barcode-scanning)
- Doesn't require any internet connection
- Large amount of customization for different use-cases
  - Auto connect with last device
  - Auto send on detection
  - Extra keys like \n or \t
  - And much more

## Screenshots

<img src="img/devices.png" width="200px" /> <img src="img/main.png" width="200px" /> <img src="img/settings1.png" width="200px" /> <img src="img/settings2.png" width="200px" />

## Download

Since this app uses the ML-Kit there are two app version to choose from. The standard version
doesn't contain the scanner library directly. Because of this the size much smaller than in the
bundled but requires the user to have the Play Store installed on their device.

Latest
APK: [here](https://nightly.link/Fabi019/hid-barcode-scanner/workflows/android/main/APK%28s%29%20release%20generated.zip)

Latest Bundled
APK: [here](https://nightly.link/Fabi019/hid-barcode-scanner/workflows/android/main/APK%28s%29%20release%20generated%20%28Bundled%29.zip)

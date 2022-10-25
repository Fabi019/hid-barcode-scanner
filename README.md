<div align="center">
  <a href="https://github.com/Fabi019/hid-barcode-scanner/actions/workflows/android.yml"><img src="https://github.com/Fabi019/hid-barcode-scanner/actions/workflows/android.yml/badge.svg" /></a>
  <a href="https://www.codefactor.io/repository/github/fabi019/hid-barcode-scanner/overview/main"><img src="https://www.codefactor.io/repository/github/fabi019/hid-barcode-scanner/badge/main" /></a>

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
  Googles [ML-Kit](https://developers.google.com/ml-kit/vision/barcode-scanning)
- Doesn't require any internet connection
- Large amount of customization for different use-cases
  - Auto connect with last device
  - Auto send on detection
  - Extra keys like \n or \t
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

*Google Play and the Google Play logo are trademarks of Google LLC.*

### Download Latest APKs

Since this app uses the ML-Kit there are two app version to choose from. The standard version
doesn't contain the scanner library directly. Because of this the size much smaller than in the
bundled but requires the user to have the Play Store installed on their device.

Latest
APK: [here](https://nightly.link/Fabi019/hid-barcode-scanner/workflows/android/main/APK%28s%29%20release%20generated.zip)

Latest Bundled
APK: [here](https://nightly.link/Fabi019/hid-barcode-scanner/workflows/android/main/APK%28s%29%20release%20generated%20%28Bundled%29.zip)


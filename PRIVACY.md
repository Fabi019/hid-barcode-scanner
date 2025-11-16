# Privacy

This app is designed to scan barcodes with the phone camera and send the value to a device connected over Bluetooth.
**It does not collect, transmit, or store any personal data.**

## Permissions

The following table explains all the permissions that are used. Some permissions (marked with legacy) are only required on older Android versions and not relevant on newer devices.

> [!IMPORTANT]
> The app **does not require internet access** to function.
> As a result it does not request or use any permissions related to internet connectivity (such as `android.permission.INTERNET` or `android.permission.ACCESS_NETWORK_STATE`).


| Permission                                                                     | Purpose                         | Explanation                                                                                                         |
| ------------------------------------------------------------------------------ | ------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| `android.permission.BLUETOOTH` <br> *(only Android 11 and below)*              | Connect to device (legacy)      | Required to communicate with Bluetooth devices.                                                                     |
| `android.permission.BLUETOOTH_ADMIN` <br> *(only Android 11 and below)*        | Discover devices (legacy)       | Needed to initiate scanning for Bluetooth devices.                                                                  |
| `android.permission.ACCESS_COARSE_LOCATION` <br> *(only Android 11 and below)* | Bluetooth scan (legacy)         | Needed on Android <= 11 due to how BLE scanning works on those versions. Not used for actual location tracking.     |
| `android.permission.ACCESS_FINE_LOCATION` <br> *(only Android 11 and below)*   | Bluetooth scan (legacy)         | Needed on Android <= 11 due to how BLE scanning works on those versions. Not used for actual location tracking.     |
| `android.permission.BLUETOOTH_SCAN`                                            | Discover devices                | Allows the app to scan for nearby Bluetooth devices on Android 12+ (API 31+) without requiring location permission. |
| `android.permission.BLUETOOTH_CONNECT`                                         | Connect to devices              | Enables connecting to and communicating with the target device.                                                     |
| `android.permission.FOREGROUND_SERVICE`                                        | Service operation               | Allows the app to run a foreground service, to ensure an uninterrupted communication with the target device.        |
| `android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE`                       | Long-running device interaction | Foreground service type.                                                                                            |
| `android.permission.CAMERA`                                                    | Scan barcodes                   | Needed to scan barcodes with the phone camera.                                                                      |
| `android.permission.VIBRATE`                                                   | Haptic feedback                 | Used to provide tactile feedback after a new scan.                                                                  |
| `android.permission.POST_NOTIFICATIONS`                                        | User notifications              | Allows the app to show a notification that the foreground service is running.                                       |

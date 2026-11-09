# Scan QR

Android Scan QR Code account-add flow.

## UI reference

The screen follows the current main reference: compact Sankalp top bar, `Scan QR Code` heading, helper text, portrait camera preview, centered white scan frame, scan status, `Cancel Scan`, and the four-item bottom navigation.

## Scanner behavior

- Requests camera permission before opening the preview.
- Uses the rear camera for scanning.
- Uses CameraX for the preview and image analysis.
- Uses ML Kit Barcode Scanning to decode QR codes.
- Accepts only `otpauth://` payloads and returns the decoded value through `onQrDetected`.
- Releases the camera and barcode scanner when the screen leaves composition.

## Android project integration

The Android app module will need the CameraX and ML Kit Barcode Scanning dependencies plus `android.permission.CAMERA` in the manifest when the Gradle project is wired.

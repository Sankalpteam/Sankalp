# Firebase setup

The Android module now contains Firebase Authentication and Cloud Firestore dependencies plus a Firebase account repository.

The remaining project-specific Firebase step is to add the real `google-services.json` for the Sankalp Android app to the module root:

```text
android/google-services.json
```

The root Gradle configuration already declares the Google services Gradle plugin. The plugin should be applied to the Android module after the project Firebase configuration file is available.

Required Firebase products for the current account flow:

- Firebase Authentication
- Cloud Firestore

The Firestore repository stores accounts under the authenticated user's document:

```text
users/{uid}/accounts/{accountId}
```

Do not invent Firebase project IDs, package registrations, API keys, or Firestore rules in source code. Those values must come from the actual Sankalp Firebase project.

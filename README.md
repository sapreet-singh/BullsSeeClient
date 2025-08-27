# BullsSeeClient

**BullsSeeClient** is an Android application that collects and uploads location data and call logs to a remote server for analysis. Built with **Jetpack Compose** for the UI, it uses **Java and Kotlin** for logic, leveraging Google Play Services for location tracking, Retrofit for API communication, and WorkManager for background tasks.

---

## ✨ Features

* **Location Tracking:** Continuously tracks device location using Google Play Services and uploads it to the BullsSee API.
* **Call Log Monitoring:** Retrieves call log data (number, date) and sends it to the server.
* **Foreground Service:** Runs a location tracking service with a persistent notification.
* **Background Tasks:** Uses WorkManager to periodically collect and upload call logs.
* **Jetpack Compose UI:** Modern, declarative UI for displaying app status.
* **Permissions:** Handles runtime permissions for location and call log access.

---

## 🛠 Tech Stack

* **Languages:** Kotlin, Java

* **UI:** Jetpack Compose

* **Libraries:**

  * Google Play Services (`play-services-location`)
  * Retrofit (`retrofit`, `converter-gson`)
  * OkHttp (`okhttp`)
  * WorkManager (`work-runtime-ktx`)
  * AndroidX (`core-ktx`, `appcompat`, `activity-compose`, `compose.material3`)

* **Minimum SDK:** 23 (Android 6.0)

* **Target SDK:** 34

* **Compile SDK:** 34

* **Gradle:** 8.13

* **Kotlin:** 2.0.21

---

## ⚙️ Prerequisites

* Android Studio (Koala or later recommended)
* Android device/emulator running Android 6.0 (API 23) or higher
* Google Play Services enabled on the device
* A valid BullsSee API endpoint for data uploads

---
## 📂 Project Structure

```
BullsSeeClient/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/bullsseeclient/
│   │   │   │   ├── MainActivity.kt          # Main UI with Jetpack Compose
│   │   │   │   ├── LocationService.kt       # Foreground service for location tracking
│   │   │   │   ├── CallLogWorker.kt         # WorkManager worker for call log collection
│   │   │   │   ├── services/
│   │   │   │   │   ├── DataCollectionService.java  # Java-based location data worker
│   │   │   │   ├── ui/theme/
│   │   │   │   │   ├── Theme.kt             # Compose theme configuration
│   │   │   │   │   ├── Color.kt             # Color definitions for Compose
│   │   │   │   │   ├── Type.kt              # Typography for Compose
│   │   ├── res/
│   │   │   ├── values/
│   │   │   │   ├── colors.xml               # Colors for system theme
│   │   │   │   ├── strings.xml              # App name and strings
│   │   │   │   ├── styles.xml               # System theme for AndroidManifest
│   ├── build.gradle                         # App module Gradle configuration
├── gradle/
│   ├── wrapper/
│   │   ├── gradle-wrapper.properties        # Gradle wrapper configuration
├── build.gradle                             # Project-level Gradle configuration
├── settings.gradle                          # Gradle settings
```

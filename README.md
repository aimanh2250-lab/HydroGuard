# HydroGuard — IoT Water-Usage Monitor

An Android app that tracks household water consumption **in real time**, reading a
water flow/valve sensor through an **ESP32** microcontroller and syncing the data to
**Firebase**.

## Features
- **Real-time monitoring** — live water-usage readings streamed from an ESP32 + flow/valve sensor.
- **Usage graphs** — daily and period consumption visualised in the app.
- **High-usage alerts** — notifies the user when consumption crosses a set threshold, helping catch leaks or waste early.
- **Account management** — per-user sign-in with data stored in Firebase.

## Tech stack
- **Android** — Java, Android Studio
- **Firebase** — data sync and authentication
- **Arduino ESP32** firmware + water flow/valve sensor (hardware side)

## How it works
The ESP32 reads the flow sensor and pushes readings up to Firebase; the Android app
subscribes to that data and renders live usage, trends, and threshold alerts.

## Getting started
1. Open the project in **Android Studio**.
2. Add your own `google-services.json` (from the Firebase console) into the `app/` module.
3. Flash the ESP32 firmware and point it at your Firebase project.
4. Build and run on an Android device or emulator.

## Screenshots
_Add a screenshot or two here (dashboard + alert) to show the app in action._

---
Built by **Aiman Haikal** — [@aimanh2250-lab](https://github.com/aimanh2250-lab)

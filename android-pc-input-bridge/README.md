# android-pc-input-bridge

This project enables seamless communication between an Android device and a PC using Bluetooth. It allows users to connect a gamepad to their Android tablet and use it to control applications on their PC.

## Features

- Bluetooth connectivity for data transfer between Android and PC.
- Gamepad input handling on Android, sending inputs to the PC.
- Simple setup and configuration.

## Project Structure

- **android/**: Contains the Android application code.
  - **app/**: The main application module.
    - **src/**: Source code for the Android app.
      - **main/**: Main source set.
        - **java/com/example/bridge/**: Contains Kotlin files for the app's functionality.
        - **res/xml/**: Contains XML resources, including network security configuration.
  - **build.gradle.kts**: Build configuration for the Android application.
  - **settings.gradle.kts**: Project settings for the Android application.
  - **gradle.properties**: Gradle properties for the project.

- **pc/**: Contains the PC application code.
  - **src/**: Source code for the PC app.
    - **types/**: Type definitions for TypeScript.
  - **package.json**: NPM configuration for the PC project.
  - **tsconfig.json**: TypeScript configuration for the PC project.

- **shared/**: Contains shared resources between Android and PC.
  - **protocol/**: Documentation for the communication protocol.

## Setup Instructions

1. **Clone the repository**:
   ```
   git clone <repository-url>
   ```

2. **Set up the Android application**:
   - Open the `android` directory in Android Studio.
   - Build and run the application on your Android device.

3. **Set up the PC application**:
   - Navigate to the `pc` directory.
   - Install dependencies:
     ```
     npm install
     ```
   - Run the application:
     ```
     npm start
     ```

## Usage

1. Connect your gamepad to the Android device via Bluetooth.
2. Launch the Android application.
3. Start the PC application to begin receiving input from the gamepad.

## License

This project is licensed under the MIT License. See the LICENSE file for more details.
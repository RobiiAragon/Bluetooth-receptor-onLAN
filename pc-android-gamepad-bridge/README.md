# PC-Android Gamepad Bridge

This project enables communication between a PC application and an Android application using a game controller connected to the Android device. The PC application receives input data from the game controller over the local network, allowing for seamless interaction.

## Project Structure

```
pc-android-gamepad-bridge
├── pc-receiver          # PC receiver application
│   ├── src
│   │   ├── app.ts      # Entry point for the PC receiver
│   │   ├── network
│   │   │   └── server.ts # Handles network communication
│   │   └── types
│   │       └── index.ts  # Type definitions for input data
│   ├── package.json     # NPM configuration
│   └── tsconfig.json    # TypeScript configuration
├── android-sender       # Android sender application
│   ├── app
│   │   └── src
│   │       └── main
│   │           ├── AndroidManifest.xml # Android app manifest
│   │           ├── java
│   │           │   └── com
│   │           │       └── example
│   │           │           └── gamepadbridge
│   │           │               ├── MainActivity.kt # Entry point for Android app
│   │           │               ├── InputService.kt # Captures input from game controller
│   │           │               └── UdpClient.kt # Handles sending data over UDP
│   │           └── res
│   │               └── values
│   │                   └── strings.xml # String resources for Android app
│   ├── build.gradle.kts # Build configuration for Android app
│   ├── settings.gradle.kts # Gradle settings
│   └── gradle.properties # Gradle properties
├── protocol
│   ├── message-schema.json # Defines message schema for communication
│   └── README.md           # Protocol documentation
└── README.md               # Overall project documentation
```

## Setup Instructions

1. **Clone the Repository**
   Clone this repository to your local machine using:
   ```
   git clone <repository-url>
   ```

2. **Install Dependencies**
   Navigate to the `pc-receiver` directory and install the required dependencies:
   ```
   cd pc-receiver
   npm install
   ```

3. **Build the Project**
   Ensure TypeScript is compiled by running:
   ```
   npm run build
   ```

4. **Run the PC Receiver**
   Start the PC receiver application:
   ```
   npm start
   ```

5. **Set Up the Android Application**
   Open the `android-sender` directory in Android Studio and build the project. Ensure you have the necessary permissions for network access in the `AndroidManifest.xml`.

6. **Connect the Game Controller**
   Connect your game controller to the Android device and run the Android application. The input will be sent to the PC receiver over the local network.

## Usage

Once both applications are running, the PC receiver will display the input received from the game controller. You can modify the input handling logic in the `server.ts` file to suit your needs.

## Contributing

Contributions are welcome! Please submit a pull request or open an issue for any enhancements or bug fixes.

## License

This project is licensed under the MIT License. See the LICENSE file for details.
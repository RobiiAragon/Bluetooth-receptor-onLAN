# Protocol Documentation for PC-Android Gamepad Bridge

## Overview
The PC-Android Gamepad Bridge allows users to control PC applications using a game controller connected to an Android tablet. This document outlines the protocol used for communication between the Android sender and the PC receiver.

## Communication Protocol
The communication between the Android device and the PC is established over a local network using UDP. The protocol defines the structure of the messages exchanged, ensuring that input data from the game controller is transmitted accurately and efficiently.

## Message Structure
The messages sent from the Android sender to the PC receiver follow a specific schema defined in `message-schema.json`. Each message contains the following fields:

- **type**: A string indicating the type of input (e.g., button press, joystick movement).
- **data**: An object containing the relevant data for the input type, such as button states or joystick positions.
- **timestamp**: A timestamp indicating when the input was captured.

## Data Transmission
1. **Input Capture**: The `InputService` on the Android device captures input from the game controller.
2. **Message Creation**: The captured input is formatted into a message according to the defined schema.
3. **UDP Transmission**: The `UdpClient` sends the message over the local network to the PC receiver.

## Receiving Input
The PC receiver listens for incoming UDP messages on a specified port. Upon receiving a message, it processes the input data using the `Server` class, which handles the communication and invokes the appropriate actions based on the input received.

## Conclusion
This protocol enables seamless interaction between the Android tablet and the PC, allowing users to enjoy a more immersive gaming experience. For further details on the message schema, refer to `message-schema.json`.
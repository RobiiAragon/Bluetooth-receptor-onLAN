# Communication Protocol between Android and PC

## Overview
This document outlines the communication protocol used between the Android application and the PC application. It specifies the data structures and message formats that facilitate the interaction between the two platforms.

## Message Types

### 1. Connection Request
- **Description**: Sent from the Android device to the PC to initiate a connection.
- **Format**:
  ```json
  {
    "type": "connection_request",
    "device_id": "string",
    "timestamp": "string"
  }
  ```

### 2. Connection Acknowledgment
- **Description**: Sent from the PC to the Android device to acknowledge the connection request.
- **Format**:
  ```json
  {
    "type": "connection_ack",
    "status": "success|failure",
    "message": "string",
    "timestamp": "string"
  }
  ```

### 3. Gamepad Input
- **Description**: Sent from the Android device to the PC containing gamepad input data.
- **Format**:
  ```json
  {
    "type": "gamepad_input",
    "buttons": {
      "A": "boolean",
      "B": "boolean",
      "X": "boolean",
      "Y": "boolean"
    },
    "axes": {
      "left_stick_x": "number",
      "left_stick_y": "number",
      "right_stick_x": "number",
      "right_stick_y": "number"
    },
    "timestamp": "string"
  }
  ```

### 4. Disconnection Notification
- **Description**: Sent from the Android device to the PC when the connection is terminated.
- **Format**:
  ```json
  {
    "type": "disconnection",
    "device_id": "string",
    "timestamp": "string"
  }
  ```

## Data Types
- **device_id**: A unique identifier for the Android device.
- **timestamp**: A string representing the time at which the message was sent, formatted as ISO 8601.
- **buttons**: An object representing the state of the gamepad buttons.
- **axes**: An object representing the position of the gamepad axes.

## Error Handling
In case of an error, the PC application should respond with an error message in the following format:
```json
{
  "type": "error",
  "code": "string",
  "message": "string",
  "timestamp": "string"
}
```

## Conclusion
This protocol ensures a structured and efficient communication between the Android and PC applications, allowing for seamless input handling and connection management.
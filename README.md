# Bluetooth receptor on LAN

Turn any Android phone/tablet into a low-latency gamepad for your Windows PC over LAN. The Android app captures a paired Bluetooth controller and streams only input state to the PC. The Windows server exposes a virtual Xbox 360 controller (XInput) via ViGEm, so games see a normal controller.

## What’s included

- Android client (`android-app/`)
  - Foreground service + tiny overlay to capture input without interfering with other apps
  - Works in background with optional battery/Wi‑Fi performance hints
  - Low-latency TCP stream with keepalive; discovery and manual IP:port
- Windows PC server (`pc-server/`)
  - .NET 8 console app that creates a virtual Xbox 360 controller using ViGEmBus
  - Receives controller state and maps buttons/axes to XInput

## Requirements

- Android 8.0+ (tested on Android 10–15)
- A Bluetooth gamepad paired with the Android device (Xbox one tested)
- Windows 10/11
- .NET 8 SDK (for building the PC server)
- ViGEmBus driver (virtual controller) from https://vigem.org/Downloads/
- LAN connectivity between phone and PC (same network)

## Quick start

1) Install PC server prerequisites
   - Install ViGEmBus and reboot if prompted
   - Ensure Windows Firewall allows inbound on TCP port 39500 (default)

2) Build and run the PC server
   - Project: `pc-server/XvcPcServer`
   - Run with port argument (default 39500). For example: 39500

3) Install and run the Android app
   - Build from `android-app/` or sideload an APK if provided
   - Pair your Bluetooth gamepad in Android system settings
   - Open the app
     - Grant overlay permission when prompted (used for background capture)
     - Optionally exclude from battery optimizations for stability
     - Enter your PC’s IP and port (39500), or use discovery if supported
     - Start the foreground service (Connect)

4) Verify in Windows
   - You should see an “Xbox 360 Controller” in Windows Game Controllers
   - Test in a game or the Windows game controller panel

## Protocol (Android → PC)

One JSON per line, over TCP:

{
  "t":"state","seq":123,"btn":65535,
  "lx":0,"ly":0,"rx":0,"ry":0,
  "lt":0,"rt":0
}

- btn bitmask:
  - A=1<<0, B=1<<1, X=1<<2, Y=1<<3, LB=1<<4, RB=1<<5,
    Back=1<<6, Start=1<<7, LS=1<<8, RS=1<<9, Guide=1<<10,
    DPadUp=1<<11, DPadDown=1<<12, DPadLeft=1<<13, DPadRight=1<<14
- Ranges:
  - lx/ly/rx/ry: −32768..32767
  - lt/rt: 0..255

## Notes and tips

- Background capture: The app uses a tiny overlay view to receive input while other apps are in the foreground. It doesn’t draw UI over your apps.
- Stability: There is an explicit connect/stop control. If the network drops, the client retries with backoff while you keep it enabled. A keepalive ping maintains the TCP session.
- Latency: TCP_NODELAY and small write buffers are used; only the latest controller state is sent.
- D‑Pad: The mapping uses standard Android HAT axes for reliable DPAD behavior.

## Build from source

- Android
  - Requires Android SDK/Gradle. Build the `android-app` module (Debug or Release).
- PC server
  - Requires .NET 8 SDK. Build and run the `pc-server/XvcPcServer` project.

## Troubleshooting

- Can’t connect
  - Verify PC IP and port; confirm firewall allows TCP 39500
  - Both devices must be on the same LAN
- No virtual controller
  - Ensure ViGEmBus is installed and loaded
- Android stops sending in background
  - Grant overlay permission and disable battery optimizations for the app
  - Keep the foreground notification active
- D‑Pad behaves oddly
  - Make sure your physical controller reports DPAD via HAT axes; many do by default

## License

MIT (or the license in this repository if specified elsewhere).

## Acknowledgements

- ViGEmBus by Nefarius
- Android Input APIs

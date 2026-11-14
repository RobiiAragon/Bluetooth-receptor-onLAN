<div align="center">

# Bluetooth receptor on LAN

**Convierte cualquier teléfono Android en un receptor de control inalámbrico de baja latencia para tu PC con Windows.**

![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white) ![Android 8+](https://img.shields.io/badge/Android%208+-3DDC84?style=flat-square&logo=android&logoColor=white) ![.NET 8](https://img.shields.io/badge/.NET%208-512BD4?style=flat-square&logo=dotnet&logoColor=white) ![Windows](https://img.shields.io/badge/Windows-0078D6?style=flat-square&logo=windows&logoColor=white) ![License MIT](https://img.shields.io/badge/License%20MIT-2ea44f?style=flat-square)

[English](README.md) · **Español**

</div>

---

¿Tu PC no tiene Bluetooth? Empareja el control con tu teléfono. La app de Android captura el control y manda **solo el estado de los botones** por tu red local; el servidor de Windows lo convierte en un **control virtual de Xbox 360** (XInput) con ViGEm, así cualquier juego lo ve como un control normal.

```mermaid
flowchart LR
    C[🎮 Control Bluetooth] --> A[📱 App Android]
    A -- TCP · LAN --> S[💻 Servidor Windows]
    S --> V[ViGEmBus · control Xbox 360 virtual] --> G[🕹️ Tus juegos]
```

## Funciones

- ⚡ Transmisión TCP de baja latencia con keepalive.
- 🔎 Descubrimiento automático o `IP:puerto` manual.
- 🔋 Funciona en segundo plano como servicio con un overlay mínimo; ajustes opcionales de batería y Wi-Fi.
- 🪟 App de bandeja en Windows con ventana de log.

## Requisitos

- Android 8.0+ (probado en Android 10–15) con un control Bluetooth emparejado (probado con Xbox One)
- Windows 10/11 con el [driver ViGEmBus](https://vigembusdriver.com/)
- Ambos en la misma red · puerto TCP **39500** permitido en el Firewall de Windows

## Compilar

```powershell
git clone https://github.com/RobiiAragon/Bluetooth-receptor-onLAN.git
cd Bluetooth-receptor-onLAN
./build-pc.ps1            # servidor de Windows → ./publishWin
./build-android.ps1       # APK de Android (usa el Gradle wrapper)
./build-android-adb.ps1   # compila e instala por adb
```

Necesitas el [.NET 8 SDK](https://dotnet.microsoft.com/download) y el Android SDK (lo instala Android Studio).

## Uso

1. Instala ViGEmBus y abre el servidor en la PC (puerto `39500` por defecto).
2. Abre la app en Android, elige tu PC (o escribe su IP) y conecta.
3. Presiona botones: Windows ya ve un control de Xbox 360.

## Estructura

```
android-app/   cliente en Kotlin: captura de input, overlay, servicio
pc-server/     app de bandeja .NET 8: servidor TCP + control virtual ViGEm
build-*.ps1    scripts de compilación
```

## Licencia

[MIT](LICENSE)

---

Hecho por **[Jesús Aragón](https://github.com/RobiiAragon)** · [Portafolio](https://robiiaragon.github.io/portfolio/)

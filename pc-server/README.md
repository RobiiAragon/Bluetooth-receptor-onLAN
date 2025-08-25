# PC Server (Windows) para XVC

Requisitos:
- Windows 10/11
- .NET 8 SDK
- Driver ViGEmBus instalado: https://vigem.org/Downloads/

Instalación:
1) Instala ViGEmBus y reinicia si lo pide.
2) Compila y ejecuta:
   - dotnet build ".\pc-server\XvcPcServer\XvcPcServer.csproj" -c Release
   - dotnet run --project ".\pc-server\XvcPcServer\XvcPcServer.csproj" -- 39500

Notas:
- El servidor crea un “Xbox 360 Controller” virtual. Windows y juegos lo detectan como mando XInput.
- Si el firewall pregunta, permite conexiones entrantes en el puerto configurado (por defecto 39500).
- Protocolo (una línea JSON por estado):
  {
    "t":"state","seq":123,"btn":65535,"lx":0,"ly":0,"rx":0,"ry":0,"lt":0,"rt":0
  }

Mapeo de botones (bitmask):
- A=1<<0, B=1<<1, X=1<<2, Y=1<<3, LB=1<<4, RB=1<<5, Back=1<<6, Start=1<<7,
  LS=1<<8, RS=1<<9, Guide=1<<10, DPadUp=1<<11, DPadDown=1<<12, DPadLeft=1<<13, DPadRight=1<<14

Rangos:
- lx/ly/rx/ry: -32768..32767
- lt/rt: 0..255

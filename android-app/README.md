# Android client (gamepad → PC)

- Empareja tu mando Bluetooth con Android en Ajustes del sistema.
- Abre la app, selecciona el mando detectado por Android, introduce IP:puerto del PC y pulsa Conectar.
- La app enviará solo el estado del mando (sin audio/vídeo) al PC por TCP (JSON por línea).

Formato de mensaje (JSON):
{
  "t":"state","seq":123,"btn":65535,"lx":0,"ly":0,"rx":0,"ry":0,"lt":0,"rt":0
}

- btn: bitmask botones (A=1<<0, B=1<<1, X=1<<2, Y=1<<3, LB=1<<4, RB=1<<5, Back=1<<6, Start=1<<7, LS=1<<8, RS=1<<9, Guide=1<<10, DPadUp=1<<11, DPadDown=1<<12, DPadLeft=1<<13, DPadRight=1<<14)
- lx/ly/rx/ry: -32768..32767
- lt/rt: 0..255

Servidor PC: pendiente (usará ViGEm para exponer un mando virtual).

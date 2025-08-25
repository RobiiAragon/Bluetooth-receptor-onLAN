export interface GamepadInput {
    button: number;
    value: number;
}

export interface BluetoothMessage {
    type: string;
    payload: any;
}

export interface ConnectionStatus {
    connected: boolean;
    deviceName: string;
    signalStrength: number;
}
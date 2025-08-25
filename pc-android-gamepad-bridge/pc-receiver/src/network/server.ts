import dgram from 'dgram';
import { InputData } from '../types';

export class Server {
    private server: dgram.Socket;

    constructor() {
        this.server = dgram.createSocket('udp4');
    }

    public start(port: number) {
        this.server.on('message', (msg, rinfo) => {
            this.handleInput(msg, rinfo);
        });

        this.server.bind(port, () => {
            console.log(`Server listening on port ${port}`);
        });
    }

    private handleInput(msg: Buffer, rinfo: dgram.RemoteInfo) {
        const inputData: InputData = JSON.parse(msg.toString());
        console.log(`Received input from ${rinfo.address}:${rinfo.port}`, inputData);
        // Process the input data as needed
    }
}
import express from 'express';
import http from 'http';
import { Server } from 'socket.io';

const app = express();
const server = http.createServer(app);
const io = new Server(server);

const PORT = process.env.PORT || 3000;

app.get('/', (req, res) => {
    res.send('PC Input Bridge Server is running');
});

io.on('connection', (socket) => {
    console.log('A device connected:', socket.id);

    socket.on('gamepadInput', (data) => {
        console.log('Received gamepad input:', data);
        // Handle gamepad input data here
    });

    socket.on('disconnect', () => {
        console.log('Device disconnected:', socket.id);
    });
});

server.listen(PORT, () => {
    console.log(`Server is running on http://localhost:${PORT}`);
});
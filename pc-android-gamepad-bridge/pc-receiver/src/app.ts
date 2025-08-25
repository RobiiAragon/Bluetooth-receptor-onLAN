import { Server } from './network/server';

const PORT = 3000;

const startServer = async () => {
    const server = new Server(PORT);
    await server.start();
    console.log(`Server is running on port ${PORT}`);
};

startServer().catch(err => {
    console.error('Error starting the server:', err);
});
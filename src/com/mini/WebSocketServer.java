package com.mini;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WebSocketServer implements Runnable{
    private ServerSocket _serverSocket;
    private boolean _running = true;

    WebSocketServer(int port){
        Logger logger = Logger.getLogger(WebSocketServer.class.getName());
        logger.setLevel(Level.WARNING);
        if (port <= 1024 && !System.getProperty("os.name").contains("Windows")) {
            logger.warning("Port 80 is restricted for root users");
        }

        if (port != 80 && port != 443) {
            logger.warning("Port 80 or 443 are recommended");
        }

        try {
            _serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stop(){
        _running = false;
    }

    @Override
    public void run(){
        _running = true;
        while(_running) {
            try {

                Socket client = _serverSocket.accept();
                WebSocketConnectionHandler wsc = new WebSocketConnectionHandler(client);
                Thread clientThread = new Thread(wsc);
                wsc.write("Hello client", false);
                clientThread.start();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            System.out.println("Attempting to close server");
            _serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

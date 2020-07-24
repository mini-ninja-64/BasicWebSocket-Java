package com.mini;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {

    public static void main(String[] args) {
	    WebSocketServer server = new WebSocketServer(4000);
	    Thread t = new Thread(server);
	    t.start();
    }
}

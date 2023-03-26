package com.tb;


import java.io.IOException;


public class UDPChat {
    public static void main(String[] args) throws IOException, InterruptedException {
        if (args[0].equals("-s")){
            Server server = new Server(args[1]);
            server.run();
        }else if (args[0].equals("-c")) {
            Client client = new Client(args[1],args[2],args[3],args[4]);
            client.run();
        }else {
            System.out.println("args error! please use 'UdpChat -s <port>' or 'UdpChat -c <client-name> <server-ip> <server-port> <local-port>'");
        }

    }
}

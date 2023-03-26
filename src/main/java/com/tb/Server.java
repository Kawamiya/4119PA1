package com.tb;

import com.alibaba.fastjson.JSON;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class Server {

    private DatagramSocket socket;
    Map<String, User> clients;
    Map<String, List<User>> groups;

    Server(String port) throws IOException {
        socket = new DatagramSocket(Integer.valueOf(port));
        clients = new HashMap<>();
        groups = new HashMap<>();
    }

    public void run() throws IOException {
        byte[] bytes = new byte[1024];

        while (true) {
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
            socket.receive(packet);
            String message = new String(packet.getData(), 0, packet.getLength());
            if (message == null) continue;
            String[] args = message.split(" ");

            switch (args[0]) {
                case "registration":
                    boolean ack = registration(args[1], packet.getAddress().getHostAddress(), packet.getPort());
                    if (ack){
                        packet.setData("registered".getBytes());
                    }else {
                        packet.setData("already_exist".getBytes());
                    }
                    socket.send(packet);
                    break;
                case "dereg":
                    deRegistration(args[1]);
                    packet.setData("deregistered".getBytes());
                    socket.send(packet);
                    break;
                case "create_group":
                    if (groups.containsKey(args[2])) {
                        packet.setData("already_exist".getBytes());
                        socket.send(packet);
                        System.out.println("[Client " + args[1] + " creating group <group-name> failed, group already exists");
                    } else {
                        List<User> group_users = new ArrayList<User>();
                        User current_user = clients.get(args[1]);
                        group_users.add(current_user);
                        groups.put(args[2], group_users);
                        packet.setData("group_create".getBytes());
                        socket.send(packet);
                        System.out.println("Client " + args[1] + " created group <group-name> successfully");
                        informUpdateGroup();
                    }
                    break;
                case "list_group":
                    System.out.println("Client " + args[1] + " requested listing groups, current groups:");
                    for (Map.Entry<String, List<User>> entry : groups.entrySet()) {
                        System.out.println(entry.getKey());
                    }
                    String responseMessage = "ack " + JSON.toJSONString(groups);
                    packet.setData(responseMessage.getBytes());
                    socket.send(packet);
                    break;
                case "join_group":
                    if (groups.containsKey(args[2])) {
                        List<User> users = groups.get(args[2]);
                        users.add(clients.get(args[1]));
                        groups.put(args[2], users);
                        System.out.println("Client " + args[1] + " joined group " + args[2]);
                        packet.setData("ack".getBytes());
                        socket.send(packet);
                        informUpdateGroup();
                    } else {
                        System.out.println("Client " + args[1] + " joining group <group-name> failed, group does not exist");
                        packet.setData("group_not_exist".getBytes());
                        socket.send(packet);
                    }
                    break;
                case "send_group":
                    args = message.split(" ", 4);
                    System.out.println("Client " + args[2] + " send group message: " + args[3]);
                    packet.setData("ack".getBytes());
                    socket.send(packet);
                    sendGroupMessage(args[0], args[1], args[2], args[3]);
                    break;
                case "list_members":
                    packet.setData(JSON.toJSONString(groups.get(args[1])).getBytes());
                    socket.send(packet);
                    System.out.println("Client " + args[2] + " request listing members of group " + args[1] + ":");
                    for (User user : groups.get(args[1])) {
                        System.out.println(user.getName());
                    }
                    break;
                case "leave_group":
                    List<User> users = groups.get(args[1]);
                    users.remove(clients.get(args[2]));
                    groups.put(args[1], users);
                    System.out.println("Client " + args[2] + " leave group " + args[1]);
                    packet.setData("ack".getBytes());
                    socket.send(packet);
                    informUpdateGroup();
                default:

            }
            informUpdate();
        }
    }

    boolean registration(String clientName, String clientIP, int clientPort) throws IOException {
        if (clients.containsKey(clientName) && clients.get(clientName).getStatus().equals("yes")) return false;
        User user = new User(clientName, clientIP, clientPort, "yes");
        clients.put(clientName, user);
        System.out.println("Server table updated.");
        return true;
    }

    void deRegistration(String clientName) {
        User user = clients.get(clientName);
        user.setStatus("no");
        clients.put(clientName,user);
//        clients.remove(clientName);
        System.out.println("Someone deregistered.");
    }

    private void informUpdate() throws IOException {
        String message = "update" + " " + JSON.toJSONString(clients);
        byte[] data = message.getBytes();
        for (Map.Entry<String, User> entry : clients.entrySet()) {
            String clientName = entry.getKey();
            User user = entry.getValue();
//            System.out.println(user);
            if (user.getStatus().equals("yes")) {
                DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(user.getIP()), Integer.valueOf(user.getPort()));
                socket.send(packet);
            }
        }
//        System.out.println("finish inform all client to update client table");
    }

    void informUpdateGroup() throws IOException {
        String message = "update_group" + " " + JSON.toJSONString(groups);
        byte[] data = message.getBytes();
        for (Map.Entry<String, User> entry : clients.entrySet()) {
            String clientName = entry.getKey();
            User user = entry.getValue();
//            System.out.println(user);
            if (user.getStatus().equals("yes")) {
                DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(user.getIP()), Integer.valueOf(user.getPort()));
                socket.send(packet);
            }
        }
    }

    void sendGroupMessage(String send_group, String groupName, String clientName, String message) throws IOException {
        DatagramSocket sendSocket = new DatagramSocket();
        sendSocket.setSoTimeout(500);

        String sendMessage = "send_group " + groupName + " " + clientName + " " + message;
        byte[] data = sendMessage.getBytes();

        List<User> userList = groups.get(groupName);
        for (User user : userList) {

            if (user.getStatus().equals("yes") && !user.getName().equals(clientName)) {
                DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(user.getIP()), Integer.valueOf(user.getPort()));
                sendSocket.send(packet);
                byte[] bytes = new byte[1024];
                DatagramPacket responsePacket = new DatagramPacket(bytes, bytes.length);

                try {
                    sendSocket.receive(responsePacket);
                    String responseMessage = new String(responsePacket.getData(), 0, responsePacket.getLength());
                    System.out.println(user.getName() + " receive this message");
                } catch (SocketTimeoutException e) {
                    System.out.println("Client " + user.getName() + " not responsive, removed from group " + groupName);
                    //Todo remove this user from current group
                }
            }
        }
    }
}

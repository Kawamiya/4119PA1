package com.tb;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import lombok.Data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.util.*;
import java.lang.Thread;

@Data
public class Client {
    private String clientName;
    private String serverIp;
    private int serverPort;
    private int clientPort;
    Map<String, User> clients;
    Map<String, List<User>> groups;
    DatagramSocket socket;

    String currentGroup = "";
    String mode ="normal";
    List<String> messageList;

    Client(String clientName, String serverIp, String serverPort, String clientPort) throws IOException {
        this.clientName = clientName;
        this.serverIp = serverIp;
        this.serverPort = Integer.valueOf(serverPort);
        this.clientPort = Integer.valueOf(clientPort);
        clients = new HashMap<>();
        groups = new HashMap<>();
        socket = new DatagramSocket(this.clientPort);
        messageList=new ArrayList<>();
    }

    void run() throws IOException, InterruptedException {
        register();

        new Thread(() -> {
            try {
                waitingReceiveMessage();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).start();

        waitingInput();
    }

    void register() throws IOException {
        String sendMessage = "registration" + " " + this.clientName;
        DatagramPacket sendPacket = new DatagramPacket(sendMessage.getBytes(), sendMessage.getBytes().length, InetAddress.getByName(this.serverIp), this.serverPort);
        socket.send(sendPacket);

        //wait response

        byte[] bytes = new byte[1024];
        DatagramPacket responsePacket = new DatagramPacket(bytes, bytes.length);
        try {
        socket.receive(responsePacket);
        String responseMessage = new String(responsePacket.getData(), 0, responsePacket.getLength());
        if(responseMessage.equals("registered")) System.out.println("Welcome, You are registered.");
        else {
            System.out.println("User already exist.");
            System.exit(0);
        }
        } catch (SocketTimeoutException e){
            System.out.println("Server not response");
        }

    }

    void waitingReceiveMessage() throws IOException {
        byte[] bytes = new byte[1024];
        while (true) {
            DatagramPacket receivedPacket = new DatagramPacket(bytes, bytes.length);
            socket.receive(receivedPacket);
            String receivedMessage = new String(receivedPacket.getData(), 0, receivedPacket.getLength());
            String[] args = receivedMessage.split(" ");
            switch (args[0]) {
                case "update":
                    Map<String, User> newClients = JSON.parseObject(args[1], new TypeReference<HashMap<String, User>>() {
                    });
                    if (!newClients.equals(clients)) {
                        clients = newClients;
                    }
                    break;
                case "chat":
                    args = receivedMessage.split(" ", 4);
                    if(mode.equals("normal")) System.out.println(args[1] + ":" + args[3]);
                    else messageList.add(args[1] + ":" + args[3]);
                    System.out.print(">>> ");
//                    if (!currentGroup.equals("")) {
//                        System.out.print("(" + currentGroup + ") ");
//                    }
                    receivedPacket.setData("ack".getBytes());
                    socket.send(receivedPacket);
                    break;
                case "update_group":
                    Map<String, List<User>> newGroups = JSON.parseObject(args[1], new TypeReference<Map<String, List<User>>>() {
                    });
                    if (!newGroups.equals(groups)) {
                        groups = newGroups;
                    }
                    break;
                case "send_group":
                    args = receivedMessage.split(" ", 4);
                    System.out.println("Group_Message " + args[2] + ": " + args[3]);
                    System.out.print(">>> ");
                    if (!currentGroup.equals("")) System.out.print("(" + currentGroup + ") ");
                    receivedPacket.setData("ack".getBytes());
                    socket.send(receivedPacket);
                    break;
                default:
                    break;
            }
        }
    }

    void waitingInput() throws IOException, InterruptedException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print(">>> ");
            if (!currentGroup.equals("")) System.out.print("(" + currentGroup + ") ");
            String input = reader.readLine();
            String[] args = input.split(" ");
            if (mode.equals("normal")){
                switch (args[0]) {
                    case "send":
                        sendMessageToClient(input);
                        break;
                    case "dereg":
                        deRegistration(this.clientName);
                        break;
                    case "create_group":
                        createGroup(args[1]);
                        break;
                    case "list_groups":
                        listGroupChats();
                        break;
                    case "join_group":
                        joinGroup(args[1]);
                        break;
                    default:
                        System.out.println("Invalid command");
                }
            } else if (mode.equals("group_chat")) {
                switch (args[0]){
                    case "dereg":
                        deRegistration(this.clientName);
                        break;
                    case "send_group":
                        sendMessageToGroup(input);
                        break;
                    case "list_members":
                        getListMembers(input);
                        break;
                    case "leave_group":
                        leaveGroup();
                        break;
                    default:
                        System.out.print("("+currentGroup+") ");
                        System.out.println("Invalid command");
                }
            }

        }
    }

    void createGroup(String groupName) throws IOException {
        String sendMessage = "create_group" + " " + this.clientName + " " + groupName;
        DatagramSocket sendSocket = new DatagramSocket();
        DatagramPacket sendPacket = new DatagramPacket(sendMessage.getBytes(), sendMessage.getBytes().length, InetAddress.getByName(this.serverIp), this.serverPort);

        sendSocket.send(sendPacket);

        //wait response
        sendSocket.setSoTimeout(500);
        byte[] bytes = new byte[1024];
        DatagramPacket responsePacket = new DatagramPacket(bytes, bytes.length);
        boolean ack = false;
        try {
            sendSocket.receive(responsePacket);
            String responseMessage = new String(responsePacket.getData(), 0, responsePacket.getLength());
            if (responseMessage.equals("group_create"))
                System.out.println("[Group " + groupName + " created by Server.");
            else System.out.println("Group " + groupName + " already exists.");
            ack = true;
        } catch (SocketTimeoutException e) {
            System.out.println("Server not responding.");
        }
        if (!ack) {
            for (int i = 5; i > 0 && !ack; i--) {
                sendSocket.send(sendPacket);
                try {
                    sendSocket.receive(responsePacket);
                    ack = true;
                    String responseMessage = new String(responsePacket.getData(), 0, responsePacket.getLength());
                    if (responseMessage.equals("group_create"))
                        System.out.println("[Group " + groupName + " created by Server.");
                    else System.out.println("Group " + groupName + " already exists.");
                } catch (SocketTimeoutException e) {
                    System.out.println("Trying to resend message, " + i + " more tries.");
                }
            }
            if (!ack) System.out.println("Exiting");
        }
        if (ack){
            currentGroup = groupName;
            mode="group_chat";
        }

        sendSocket.close();
    }

    void sendMessageToClient(String input) throws IOException, InterruptedException {
        DatagramSocket sendSocket = new DatagramSocket();
        String[] args = input.split(" ", 3);
        String sendMessage = "chat " + this.clientName + " " + args[1] + " " + args[2];
        User targetClient = clients.get(args[1]);

        DatagramPacket sendPacket = new DatagramPacket(sendMessage.getBytes(), sendMessage.getBytes().length, InetAddress.getByName(targetClient.getIP()), targetClient.getPort());
        sendSocket.send(sendPacket);

        //wait response
        sendSocket.setSoTimeout(500);
        byte[] bytes = new byte[1024];
        DatagramPacket responsePacket = new DatagramPacket(bytes, bytes.length);
        try {
            sendSocket.receive(responsePacket);
            String responseMessage = new String(responsePacket.getData(), 0, responsePacket.getLength());
            System.out.println("Message received by " + targetClient.getName() + ".");
        } catch (SocketTimeoutException e) {
            System.out.println("No ACK from " + targetClient.getName() + ", message not delivered.");
//            if (targetClient.getStatus().equals("yes")) {
                deRegistration(targetClient.getName());
//            }
        }
        sendSocket.close();
    }

    void deRegistration(String clientName) throws IOException {
        String sendMessage = "dereg" + " " + clientName;
        DatagramSocket sendSocket = new DatagramSocket();
        DatagramPacket sendPacket = new DatagramPacket(sendMessage.getBytes(), sendMessage.getBytes().length, InetAddress.getByName(this.serverIp), this.serverPort);

        sendSocket.send(sendPacket);

        //wait response
        sendSocket.setSoTimeout(500);
        byte[] bytes = new byte[1024];
        DatagramPacket responsePacket = new DatagramPacket(bytes, bytes.length);
        boolean ack = false;
        try {
            sendSocket.receive(responsePacket);
            String responseMessage = new String(responsePacket.getData(), 0, responsePacket.getLength());
            if (clientName.equals(this.clientName)) System.out.println("You are Offline. Bye.");
            else System.out.println("Server received, " + clientName + " has been de-registered.");
            ack = true;
        } catch (SocketTimeoutException e) {
            System.out.println("Server not responding.");

        }
        if (!ack) {
            for (int i = 5; i > 0 && !ack; i--) {
                sendSocket.send(sendPacket);
                try {
                    sendSocket.receive(responsePacket);
                    String responseMessage = new String(responsePacket.getData(), 0, responsePacket.getLength());
                    ack = true;
                    if (clientName.equals(this.clientName)) System.out.println("You are Offline. Bye.");
                    else System.out.println("Server received, " + clientName + " has been de-registered.");
                } catch (SocketTimeoutException e) {
                    System.out.println("Trying to resend message, " + i + " more tries.");
                }
            }
            if (!ack) System.out.println("Exiting");
        }
        sendSocket.close();
        if (ack&&clientName.equals(this.clientName)){
            System.exit(0);
        }
    }

    void listGroupChats() throws IOException {
        String sendMessage = "list_group" + " " + this.clientName;
        DatagramSocket sendSocket = new DatagramSocket();
        DatagramPacket sendPacket = new DatagramPacket(sendMessage.getBytes(), sendMessage.getBytes().length, InetAddress.getByName(this.serverIp), this.serverPort);

        sendSocket.send(sendPacket);

        //wait response
        sendSocket.setSoTimeout(500);
        byte[] bytes = new byte[1024];
        DatagramPacket responsePacket = new DatagramPacket(bytes, bytes.length);
        boolean ack = false;
        try {
            sendSocket.receive(responsePacket);
            String responseMessage = new String(responsePacket.getData(), 0, responsePacket.getLength());
            String[] args = responseMessage.split(" ", 2);
            Map<String, List<User>> newGroups = JSON.parseObject(args[1], new TypeReference<Map<String, List<User>>>() {
            });
            System.out.println("Available group chats:");
            for (Map.Entry<String, List<User>> entry : newGroups.entrySet()) {
                System.out.println(entry.getKey());
            }
            ack = true;
        } catch (SocketTimeoutException e) {
            System.out.println("Server not responding.");
        }
        if (!ack) {
            for (int i = 5; i > 0 && !ack; i--) {
                sendSocket.send(sendPacket);
                try {
                    sendSocket.receive(responsePacket);
                    String responseMessage = new String(responsePacket.getData(), 0, responsePacket.getLength());
                    String[] args = responseMessage.split(" ", 2);
                    Map<String, List<User>> newGroups = JSON.parseObject(args[1], new TypeReference<Map<String, List<User>>>() {
                    });
                    System.out.println("Available group chats:");
                    for (Map.Entry<String, List<User>> entry : newGroups.entrySet()) {
                        System.out.println(entry.getKey());
                    }
                    ack = true;
                } catch (SocketTimeoutException e) {
                    System.out.println("Trying to resend message, " + i + " more tries.");
                }
            }
            if (!ack) System.out.println("Exiting");
        }

        sendSocket.close();
    }

    void joinGroup(String groupName) throws IOException {
        String sendMessage = "join_group " + this.clientName + " " + groupName;
        DatagramSocket sendSocket = new DatagramSocket();
        DatagramPacket sendPacket = new DatagramPacket(sendMessage.getBytes(), sendMessage.getBytes().length, InetAddress.getByName(this.serverIp), this.serverPort);
        sendSocket.send(sendPacket);
        //wait response
        sendSocket.setSoTimeout(500);
        byte[] bytes = new byte[1024];
        DatagramPacket responsePacket = new DatagramPacket(bytes, bytes.length);
        boolean ack = false;
        try {
            sendSocket.receive(responsePacket);
            String responseMessage = new String(responsePacket.getData(), 0, responsePacket.getLength());
            if (responseMessage.equals("ack")) System.out.println("Entered group " + groupName + " successfully");
            else System.out.println("Group " + groupName + " does not exist");
            ack = true;
        } catch (SocketTimeoutException e) {
            System.out.println("Server not responding.");
        }
        if (!ack) {
            for (int i = 5; i > 0 && !ack; i--) {
                sendSocket.send(sendPacket);
                try {
                    sendSocket.receive(responsePacket);
                    String responseMessage = new String(responsePacket.getData(), 0, responsePacket.getLength());
                    if (responseMessage.equals("ack"))
                        System.out.println("Entered group " + groupName + " successfully");
                    else System.out.println("Group " + groupName + " does not exist");
                    ack = true;
                } catch (SocketTimeoutException e) {
                    System.out.println("Trying to resend message, " + i + " more tries.");
                }
            }
            if (!ack) System.out.println("Exiting");
        }
        if (ack){
            currentGroup = groupName;
            mode="group_chat";
        }
        sendSocket.close();
    }

    void sendMessageToGroup(String input) throws IOException {
        String[] args = input.split(" ", 2);
        String sendMessage = "send_group " + currentGroup + " " + this.clientName + " " + args[1];
        DatagramSocket sendSocket = new DatagramSocket();
        DatagramPacket sendPacket = new DatagramPacket(sendMessage.getBytes(), sendMessage.getBytes().length, InetAddress.getByName(this.serverIp), this.serverPort);
        sendSocket.send(sendPacket);
        //wait response
        sendSocket.setSoTimeout(500);
        byte[] bytes = new byte[1024];
        DatagramPacket responsePacket = new DatagramPacket(bytes, bytes.length);
        boolean ack = false;
        try {
            sendSocket.receive(responsePacket);
            String responseMessage = new String(responsePacket.getData(), 0, responsePacket.getLength());
            System.out.println("(" + args[0] + ")" + " Message received by Server.");
            ack = true;
        } catch (SocketTimeoutException e) {
            System.out.println("Server not responding.");
        }
        if (!ack) {
            for (int i = 5; i > 0 && !ack; i--) {
                sendSocket.send(sendPacket);
                try {
                    sendSocket.receive(responsePacket);
                    String responseMessage = new String(responsePacket.getData(), 0, responsePacket.getLength());
                    System.out.println("(" + args[0] + ")" + " Message received by Server.");
                    ack = true;
                } catch (SocketTimeoutException e) {
                    System.out.println("Trying to resend message, " + i + " more tries.");
                }
            }
            if (!ack) System.out.println("Exiting");
        }
        sendSocket.close();
    }

    void getListMembers(String input) throws IOException {
        String[] args = input.split(" ", 2);

        String sendMessage = input + " " + currentGroup + " " + this.clientName;
        DatagramSocket sendSocket = new DatagramSocket();
        DatagramPacket sendPacket = new DatagramPacket(sendMessage.getBytes(), sendMessage.getBytes().length, InetAddress.getByName(this.serverIp), this.serverPort);
        sendSocket.send(sendPacket);
        //wait response
        sendSocket.setSoTimeout(500);
        byte[] bytes = new byte[1024];
        DatagramPacket responsePacket = new DatagramPacket(bytes, bytes.length);
        boolean ack = false;
        try {
            sendSocket.receive(responsePacket);
            String responseMessage = new String(responsePacket.getData(), 0, responsePacket.getLength());
            List<User> userList = JSON.parseObject(responseMessage, new TypeReference<List<User>>() {
            });
            System.out.print(">>> (" + currentGroup + ") ");
            System.out.println("Members in the group " + currentGroup+ ":");
            for (User user : userList) {
                System.out.println(">>> (" + currentGroup + ") " + user.getName());
            }
            ack = true;
        } catch (SocketTimeoutException e) {
            System.out.println("Server not responding.");
        }
        if (!ack) {
            for (int i = 5; i > 0 && !ack; i--) {
                sendSocket.send(sendPacket);
                try {
                    sendSocket.receive(responsePacket);
                    String responseMessage = new String(responsePacket.getData(), 0, responsePacket.getLength());
                    List<User> userList = JSON.parseObject(responseMessage, new TypeReference<List<User>>() {
                    });
                    System.out.println("(" + currentGroup + ")" + " Members in the group " + args[1] + ":");
                    for (User user : userList) {
                        System.out.println("(" + currentGroup + ") " + user.getName());
                    }
                    ack = true;
                } catch (SocketTimeoutException e) {
                    System.out.println("Trying to resend message, " + i + " more tries.");
                }
            }
            if (!ack) System.out.println(">>> (" + currentGroup + ") "+"Exiting");
        }
        sendSocket.close();
    }
    void leaveGroup() throws IOException {
        String sendMessage = "leave_group " + currentGroup + " " + this.clientName;
        DatagramSocket sendSocket = new DatagramSocket();
        DatagramPacket sendPacket = new DatagramPacket(sendMessage.getBytes(), sendMessage.getBytes().length, InetAddress.getByName(this.serverIp), this.serverPort);
        sendSocket.send(sendPacket);
        //wait response
        sendSocket.setSoTimeout(500);
        byte[] bytes = new byte[1024];
        DatagramPacket responsePacket = new DatagramPacket(bytes, bytes.length);
        boolean ack = false;
        try {
            sendSocket.receive(responsePacket);
            String responseMessage = new String(responsePacket.getData(), 0, responsePacket.getLength());
            System.out.print(">>> ");
            System.out.println("Leave group chat " + currentGroup);
            ack = true;
        } catch (SocketTimeoutException e) {
            System.out.println("Server not responding.");
        }
        if (!ack) {
            for (int i = 5; i > 0 && !ack; i--) {
                sendSocket.send(sendPacket);
                try {
                    sendSocket.receive(responsePacket);
                    String responseMessage = new String(responsePacket.getData(), 0, responsePacket.getLength());
                    System.out.print(">>> ");
                    System.out.println("Leave group chat " + currentGroup);
                    ack = true;
                } catch (SocketTimeoutException e) {
                    System.out.println("Trying to resend message, " + i + " more tries.");
                }
            }
            if (!ack) System.out.println(">>> (" + currentGroup + ") "+"Exiting");
        }
        if (ack){
            for (String message:messageList){
                System.out.println(">>> "+message);
            }
            currentGroup = "";
            mode="normal";
        }
        sendSocket.close();
    }

}

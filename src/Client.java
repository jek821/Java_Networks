import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    private static String myId;
    private static String username;
    private static volatile String activePartnerId = null;
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        String host = "localhost";
        int port = 59090;

        try (Socket socket = new Socket(host, port)) {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            // username handshake
            System.out.print("Enter your username: ");
            username = scanner.nextLine().trim();
            out.writeObject(new Message("SYSTEM", username, "temp", "Server"));
            out.flush();

            // server welcome
            Message welcome = (Message) in.readObject();
            System.out.println("Server: " + welcome.getText());
            myId = welcome.getTo();

            // listener thread
            Thread listener = new Thread(() -> {
                try {
                    while (true) {
                        Message msg = (Message) in.readObject();
                        switch (msg.getType()) {
                            case "SYSTEM":
                            case "SYSTEM_ERROR":
                            case "SYSTEM_LIST":
                                System.out.println(msg.getText());
                                break;
                            case "CONNECT_REQUEST":
                                System.out.println(msg.getFrom() +
                                        " wants to connect. Type /accept " + msg.getFrom() +
                                        " or /deny " + msg.getFrom());
                                break;
                            case "CONNECT_ACCEPT":
                                activePartnerId = msg.getFrom();
                                clearScreen();
                                System.out.println("Connected to " + msg.getFrom() + " — start chatting!");
                                break;
                            case "CONNECT_DENY":
                                System.out.println(msg.getFrom() + " denied your request.");
                                break;
                            case "CHAT":
                                System.out.println(msg.getFrom() + ": " + msg.getText());
                                break;
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Disconnected from server.");
                    System.exit(0);
                }
            });
            listener.setDaemon(true);
            listener.start();

            // main input loop
            while (true) {
                if (activePartnerId == null) {
                    System.out.print("Command (/list, clientId, /accept ID, /deny ID): ");
                    String inputLine = scanner.nextLine().trim();
                    if (inputLine.equals("/list")) {
                        out.writeObject(new Message("SYSTEM", "/list", myId, "Server"));
                    } else if (inputLine.startsWith("/accept")) {
                        String target = inputLine.split(" ")[1];
                        activePartnerId = target;
                        out.writeObject(new Message("CONNECT_ACCEPT", "Accepted", myId, target));
                        clearScreen();
                        System.out.println("Connected to " + target + " — start chatting!");
                    } else if (inputLine.startsWith("/deny")) {
                        String target = inputLine.split(" ")[1];
                        out.writeObject(new Message("CONNECT_DENY", "Denied", myId, target));
                    } else {
                        // treat as connect request
                        out.writeObject(new Message("CONNECT_REQUEST", "Requesting chat", myId, inputLine));
                        System.out.println("Asking permission from " + inputLine + "...");
                    }
                    out.flush();
                } else {
                    System.out.print("You: ");
                    String text = scanner.nextLine();
                    out.writeObject(new Message("CHAT", text, myId, activePartnerId));
                    out.flush();
                    System.out.println(username + ": " + text);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void clearScreen() {
        for (int i = 0; i < 50; i++) System.out.println();
    }
}

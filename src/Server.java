import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    private static final int PORT = 59090;

    // clientId -> handler
    private static final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.out.println("Server listening on port " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            int clientCounter = 1;

            while (true) {
                Socket socket = serverSocket.accept();
                String clientId = "Client" + clientCounter++;
                ClientHandler handler = new ClientHandler(socket, clientId);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void addClient(ClientHandler handler) {
        clients.put(handler.getClientId(), handler);
        printConnectedClients();
    }

    static void removeClient(String clientId) {
        clients.remove(clientId);
        System.out.println(clientId + " disconnected.");
        printConnectedClients();
    }

    static boolean clientExists(String clientId) {
        return clients.containsKey(clientId);
    }

    static void forwardMessage(Message msg) {
        switch (msg.getType()) {
            case "SYSTEM":
                if ("/list".equalsIgnoreCase(msg.getText())) {
                    // Reply only to requester with the current list
                    ClientHandler requester = clients.get(msg.getFrom());
                    if (requester != null) {
                        requester.sendMessage(new Message(
                                "SYSTEM_LIST",
                                getClientList(),
                                "Server",
                                requester.getClientId()
                        ));
                    }
                }
                break;

            case "CONNECT_REQUEST": {
                ClientHandler recipient = clients.get(msg.getTo());
                if (recipient == null) {
                    // Inform sender: no such client
                    ClientHandler sender = clients.get(msg.getFrom());
                    if (sender != null) {
                        sender.sendMessage(new Message(
                                "SYSTEM_ERROR",
                                "No such client ID: " + msg.getTo(),
                                "Server",
                                sender.getClientId()
                        ));
                    }
                } else {
                    // Forward request to target client
                    recipient.sendMessage(msg);
                }
                break;
            }

            case "CONNECT_ACCEPT":
            case "CONNECT_DENY":
            case "CHAT": {
                ClientHandler recipient = clients.get(msg.getTo());
                if (recipient != null) {
                    recipient.sendMessage(msg);
                } else {
                    // Inform sender if recipient vanished
                    ClientHandler sender = clients.get(msg.getFrom());
                    if (sender != null) {
                        sender.sendMessage(new Message(
                                "SYSTEM_ERROR",
                                "Recipient not available: " + msg.getTo(),
                                "Server",
                                sender.getClientId()
                        ));
                    }
                }
                break;
            }

            default:
                // ignore unknown types
                break;
        }
    }

    private static String getClientList() {
        StringBuilder sb = new StringBuilder("Connected Clients:\n");
        if (clients.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (ClientHandler h : clients.values()) {
                sb.append(" - ").append(h.getUsername())
                        .append(" (").append(h.getClientId()).append(")\n");
            }
        }
        return sb.toString();
    }

    private static void printConnectedClients() {
        System.out.println("=== Connected Clients ===");
        if (clients.isEmpty()) {
            System.out.println("None");
        } else {
            for (ClientHandler h : clients.values()) {
                System.out.println(" - " + h.getUsername() + " (" + h.getClientId() + ")");
            }
        }
        System.out.println("=========================");
    }
}

class ClientHandler implements Runnable {
    private final Socket socket;
    private final String clientId;
    private String username = "(unknown)";
    private ObjectOutputStream out;
    private ObjectInputStream in;

    ClientHandler(Socket socket, String clientId) {
        this.socket = socket;
        this.clientId = clientId;
    }

    String getClientId() { return clientId; }
    String getUsername() { return username; }

    @Override
    public void run() {
        try {
            // IMPORTANT: create ObjectOutputStream before ObjectInputStream to avoid deadlock
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in  = new ObjectInputStream(socket.getInputStream());

            // Expect first message: username (SYSTEM type)
            Message intro = (Message) in.readObject();
            if (!"SYSTEM".equals(intro.getType())) {
                sendMessage(new Message("SYSTEM_ERROR",
                        "Expected username handshake. Disconnecting.",
                        "Server", clientId));
                closeAll();
                return;
            }
            this.username = intro.getText();

            // Register and greet (client learns its ID from the 'to' field)
            Server.addClient(this);
            sendMessage(new Message(
                    "SYSTEM",
                    "Welcome " + username + "! Your ID is " + clientId,
                    "Server",
                    clientId
            ));

            // Main loop
            while (true) {
                Message msg = (Message) in.readObject();

                // Enforce sender identity
                if (!clientId.equals(msg.getFrom())) {
                    System.out.println("⚠️  Spoof attempt: " + clientId + " claimed " + msg.getFrom());
                    sendMessage(new Message("SYSTEM_ERROR",
                            "Invalid 'from' ID. Your ID is " + clientId,
                            "Server", clientId));
                    continue;
                }

                // Log and forward
                System.out.println(username + " (" + clientId + "): " + msg.getType() + " -> " + msg.getText());
                Server.forwardMessage(msg);
            }
        } catch (EOFException eof) {
            // client closed connection
        } catch (Exception e) {
            System.out.println("Client error (" + clientId + "): " + e.getMessage());
        } finally {
            Server.removeClient(clientId);
            closeAll();
        }
    }

    void sendMessage(Message msg) {
        try {
            out.writeObject(msg);
            out.flush();
        } catch (IOException e) {
            System.out.println("Send failed to " + clientId + ": " + e.getMessage());
        }
    }

    private void closeAll() {
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        try { if (out != null) out.close(); } catch (IOException ignored) {}
        try { socket.close(); } catch (IOException ignored) {}
    }
}


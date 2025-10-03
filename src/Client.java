import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Client UI model with three states:
 *  - IDLE: prompt "/list or client ID". After /list, re-prompt.
 *  - AWAIT_DECISION: someone requested -> clear screen, ask (y/n).
 *  - CHATTING: clear-and-redraw full history after every send/receive.
 *
 * Only the MAIN LOOP prints prompts. A background stdin reader thread
 * pushes lines into inputQueue. A listener thread pushes server messages
 * into msgQueue. The main loop "selects" between the two queues.
 */
public class Client {
    private enum State { IDLE, AWAIT_DECISION, CHATTING }

    private static String myId;
    private static String username;
    private static volatile State state = State.IDLE;

    // who we are chatting with (clientId) when CHATTING
    private static volatile String activePartnerId = null;

    // who asked to connect (clientId) when AWAIT_DECISION
    private static volatile String pendingRequesterId = null;

    // Queues
    private static final BlockingQueue<String> inputQueue = new LinkedBlockingQueue<>();
    private static final BlockingQueue<Message> msgQueue   = new LinkedBlockingQueue<>();

    // Simple chat history (strings already formatted for display)
    private static final List<String> chatHistory = new ArrayList<>();

    public static void main(String[] args) {
        String host = "localhost";
        int port = 59090;

        try (Socket socket = new Socket(host, port)) {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            ObjectInputStream in  = new ObjectInputStream(socket.getInputStream());

            // ---- stdin reader (never prints) ----
            Thread stdinReader = new Thread(() -> {
                BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
                try {
                    while (true) {
                        String line = br.readLine();
                        if (line == null) break;
                        inputQueue.put(line);
                    }
                } catch (Exception ignored) {}
            });
            stdinReader.setDaemon(true);
            stdinReader.start();

            // ---- username handshake ----
            printPlain("Enter your username: ");
            String uname = takeLineBlocking();
            if (uname == null || uname.trim().isEmpty()) uname = "user";
            username = uname.trim();
            out.writeObject(new Message("SYSTEM", username, "temp", "Server"));
            out.flush();

            Message welcome = (Message) in.readObject();
            myId = welcome.getTo();
            println("Server: " + welcome.getText());
            println(""); // spacing

            // ---- network listener (never prints) ----
            Thread listener = new Thread(() -> {
                try {
                    while (true) {
                        Message m = (Message) in.readObject();
                        msgQueue.put(m);
                    }
                } catch (Exception ignored) {
                    // server closed or error
                    try { msgQueue.put(new Message("SYSTEM_ERROR", "Disconnected from server.", "Server", myId)); } catch (Exception __) {}
                }
            });
            listener.setDaemon(true);
            listener.start();

            // ---- main event/render loop ----
            state = State.IDLE;
            boolean promptShown = false;

            while (true) {
                // 1) Drain any pending server messages first so UI reacts immediately
                Message m;
                while ((m = msgQueue.poll()) != null) {
                    promptShown = handleServerMessage(m, out, promptShown);
                }

                // 2) Depending on state, show the right prompt (exactly once)
                if (!promptShown) {
                    switch (state) {
                        case IDLE:
                            printPlain("Enter /list or client ID: ");
                            promptShown = true;
                            break;
                        case AWAIT_DECISION:
                            clearScreen();
                            println((pendingRequesterId == null ? "Unknown" : pendingRequesterId) +
                                    " wants to chat. Accept? (y/n): ");
                            printPlain("> ");
                            promptShown = true;
                            break;
                        case CHATTING:
                            // show history and one input prompt
                            redrawChat();
                            printPlain("You: ");
                            promptShown = true;
                            break;
                    }
                }

                // 3) Read a line of input (non-blocking with small timeout), so
                //    server events can still preempt.
                String line = inputQueue.poll(150, TimeUnit.MILLISECONDS);
                if (line == null) continue; // no user input yet; spin to handle messages

                line = line.trim();

                // 4) Handle input by state
                switch (state) {
                    case IDLE:
                        if (line.equalsIgnoreCase("/list")) {
                            out.writeObject(new Message("SYSTEM", "/list", myId, "Server"));
                            out.flush();
                            promptShown = false; // will re-prompt after list arrives
                        } else if (!line.isEmpty()) {
                            // treat as clientId to request
                            out.writeObject(new Message("CONNECT_REQUEST", "Requesting chat", myId, line));
                            out.flush();
                            println("Asking permission from " + line + "...");
                            promptShown = false; // re-show the idle prompt
                        }
                        break;

                    case AWAIT_DECISION:
                        if (line.equalsIgnoreCase("y") || line.equalsIgnoreCase("yes")) {
                            if (pendingRequesterId != null) {
                                out.writeObject(new Message("CONNECT_ACCEPT", "Accepted", myId, pendingRequesterId));
                                out.flush();
                                // transition to chat
                                activePartnerId = pendingRequesterId;
                                pendingRequesterId = null;
                                chatHistory.clear();
                                chatHistory.add("Connected to " + activePartnerId);
                                state = State.CHATTING;
                                promptShown = false; // force redrawChat + You:
                            } else {
                                // No requester? go idle
                                state = State.IDLE;
                                promptShown = false;
                            }
                        } else if (line.equalsIgnoreCase("n") || line.equalsIgnoreCase("no")) {
                            if (pendingRequesterId != null) {
                                out.writeObject(new Message("CONNECT_DENY", "Denied", myId, pendingRequesterId));
                                out.flush();
                            }
                            pendingRequesterId = null;
                            state = State.IDLE;
                            promptShown = false;
                        } else {
                            // ignore anything else, re-prompt (stay in AWAIT_DECISION)
                            promptShown = false;
                        }
                        break;

                    case CHATTING:
                        if (!line.isEmpty()) {
                            out.writeObject(new Message("CHAT", line, myId, activePartnerId));
                            out.flush();
                            chatHistory.add(username + ": " + line);
                            // After each send, clear and show full history
                            redrawChat();
                            printPlain("You: ");
                        }
                        // keep promptShown = true (we already printed it)
                        break;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            println("Fatal: " + e.getMessage());
        }
    }

    // ---------------- UI & handlers ----------------

    /** Process a server message. Returns updated promptShown flag. */
    private static boolean handleServerMessage(Message msg, ObjectOutputStream out, boolean promptShown) throws IOException {
        switch (msg.getType()) {
            case "SYSTEM":
                println(msg.getText());
                return false; // re-prompt

            case "SYSTEM_ERROR":
                println("Error: " + msg.getText());
                return false;

            case "SYSTEM_LIST":
                println(msg.getText());
                return false;

            case "CONNECT_REQUEST":
                // Preempt into decision state
                pendingRequesterId = msg.getFrom();  // (Server forwards 'from' as clientId)
                state = State.AWAIT_DECISION;
                return false; // will clear + prompt (y/n)

            case "CONNECT_ACCEPT":
                // We are now in a chat session with msg.getFrom()
                activePartnerId = msg.getFrom();
                pendingRequesterId = null;
                chatHistory.clear();
                chatHistory.add("Connected to " + activePartnerId);
                state = State.CHATTING;
                return false; // will redraw chat + prompt "You:"

            case "CONNECT_DENY":
                println(msg.getFrom() + " denied your request.");
                // remain idle
                state = State.IDLE;
                return false;

            case "CHAT":
                // Only show if relevant to the active chat partner
                if (state == State.CHATTING && activePartnerId != null && activePartnerId.equals(msg.getFrom())) {
                    chatHistory.add(msg.getFrom() + ": " + msg.getText());
                    // Clear and redraw the whole chat on receive
                    redrawChat();
                    printPlain("You: ");
                    return true; // prompt already shown
                } else {
                    // If not in chat, just surface it (rare edge); stay in current state
                    println(msg.getFrom() + ": " + msg.getText());
                    return false;
                }
        }
        return promptShown;
    }

    private static void redrawChat() {
        clearScreen();
        for (String line : chatHistory) {
            System.out.println(line);
        }
    }

    private static void clearScreen() {
        // Portable "clear": lots of newlines (more reliable than ANSI across IDEs)
        for (int i = 0; i < 60; i++) System.out.println();
    }

    private static void println(String s) {
        System.out.println(s);
    }
    private static void printPlain(String s) {
        System.out.print(s);
        System.out.flush();
    }

    private static String takeLineBlocking() {
        try {
            return inputQueue.take();
        } catch (InterruptedException e) {
            return null;
        }
    }
}

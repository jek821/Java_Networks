
# Chat Application (Java, TCP Sockets)

## 📌 Overview
This project implements a **multi-client chat application** using Java sockets. It was built as part of a Computer Networks course project, modeled after Kurose & Ross’s socket programming examples and extended with custom features such as usernames, connection requests, and chat history displays.  

The system consists of three main files:  
- `Server.java` – multi-threaded server that manages clients and routes messages.  
- `Client.java` – client application with username setup, `/list` command, and private chats.  
- `Message.java` – serializable data structure used for all communication.  

---

## ⚡ Challenges Faced

### 1. Java I/O Streams Deadlock
We discovered that if both client and server created their `ObjectInputStream` first, the connection would freeze. The fix was to always create the `ObjectOutputStream` before the `ObjectInputStream`.

### 2. Client Requests Not Showing Immediately
At first, connection requests (`CONNECT_REQUEST`) only appeared to the recipient after they typed something. This was due to input blocking in the main loop. We solved it by using a **queue system**: the listener thread enqueues requests, and the main loop processes them immediately before re-prompting.

### 3. /list Command
Initially, `/list` only worked for one client because the server was printing the client list to its own console instead of returning it as a message. We fixed this by sending back a `SYSTEM_LIST` message that the client displays correctly.

### 4. Maintaining Persistent Chats
After a connection was accepted, we needed a way to keep the conversation persistent. We introduced the concept of `activePartnerId` and changed the input loop so all typed messages automatically route to that partner until the session ends.

### 5. Clean Chat Display
We wanted a real chat-like interface where the screen is cleared and the full history is redrawn. This required ANSI escape codes and maintaining a `chatHistory` list that gets re-rendered on each new message.

---

## 🔧 Features and File Responsibilities

### `Message.java`
- Defines a serializable `Message` class used for all communication.  
- Fields include: `type`, `text`, `from`, `to`, and `created`.  
- Types: `"CHAT"`, `"CONNECT_REQUEST"`, `"CONNECT_ACCEPT"`, `"CONNECT_DENY"`, `"SYSTEM"`, `"SYSTEM_LIST"`, `"SYSTEM_ERROR"`.  

### `Server.java`
- Accepts incoming socket connections and assigns each client a unique ID (`Client1`, `Client2`, …).  
- Stores all connected clients in a `ConcurrentHashMap`.  
- Routes messages based on their type:  
  - `/list` requests return a `SYSTEM_LIST` message to the requester.  
  - `CONNECT_REQUEST` forwards to the requested client.  
  - `CONNECT_ACCEPT` and `CONNECT_DENY` complete the handshake.  
  - `CHAT` messages are routed to the correct partner.  
- Logs all actions (connections, disconnections, spoof attempts).  

### `Client.java`
- Prompts the user for a username on startup.  
- Handles three major states: idle (no partner), connection requests, and active chat.  
- `/list` command shows all currently connected clients.  
- Connection requests appear immediately with a yes/no prompt.  
- Accepted connections open a persistent chat session.  
- Maintains `chatHistory`, redraws the full conversation, and always shows `You:` at the bottom for new input.  

---

## 🚀 How to Run
1. Compile all files:
   ```bash
   javac *.java
   ```
2. Start the server:
   ```bash
   java Server
   ```
3. Run one or more clients (in IntelliJ allow parallel run or use multiple terminals):
   ```bash
   java Client
   ```

---

## ✅ Summary
This project evolved from a basic TCP socket demo into a fully functional chat application with persistent sessions, usernames, and a terminal-based chat UI. We overcame synchronization challenges, I/O stream ordering, and user experience issues to deliver a working system closely tied to networking fundamentals.

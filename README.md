
# Chat Application (Java, TCP Sockets)

## Overview
<<<<<<< HEAD
I built a multi-client chat application using Java sockets. This project was part of a Computer Networks course, inspired by Kurose & Ross’s socket programming examples. I extended the design to support usernames, connection requests, and basic chat history.  
=======
This project implements a **multi-client chat application** using Java sockets. It was built as part of a Computer Networks course project, modeled after Kurose & Ross’s socket programming examples and extended with custom features such as usernames, connection requests, and chat history displays.  
>>>>>>> f9937123bdb90b288c93d7b8eb54c969e7cb92df

The system consists of three files:  
- `Server.java` – runs as a multi-threaded server that accepts clients and forwards messages.  
- `Client.java` – the program each user runs to connect, request sessions, and chat.  
- `Message.java` – a serializable class that defines the format of messages exchanged between clients and the server.  

---

## Challenges Faced

### 1. Stream Initialization
One of the earliest problems I ran into was that if both sides created their input streams first, the program froze. I learned that output streams had to be created before input streams to avoid deadlock.

### 2. Connection Requests
Requests to connect did not appear until the recipient typed something. This happened because the program was blocked waiting for input. I solved this by restructuring how input and messages were handled, so requests could be processed right away.

### 3. Listing Clients
When I first tried to list clients, the output only showed on the server console. I reworked the logic so that the client receives the list as a message and displays it properly.

### 4. Keeping Chats Open
After a connection was accepted, I needed a way to keep the chat session going without repeatedly asking who to message. I added state to track the current partner so that all messages flow directly between the two clients until the session ends.

### 5. Displaying Messages
I wanted the chat to look more like a conversation. I experimented with clearing the terminal and redrawing the conversation history so the interface was cleaner, though this introduced new difficulties.

---

## Features and File Responsibilities

### `Message.java`
- Defines the format for all messages exchanged in the system.  
- Supports message types for system commands, connection requests, accept/deny responses, and chat messages.  

### `Server.java`
- Accepts incoming connections from clients.  
- Assigns a unique ID to each client.  
- Keeps track of connected clients and forwards messages between them.  
- Handles listing of clients, connection requests, and relaying of chat messages.  

### `Client.java`
- Prompts for a username when started.  
- Allows a user to list connected clients or request a connection with a specific client.  
- Displays incoming connection requests and asks the user to accept or deny them.  
- Once connected, maintains a direct chat session and displays conversation history.  

---
## Existing Issues

<<<<<<< HEAD
## Existing Issues

The user interface is still buggy and has been the hardest part of this project. Trying to balance incoming requests, user prompts, and displaying chat history cleanly has been extremely difficult. Clearing the screen and redrawing history often causes awkward behavior. I am still working to fix these issues to make the interface smoother and more reliable.
=======
There are many existing issues with the user interface, which has honestly been the hardest part of this project. Trying to display incoming connections, accept them and also display message history has been extremely difficult and buggy but I am still working to fix it.
>>>>>>> f9937123bdb90b288c93d7b8eb54c969e7cb92df

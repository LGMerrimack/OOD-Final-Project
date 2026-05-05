import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

// Server-side chat application supporting multiple simultaneous clients across named chatrooms
public class CServer {

    private static final String DEFAULT_ROOM_NAME = "General";
    private static final Pattern ROOM_NAME_RE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9 _-]{2,23}$");
    private static final Object ROOMS_LOCK = new Object();

    // Predefined chatrooms available to all connecting clients
    private static final List<ChatRoom> rooms = new CopyOnWriteArrayList<>();
    private static final ConcurrentMap<String, ClientHandler> onlineClients = new ConcurrentHashMap<>();

    // Starts the server and waits for clients to connect.
    public static void main(String[] args) {
        int port = 3500; // Port to listen on; must match the port used in client

        // Ensure users.txt exists so login/register works from the start
        java.io.File usersFile = new java.io.File("users.txt");
        if (!usersFile.exists()) {
            try {
                usersFile.createNewFile();
                System.out.println("Created users.txt");
            } catch (java.io.IOException e) {
                System.err.println("Warning: could not create users.txt — " + e.getMessage());
            }
        }

        rooms.add(new ChatRoom(DEFAULT_ROOM_NAME));
        rooms.add(new ChatRoom("Gaming"));
        rooms.add(new ChatRoom("Tech"));

        ExecutorService pool = Executors.newCachedThreadPool();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started on port " + port + ". Waiting for clients...");

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New connection from: " + socket.getInetAddress());
                pool.execute(new ClientHandler(socket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static ChatRoom getDefaultRoom() {
        for (ChatRoom room : rooms) {
            if (room.getName().equalsIgnoreCase(DEFAULT_ROOM_NAME)) {
                return room;
            }
        }
        return rooms.isEmpty() ? null : rooms.get(0);
    }

    private static ChatRoom findRoomByName(String roomName) {
        for (ChatRoom room : rooms) {
            if (room.getName().equalsIgnoreCase(roomName)) {
                return room;
            }
        }
        return null;
    }

    private static String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    // This is where new private rooms get made and checked before they are added
    // to the live room list.
    private static ChatRoom createPrivateRoom(String roomName, String ownerUsername) {
        synchronized (ROOMS_LOCK) {
            String trimmedName = roomName == null ? "" : roomName.trim();
            if (!ROOM_NAME_RE.matcher(trimmedName).matches()) {
                throw new IllegalArgumentException(
                        "Room names must be 3-24 characters using letters, digits, spaces, underscores, or hyphens.");
            }
            if (findRoomByName(trimmedName) != null) {
                throw new IllegalArgumentException("A room with that name already exists.");
            }

            ChatRoom room = new ChatRoom(trimmedName, true, ownerUsername);
            rooms.add(room);
            return room;
        }
    }

    // Deleting a private room is handled here once the owner is allowed to remove it.
    private static void deleteRoom(ChatRoom room) {
        synchronized (ROOMS_LOCK) {
            rooms.remove(room);
        }
    }

    // The login room picker uses this list, so private rooms show up here too.
    private static void sendRoomList(PrintWriter output) {
        output.println("ROOMS_BEGIN");
        for (int i = 0; i < rooms.size(); i++) {
            ChatRoom room = rooms.get(i);
            String owner = room.getOwnerUsername() == null ? "-" : room.getOwnerUsername();
            output.println((i + 1) + ":" + room.getName() + ":" + room.getClientCount() + ":"
                    + room.isPrivate() + ":" + owner);
        }
        output.println("ROOMS_END");
    }

    private static String buildRoomListPayload() {
        StringJoiner joiner = new StringJoiner(";");
        for (int i = 0; i < rooms.size(); i++) {
            ChatRoom room = rooms.get(i);
            String owner = room.getOwnerUsername() == null ? "-" : room.getOwnerUsername();
            joiner.add((i + 1) + ":" + room.getName() + ":" + room.getClientCount() + ":"
                    + room.isPrivate() + ":" + owner);
        }
        return joiner.toString();
    }

    private static void broadcastRoomListUpdate() {
        String payload = "CONTROL:ROOM_LIST:" + buildRoomListPayload();
        for (ClientHandler client : onlineClients.values()) {
            if (client.joinedRoom) {
                client.sendControl(payload);
            }
        }
    }

    private static class ClientHandler implements Runnable {

        private final Socket socket;
        private volatile ChatRoom selectedRoom;
        private PrintWriter output;
        private BufferedReader input;
        private String username;
        private boolean joinedRoom;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        private void sendNotice(String message) {
            output.println("CONTROL:NOTICE:" + message);
        }

        private void sendControl(String message) {
            if (output != null) {
                output.println(message);
            }
        }

        private boolean registerOnlineClient() {
            return onlineClients.putIfAbsent(normalizeUsername(username), this) == null;
        }

        private void unregisterOnlineClient() {
            if (username != null) {
                onlineClients.remove(normalizeUsername(username), this);
            }
        }

        // When a room changes, this keeps the client window in sync with the real room state.
        private void sendRoomSync() {
            String owner = selectedRoom.getOwnerUsername() == null ? "-" : selectedRoom.getOwnerUsername();
            output.println("CONTROL:ROOM_SYNC:" + selectedRoom.getName() + ":" + selectedRoom.isPrivate() + ":"
                    + owner);
        }

        private void broadcastRoomDeparture(ChatRoom room) {
            room.broadcastEncrypted(EncryptionManager.encryptMessage(
                    "[" + room.getName() + "] " + username + " has left the chat."));
            System.out.println(username + " left room: " + room.getName());
        }

        private void broadcastRoomArrival(ChatRoom room) {
            room.broadcastEncrypted(EncryptionManager.encryptMessage(
                    "[" + room.getName() + "] " + username + " has joined the chat."));
            System.out.println(username + " joined room: " + room.getName());
        }

        private void switchToRoom(ChatRoom newRoom, boolean announceJoin) {
            ChatRoom previousRoom;

            synchronized (this) {
                previousRoom = selectedRoom;
                if (previousRoom != null) {
                    previousRoom.removeClient(output);
                }

                selectedRoom = newRoom;
                selectedRoom.addClient(output);
                sendRoomSync();
            }

            if (previousRoom != null) {
                broadcastRoomDeparture(previousRoom);
            }

            if (announceJoin) {
                broadcastRoomArrival(newRoom);
            }

            broadcastRoomListUpdate();
        }

        private synchronized boolean isInRoom(ChatRoom room) {
            return selectedRoom == room;
        }

        private void moveToInvitedRoom(ChatRoom room, String inviterUsername) {
            switchToRoom(room, true);
            sendNotice(inviterUsername + " added you to room '" + room.getName() + "'.");
        }

        // The actual private room commands are handled here.
        private boolean handleRoomCommand(String decryptedMessage) {
            if (decryptedMessage.startsWith("/join-room ")) {
                String roomNumberText = decryptedMessage.substring("/join-room ".length()).trim();
                if (roomNumberText.isEmpty()) {
                    throw new IllegalArgumentException("Choose a room to join.");
                }

                int roomIndex;
                try {
                    roomIndex = Integer.parseInt(roomNumberText) - 1;
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid room number.");
                }

                if (roomIndex < 0 || roomIndex >= rooms.size()) {
                    throw new IllegalArgumentException("That room is no longer available.");
                }

                ChatRoom targetRoom = rooms.get(roomIndex);
                if (selectedRoom == targetRoom) {
                    throw new IllegalArgumentException("You are already in that room.");
                }

                switchToRoom(targetRoom, true);
                sendNotice("Joined room '" + targetRoom.getName() + "'.");
                return true;
            }

            if (decryptedMessage.startsWith("/create-private ")) {
                String roomName = decryptedMessage.substring("/create-private ".length()).trim();
                ChatRoom privateRoom = createPrivateRoom(roomName, username);
                switchToRoom(privateRoom, true);
                sendNotice("Private room '" + privateRoom.getName() + "' created.");
                return true;
            }

            if (decryptedMessage.equalsIgnoreCase("/delete-room")) {
                if (selectedRoom == null || !selectedRoom.isPrivate()) {
                    throw new IllegalArgumentException("You are not in a private room.");
                }
                if (!selectedRoom.isOwnedBy(username)) {
                    throw new IllegalArgumentException("Only the private room owner can delete this room.");
                }
                if (selectedRoom.getClientCount() > 1) {
                    throw new IllegalArgumentException("Private rooms can only be deleted when empty except for you.");
                }

                ChatRoom roomToDelete = selectedRoom;
                ChatRoom fallbackRoom = getDefaultRoom();
                if (fallbackRoom == null || fallbackRoom == roomToDelete) {
                    throw new IllegalStateException("No fallback room is available.");
                }

                switchToRoom(fallbackRoom, true);
                deleteRoom(roomToDelete);
                broadcastRoomListUpdate();
                sendNotice("Private room '" + roomToDelete.getName() + "' deleted.");
                return true;
            }

            if (decryptedMessage.startsWith("/invite-user ")) {
                if (selectedRoom == null || !selectedRoom.isPrivate()) {
                    throw new IllegalArgumentException("Invites are only available inside a private room.");
                }
                if (!selectedRoom.isOwnedBy(username)) {
                    throw new IllegalArgumentException("Only the private room owner can invite users.");
                }

                String invitedUsername = decryptedMessage.substring("/invite-user ".length()).trim();
                if (invitedUsername.isEmpty()) {
                    throw new IllegalArgumentException("Enter a username to invite.");
                }
                if (invitedUsername.equalsIgnoreCase(username)) {
                    throw new IllegalArgumentException("You are already in this room.");
                }

                ClientHandler invitedClient = onlineClients.get(normalizeUsername(invitedUsername));
                if (invitedClient == null || !invitedClient.joinedRoom) {
                    throw new IllegalArgumentException("User '" + invitedUsername + "' is not online.");
                }
                if (invitedClient.isInRoom(selectedRoom)) {
                    throw new IllegalArgumentException("User '" + invitedClient.username + "' is already in this room.");
                }

                invitedClient.moveToInvitedRoom(selectedRoom, username);
                sendNotice("Added '" + invitedClient.username + "' to room '" + selectedRoom.getName() + "'.");
                return true;
            }

            return false;
        }

        private boolean handleFileTransfer(String decryptedMessage) {
            if (decryptedMessage.startsWith("/file-start|")) {
                String[] parts = decryptedMessage.split("\\|", 4);
                if (parts.length != 4) {
                    throw new IllegalArgumentException("Invalid file start packet.");
                }

                String transferId = parts[1];
                String fileName = parts[2];
                String mimeType = parts[3];

                String relay = "FILE_START|" + username + "|" + transferId + "|" + fileName + "|" + mimeType;
                selectedRoom.broadcastEncrypted(EncryptionManager.encryptMessage(relay));
                return true;
            }

            if (decryptedMessage.startsWith("/file-chunk|")) {
                String[] parts = decryptedMessage.split("\\|", 3);
                if (parts.length != 3) {
                    throw new IllegalArgumentException("Invalid file chunk packet.");
                }

                String transferId = parts[1];
                String base64Chunk = parts[2];

                String relay = "FILE_CHUNK|" + username + "|" + transferId + "|" + base64Chunk;
                selectedRoom.broadcastEncrypted(EncryptionManager.encryptMessage(relay));
                return true;
            }

            if (decryptedMessage.startsWith("/file-end|")) {
                String[] parts = decryptedMessage.split("\\|", 2);
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Invalid file end packet.");
                }

                String transferId = parts[1];

                String relay = "FILE_END|" + username + "|" + transferId;
                selectedRoom.broadcastEncrypted(EncryptionManager.encryptMessage(relay));
                return true;
            }

            return false;
        }

        @Override
        public void run() {
            try (Socket s = socket) {
                input = new BufferedReader(new InputStreamReader(s.getInputStream()));
                output = new PrintWriter(s.getOutputStream(), true);

                output.println("Login or New User? (login/new):");
                String authChoice = input.readLine();
                if (authChoice == null)
                    return;

                output.println("Enter username:");
                username = input.readLine();
                if (username == null)
                    return;

                output.println("Enter password hash:");
                String transmittedHash = input.readLine();
                if (transmittedHash == null)
                    return;

                AuthenticationManager.AuthResult authResult;

                if (authChoice.trim().equalsIgnoreCase("login")) {
                    authResult = AuthenticationManager.loginUserWithHash(username, transmittedHash);
                    if (!authResult.isSuccess()) {
                        output.println("AUTH_FAIL:" + authResult.getMessage());
                        return;
                    }

                } else if (authChoice.trim().equalsIgnoreCase("new")) {
                    authResult = AuthenticationManager.registerUserWithHash(username, transmittedHash);
                    if (!authResult.isSuccess()) {
                        output.println("AUTH_FAIL:" + authResult.getMessage());
                        return;
                    }

                } else {
                    output.println("AUTH_FAIL:Invalid option.");
                    return;
                }

                output.println("AUTH_OK:" + authResult.getMessage());

                sendRoomList(output);
                output.println("Enter room number:");

                while (selectedRoom == null) {
                    String choice = input.readLine();
                    if (choice == null)
                        return;
                    try {
                        int index = Integer.parseInt(choice.trim()) - 1;
                        if (index >= 0 && index < rooms.size()) {
                            selectedRoom = rooms.get(index);
                        } else {
                            output.println("Invalid choice. Enter room number:");
                        }
                    } catch (NumberFormatException e) {
                        output.println("Invalid choice. Enter room number:");
                    }
                }

                if (!registerOnlineClient()) {
                    output.println("AUTH_FAIL:Username is already logged in.");
                    return;
                }

                output.println("Joined: " + selectedRoom.getName() + ". Type 'exit' to leave.");
                selectedRoom.addClient(output);
                joinedRoom = true;
                sendRoomSync();
                broadcastRoomListUpdate();

                broadcastRoomArrival(selectedRoom);

                String message;
                while ((message = input.readLine()) != null) {
                    String decryptedMessage = EncryptionManager.decryptMessage(message);

                    if (decryptedMessage.equalsIgnoreCase("exit"))
                        break;

                    try {
                        if (handleRoomCommand(decryptedMessage)) {
                            continue;
                        }

                        if (handleFileTransfer(decryptedMessage)) {
                            continue;
                        }
                    } catch (IllegalArgumentException e) {
                        output.println("ERROR:" + e.getMessage());
                        continue;
                    }

                    String finalMessage = username + ": " + decryptedMessage;
                    selectedRoom.broadcastEncrypted(EncryptionManager.encryptMessage(finalMessage));
                }

            } catch (IOException e) {
                e.printStackTrace();
            } catch (RuntimeException e) {
                if (output != null) {
                    output.println("ERROR:" + e.getMessage());
                }
            } finally {
                unregisterOnlineClient();
                if (joinedRoom && selectedRoom != null && output != null && username != null) {
                    selectedRoom.removeClient(output);
                    broadcastRoomDeparture(selectedRoom);
                    if (selectedRoom.isPrivate() && selectedRoom.isOwnedBy(username) && selectedRoom.isEmpty()) {
                        deleteRoom(selectedRoom);
                        System.out.println("Deleted empty private room: " + selectedRoom.getName());
                    }
                    broadcastRoomListUpdate();
                }
            }
        }
    }
}
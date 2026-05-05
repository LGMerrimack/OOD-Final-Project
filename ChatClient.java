/* ChatClient.java
 
 Networking bridge between the GUI and CServer.
 Called by LoginWindow after local validation passes.
 Drives MainChatWindow via its public API.
 
 To test locally:  SERVER_ADDRESS = "localhost"  (default)
 To test over LAN: SERVER_ADDRESS = "192.168.x.x" (your PC's local IP)
 To test over WAN: SERVER_ADDRESS = your public IP + port-forward 3500
 */
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.SwingUtilities;

public class ChatClient {

    // The client keeps room details here so the chooser and the main window can
    // show public and private rooms the same way.
    public static class RoomInfo {
        private final int number;
        private final String name;
        private final int clientCount;
        private final boolean privateRoom;
        private final String ownerUsername;

        public RoomInfo(int number, String name, int clientCount, boolean privateRoom, String ownerUsername) {
            this.number = number;
            this.name = name;
            this.clientCount = clientCount;
            this.privateRoom = privateRoom;
            this.ownerUsername = ownerUsername;
        }

        public int getNumber() {
            return number;
        }

        public String getName() {
            return name;
        }

        public int getClientCount() {
            return clientCount;
        }

        public boolean isPrivateRoom() {
            return privateRoom;
        }

        public String getOwnerUsername() {
            return ownerUsername;
        }

        public boolean isOwnedBy(String username) {
            return ownerUsername != null && ownerUsername.equalsIgnoreCase(username);
        }

        @Override
        public String toString() {
            StringBuilder label = new StringBuilder(name).append(" (").append(clientCount).append(" online)");
            if (privateRoom) {
                label.append("  [Private]");
                if (ownerUsername != null && !ownerUsername.isBlank() && !ownerUsername.equals("-")) {
                    label.append("  owner: ").append(ownerUsername);
                }
            }
            return label.toString();
        }
    }

    private static class IncomingFileTransfer {
        private final String sender;
        private final String transferId;
        private final String fileName;
        private final String mimeType;
        private final StringBuilder base64Data = new StringBuilder();

        public IncomingFileTransfer(String sender, String transferId, String fileName, String mimeType) {
            this.sender = sender;
            this.transferId = transferId;
            this.fileName = fileName;
            this.mimeType = mimeType;
        }
    }

    
    private static String SERVER_ADDRESS = "10.2.146.110";
    private static final int SERVER_PORT = 3500;

    private static final int FILE_CHUNK_SIZE = 12000;

    public static void setServerAddress(String address) {
        SERVER_ADDRESS = address;
    }

    private final String username;
    private final String password;
    private final boolean isNewUser;
    private final MainChatWindow chatWindow;

    private Socket socket;
    private PrintWriter output;
    private BufferedReader input;
    private volatile boolean running = false;
    private RoomInfo selectedRoom;

    private final Map<String, IncomingFileTransfer> incomingTransfers = new ConcurrentHashMap<>();

    public ChatClient(String username, String password, boolean isNewUser, MainChatWindow chatWindow) {
        this.username = username;
        this.password = password;
        this.isNewUser = isNewUser;
        this.chatWindow = chatWindow;
    }

    public void connect() {
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(SERVER_ADDRESS, SERVER_PORT), 10000);
            input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            output = new PrintWriter(socket.getOutputStream(), true);

            String line;
            List<RoomInfo> availableRooms = List.of();
            while ((line = input.readLine()) != null) {

                if (line.startsWith("Login or New User?")) {
                    output.println(isNewUser ? "new" : "login");

                } else if (line.equals("Enter username:")) {
                    output.println(username);

                } else if (line.equals("Enter password hash:")) {
                    AuthenticationManager.AuthResult v = AuthenticationManager.validateCredentials(username, password);
                    if (!v.isSuccess()) {
                        notifySystem("Auth error: " + v.getMessage());
                        disconnect();
                        return;
                    }
                    output.println(AuthenticationManager.hashPasswordForTransmission(password));

                } else if (line.startsWith("AUTH_OK:")) {
                    notifySystem("Good " + line.substring("AUTH_OK:".length()));

                } else if (line.startsWith("AUTH_FAIL:")) {
                    notifySystem("Error" + line.substring("AUTH_FAIL:".length()));
                    disconnect();
                    return;

                } else if (line.equals("ROOMS_BEGIN")) {
                    availableRooms = readRoomList();
                    chatWindow.setKnownRooms(availableRooms);

                } else if (line.equals("Enter room number:") ||
                        line.startsWith("Invalid choice")) {
                    // Auto-join General (room 1) — user can switch via Available Rooms after connecting
                    selectedRoom = availableRooms.isEmpty() ? null : availableRooms.get(0);
                    if (selectedRoom == null) {
                        notifySystem("No rooms are available.");
                        disconnect();
                        return;
                    }
                    output.println(selectedRoom.getNumber());

                } else if (line.startsWith("Joined:")) {
                    final String msg = line;
                    SwingUtilities.invokeLater(() -> {
                        chatWindow.setConnected(true);
                        chatWindow.resetUsers(username);
                        chatWindow.appendSystemMessage(msg);
                        if (selectedRoom != null) {
                            chatWindow.setCurrentRoom(selectedRoom.getName(),
                                    selectedRoom.isPrivateRoom(),
                                    selectedRoom.isOwnedBy(username));
                        }
                    });
                    break;

                } else {
                    notifySystem(line);
                }
            }

            running = true;
            Thread listener = new Thread(this::listenLoop, "ChatClient-Listener");
            listener.setDaemon(true);
            listener.start();

        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                chatWindow.setConnected(false);
                chatWindow.appendSystemMessage(
                        "Could not connect to server at " + SERVER_ADDRESS
                                + ":" + SERVER_PORT + " — " + e.getMessage());
            });
        }
    }

    // The server sends the room list during login, including any private rooms.
    private List<RoomInfo> readRoomList() throws IOException {
        List<RoomInfo> rooms = new ArrayList<>();
        String line;
        while ((line = input.readLine()) != null && !line.equals("ROOMS_END")) {
            String[] parts = line.split(":", 5);
            if (parts.length < 5)
                continue;

            try {
                rooms.add(new RoomInfo(
                        Integer.parseInt(parts[0]),
                        parts[1],
                        Integer.parseInt(parts[2]),
                        Boolean.parseBoolean(parts[3]),
                        "-".equals(parts[4]) ? null : parts[4]));
            } catch (NumberFormatException ignored) {
            }
        }
        return rooms;
    }

    private void listenLoop() {
        try {
            String raw;
            while (running && (raw = input.readLine()) != null) {
                final String line = raw;

                if (line.startsWith("ERROR:")) {
                    notifySystem("Server: " + line.substring(6));
                    continue;
                }

                if (line.startsWith("CONTROL:")) {
                    handleControlMessage(line);
                    continue;
                }

                String decrypted;
                try {
                    decrypted = EncryptionManager.decryptMessage(line);
                } catch (RuntimeException e) {
                    notifySystem("[Could not decrypt a message]");
                    continue;
                }

                if (handleIncomingFilePacket(decrypted)) {
                    continue;
                }

                final String msg = decrypted;

                if (msg.contains(" has joined the chat.")) {
                    String user = extractUser(msg, " has joined the chat.");
                    SwingUtilities.invokeLater(() -> {
                        chatWindow.addUser(user);
                        chatWindow.appendSystemMessage(msg);
                    });
                } else if (msg.contains(" has left the chat.")) {
                    String user = extractUser(msg, " has left the chat.");
                    SwingUtilities.invokeLater(() -> {
                        chatWindow.removeUser(user);
                        chatWindow.appendSystemMessage(msg);
                    });
                } else if (msg.contains(": ")) {
                    int sep = msg.indexOf(": ");
                    String sender = msg.substring(0, sep);
                    String content = msg.substring(sep + 2);
                    if (!sender.equals(username)) {
                        SwingUtilities.invokeLater(() -> chatWindow.appendMessage(sender, content));
                    }
                } else {
                    notifySystem(msg);
                }
            }
        } catch (IOException e) {
            if (running) {
                SwingUtilities.invokeLater(() -> chatWindow.setConnected(false));
            }
        }
    }

    private boolean handleIncomingFilePacket(String decrypted) {
        if (decrypted.startsWith("FILE_START|")) {
            String[] parts = decrypted.split("\\|", 5);
            if (parts.length < 5) {
                notifySystem("Received malformed file start packet.");
                return true;
            }

            String sender = parts[1];
            String transferId = parts[2];
            String fileName = parts[3];
            String mimeType = parts[4];

            if (sender.equals(username)) {
                return true;
            }

            incomingTransfers.put(transferId, new IncomingFileTransfer(sender, transferId, fileName, mimeType));
            notifySystem(sender + " started sending file: " + fileName);
            return true;
        }

        if (decrypted.startsWith("FILE_CHUNK|")) {
            String[] parts = decrypted.split("\\|", 4);
            if (parts.length < 4) {
                notifySystem("Received malformed file chunk packet.");
                return true;
            }

            String transferId = parts[2];
            String base64Chunk = parts[3];

            IncomingFileTransfer transfer = incomingTransfers.get(transferId);
            if (transfer != null) {
                transfer.base64Data.append(base64Chunk);
            }
            return true;
        }

        if (decrypted.startsWith("FILE_END|")) {
            String[] parts = decrypted.split("\\|", 3);
            if (parts.length < 3) {
                notifySystem("Received malformed file end packet.");
                return true;
            }

            String transferId = parts[2];
            IncomingFileTransfer transfer = incomingTransfers.remove(transferId);

            if (transfer != null) {
                saveIncomingFile(transfer);
            }
            return true;
        }

        return false;
    }

    private void saveIncomingFile(IncomingFileTransfer transfer) {
        try {
            byte[] fileBytes = Base64.getDecoder().decode(transfer.base64Data.toString());

            File saveDir = new File("received_files");
            if (!saveDir.exists()) {
                saveDir.mkdirs();
            }

            String safeName = System.currentTimeMillis() + "_" + transfer.fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
            File outFile = new File(saveDir, safeName);

            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(fileBytes);
            }

            if (transfer.mimeType != null && transfer.mimeType.regionMatches(true, 0, "image/", 0, 6)) {
                chatWindow.appendImageMessage(transfer.sender, outFile);
                notifySystem(transfer.sender + " shared image: " + transfer.fileName +
                        " saved to " + outFile.getPath());
            } else {
                notifySystem(transfer.sender + " sent file: " + transfer.fileName +
                        " (" + transfer.mimeType + ") saved to " + outFile.getPath());
            }
        } catch (Exception e) {
            notifySystem("Failed to save incoming file: " + e.getMessage());
        }
    }

    // Room create/delete updates come back through these control messages so the
    // window can refresh itself without reconnecting.
    private void handleControlMessage(String line) {
        if (line.startsWith("CONTROL:NOTICE:")) {
            notifySystem(line.substring("CONTROL:NOTICE:".length()));
            return;
        }

        if (line.startsWith("CONTROL:ROOM_LIST:")) {
            List<RoomInfo> rooms = parseRoomList(line.substring("CONTROL:ROOM_LIST:".length()));
            chatWindow.setKnownRooms(rooms);
            if (selectedRoom != null) {
                for (RoomInfo room : rooms) {
                    if (room.getName().equalsIgnoreCase(selectedRoom.getName())) {
                        selectedRoom = room;
                        break;
                    }
                }
            }
            return;
        }

        if (line.startsWith("CONTROL:ROOM_SYNC:")) {
            String[] parts = line.substring("CONTROL:ROOM_SYNC:".length()).split(":", 3);
            if (parts.length < 3)
                return;

            String roomName = parts[0];
            boolean privateRoom = Boolean.parseBoolean(parts[1]);
            String ownerUsername = "-".equals(parts[2]) ? null : parts[2];

            selectedRoom = new RoomInfo(
                    selectedRoom == null ? 1 : selectedRoom.getNumber(),
                    roomName,
                    selectedRoom == null ? 0 : selectedRoom.getClientCount(),
                    privateRoom,
                    ownerUsername);

            SwingUtilities.invokeLater(() -> {
                chatWindow.setCurrentRoom(roomName, privateRoom,
                        ownerUsername != null && ownerUsername.equalsIgnoreCase(username));
                chatWindow.resetUsers(username);
            });
        }
    }

    private List<RoomInfo> parseRoomList(String payload) {
        List<RoomInfo> rooms = new ArrayList<>();
        if (payload == null || payload.isBlank())
            return rooms;

        for (String entry : payload.split(";")) {
            if (entry.isBlank())
                continue;

            String[] parts = entry.split(":", 5);
            if (parts.length < 5)
                continue;

            try {
                rooms.add(new RoomInfo(
                        Integer.parseInt(parts[0]),
                        parts[1],
                        Integer.parseInt(parts[2]),
                        Boolean.parseBoolean(parts[3]),
                        "-".equals(parts[4]) ? null : parts[4]));
            } catch (NumberFormatException ignored) {
            }
        }

        return rooms;
    }

    public void sendMessage(String plaintext) {
        if (output == null || !running)
            return;
        try {
            output.println(EncryptionManager.encryptMessage(plaintext));
        } catch (RuntimeException e) {
            notifySystem("Encryption failed: " + e.getMessage());
        }
    }

    public boolean sendFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            notifySystem("Selected file is invalid.");
            return false;
        }

        if (output == null || !running) {
            notifySystem("You are not connected.");
            return false;
        }

        try {
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            String base64 = Base64.getEncoder().encodeToString(fileBytes);
            String transferId = UUID.randomUUID().toString();

            String mimeType = Files.probeContentType(file.toPath());
            if (mimeType == null || mimeType.isBlank()) {
                mimeType = "application/octet-stream";
            }

            output.println(EncryptionManager.encryptMessage(
                    "/file-start|" + transferId + "|" + file.getName() + "|" + mimeType));

            for (int i = 0; i < base64.length(); i += FILE_CHUNK_SIZE) {
                int end = Math.min(i + FILE_CHUNK_SIZE, base64.length());
                String chunk = base64.substring(i, end);

                output.println(EncryptionManager.encryptMessage(
                        "/file-chunk|" + transferId + "|" + chunk));
            }

            output.println(EncryptionManager.encryptMessage("/file-end|" + transferId));

            notifySystem("Sent file: " + file.getName());
            return true;
        } catch (Exception e) {
            notifySystem("Failed to send file: " + e.getMessage());
            return false;
        }
    }

    // These two helpers are what the private room buttons call.
    public void createPrivateRoom(String roomName) {
        sendMessage("/create-private " + roomName);
    }

    public void inviteUserToCurrentRoom(String invitedUsername) {
        sendMessage("/invite-user " + invitedUsername);
    }

    public void deleteCurrentPrivateRoom() {
        sendMessage("/delete-room");
    }

    public void disconnect() {
        running = false;
        try {
            if (output != null)
                output.println(EncryptionManager.encryptMessage("exit"));
            if (socket != null)
                socket.close();
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(() -> chatWindow.setConnected(false));
    }

    private void notifySystem(String msg) {
        SwingUtilities.invokeLater(() -> chatWindow.appendSystemMessage(msg));
    }

    private String extractUser(String msg, String suffix) {
        try {
            int start = msg.indexOf(']') + 2;
            int end = msg.indexOf(suffix);
            if (start > 0 && end > start)
                return msg.substring(start, end);
        } catch (Exception ignored) {
        }
        return "Unknown";
    }
}
import java.io.PrintWriter;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChatRoom {
    private final String name;
    // Private room support starts here: each room now knows if it is private
    // and who created it.
    private final boolean privateRoom;
    private final String ownerUsername;

    private final Set<PrintWriter> clients = ConcurrentHashMap.newKeySet();

    public ChatRoom(String name) {
        this(name, false, null);
    }

    public ChatRoom(String name, boolean privateRoom, String ownerUsername) {
        this.name = name;
        this.privateRoom = privateRoom;
        this.ownerUsername = ownerUsername;
    }

    public String getName() {
        return name;
    }

    public boolean isPrivate() {
        return privateRoom;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public boolean isOwnedBy(String username) {
        return ownerUsername != null && ownerUsername.equalsIgnoreCase(username);
    }

    public int getClientCount() {
        return clients.size();
    }

    public boolean isEmpty() {
        return clients.isEmpty();
    }

    public void addClient(PrintWriter writer) {
        clients.add(writer);
    }

    public void removeClient(PrintWriter writer) {
        clients.remove(writer);
    }

    public void broadcastEncrypted(String encryptedMessage) {
        for (PrintWriter client : clients) {
            try {
                client.println(encryptedMessage);
            } catch (Exception e) {
                // ignore individual send failures
            }
        }
    }
}
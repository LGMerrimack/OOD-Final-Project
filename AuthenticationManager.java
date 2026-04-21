import java.io.*;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class AuthenticationManager {

    private static final String USERS_FILE = "users.txt";

    private static final Pattern USER_RE = Pattern.compile("^\\w{3,20}$");
    private static final Pattern PASS_RE = Pattern.compile(
            "^(?=.*[0-9])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{8,}$");

    public static class AuthResult {
        private final boolean success;
        private final String message;

        public AuthResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }

    public static AuthResult validateCredentials(String username, String password) {
        if (!USER_RE.matcher(username).matches())
            return new AuthResult(false, "Invalid username format.");
        if (!PASS_RE.matcher(password).matches())
            return new AuthResult(false, "Invalid password format.");
        return new AuthResult(true, "Format OK.");
    }

    /**
     * Hashes the password using SHA-256 before transmission/storage.
     */
    public static String hashPasswordForTransmission(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Hashing failed: " + e.getMessage());
        }
    }

    public static AuthResult loginUserWithHash(String username, String transmittedHash) {
        HashMap<String, String> users = loadUsers();
        if (users.isEmpty())
            return new AuthResult(false, "User database not found.");
        if (!users.containsKey(username))
            return new AuthResult(false, "Username not found.");
        if (!users.get(username).equals(transmittedHash))
            return new AuthResult(false, "Incorrect password.");
        return new AuthResult(true, "Login successful. Welcome, " + username + "!");
    }

    public static AuthResult registerUserWithHash(String username, String transmittedHash) {
        HashMap<String, String> users = loadUsers();
        if (users.containsKey(username))
            return new AuthResult(false, "Username already taken.");

        try (FileWriter fw = new FileWriter(USERS_FILE, true)) {
            fw.write("\n" + username + ":" + transmittedHash);
            return new AuthResult(true, "Account created. Welcome, " + username + "!");
        } catch (IOException e) {
            return new AuthResult(false, "Could not save account: " + e.getMessage());
        }
    }

    private static HashMap<String, String> loadUsers() {
        HashMap<String, String> users = new HashMap<>();
        File file = new File(USERS_FILE);
        if (!file.exists()) {
            return users;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#"))
                    continue;
                String[] parts = line.split(":", 2);
                if (parts.length == 2)
                    users.put(parts[0].trim(), parts[1].trim());
            }
        } catch (IOException e) {
            System.err.println("[AuthenticationManager] Error reading " + USERS_FILE + ": " + e.getMessage());
        }
        return users;
    }
}
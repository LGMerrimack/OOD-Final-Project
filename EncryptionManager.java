import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class EncryptionManager {

    public interface EncryptionStrategy {
        String encrypt(String plaintext);
        String decrypt(String ciphertext);
    }

    public static class AESEncryptionStrategy implements EncryptionStrategy {
        private final String aesKey;

        public AESEncryptionStrategy(String aesKey) {
            this.aesKey = aesKey;
        }

        @Override
        public String encrypt(String plaintext) {
            try {
                SecretKeySpec key = new SecretKeySpec(aesKey.getBytes(StandardCharsets.UTF_8), "AES");
                Cipher cipher = Cipher.getInstance("AES");
                cipher.init(Cipher.ENCRYPT_MODE, key);
                byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
                return Base64.getEncoder().encodeToString(encrypted);
            } catch (Exception e) {
                throw new RuntimeException("Encryption failed: " + e.getMessage());
            }
        }

        @Override
        public String decrypt(String ciphertext) {
            try {
                SecretKeySpec key = new SecretKeySpec(aesKey.getBytes(StandardCharsets.UTF_8), "AES");
                Cipher cipher = Cipher.getInstance("AES");
                cipher.init(Cipher.DECRYPT_MODE, key);
                byte[] decoded = Base64.getDecoder().decode(ciphertext);
                return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new RuntimeException("Decryption failed: " + e.getMessage());
            }
        }
    }

    public static class NoEncryptionStrategy implements EncryptionStrategy {
        @Override
        public String encrypt(String plaintext) {
            return plaintext;
        }

        @Override
        public String decrypt(String ciphertext) {
            return ciphertext;
        }
    }

    private static final String AES_KEY = "p36-XyZ-99-abc-1";
    private static EncryptionStrategy strategy = new AESEncryptionStrategy(AES_KEY);

    public static void setStrategy(EncryptionStrategy newStrategy) {
        if (newStrategy == null) {
            throw new IllegalArgumentException("Strategy cannot be null.");
        }
        strategy = newStrategy;
    }

    public static String encryptMessage(String plaintext) {
        return strategy.encrypt(plaintext);
    }

    public static String decryptMessage(String ciphertext) {
        return strategy.decrypt(ciphertext);
    }

    public static String encryptBytes(byte[] data) {
        String base64 = Base64.getEncoder().encodeToString(data);
        return encryptMessage(base64);
    }

    public static byte[] decryptToBytes(String encryptedPayload) {
        String base64 = decryptMessage(encryptedPayload);
        return Base64.getDecoder().decode(base64);
    }

    public static String encryptBase64String(String base64) {
        return encryptMessage(base64);
    }

    public static String decryptToBase64String(String encryptedPayload) {
        return decryptMessage(encryptedPayload);
    }
}
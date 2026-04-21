/* CClient.java
 
 Entry point for the client application.
 Now only launches the GUI (the LoginWindow) which drives ChatClient for all
 networking, authentication, and messaging services
 
 The original console input and output logic has been moved into ChatClient.java
 so that it can be driven by the GUI instead of the terminal
 
 To run: java CClient
 NOTE: make sure secureLock.png, users.txt, and (optionally) emojis/ are
 in the directory being ran from
 */

import javax.swing.SwingUtilities;

public class CClient {
    // Starts the app and opens the login window.
    public static void main(String[] args) {
        // launch the Swing GUI on the Event Dispatch Thread
        SwingUtilities.invokeLater(LoginWindow::getInstance);
    }
}
import java.io.*;
import java.net.*;

public class User {
    private String username;
    private Socket userSocket;

    public User(String username, Socket userSocket) {
        this.username = username;
        this.userSocket = userSocket;
    }

    public String getUsername() {
        return username;
    }

    public synchronized void receiveMessage(String message) {
        try {
            DataOutputStream outputStream = new DataOutputStream(userSocket.getOutputStream());
            outputStream.writeUTF(message);
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    
}

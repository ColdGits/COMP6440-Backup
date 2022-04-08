import java.net.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.*;

public class Server {

    // Server information
    private static ServerSocket serverSocket;
    //private static List<User> users = new ArrayList<>();
    private static Map<String, User> users = new HashMap<>();
    private static String databaseUrl = "jdbc:sqlite:mydb.db";
    private static Integer serverPort = 8000;
    private static Integer timeout = 50000;

    // define ClientThread for handling multi-threading issue
    // ClientThread needs to extend Thread and override run() method
    private static class ClientThread extends Thread {
        private final Socket clientSocket;
        private boolean clientAlive = false;
        private User user;

        ClientThread(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            super.run();
            // get client Internet Address and port number
            String clientAddress = clientSocket.getInetAddress().getHostAddress();
            int clientPort = clientSocket.getPort();
            String clientID = "("+ clientAddress + ", " + clientPort + ")";

            System.out.println("===== New connection created for user - " + clientID);
            clientAlive = true;

            // define the dataInputStream to get message (input) from client
            // DataInputStream - used to acquire input from client
            // DataOutputStream - used to send data to client
            DataInputStream dataInputStream = null;
            DataOutputStream dataOutputStream = null;
            try {
                dataInputStream = new DataInputStream(this.clientSocket.getInputStream());
                dataOutputStream = new DataOutputStream(this.clientSocket.getOutputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
            while (clientAlive) {
                try {
                    // get input from client
                    // socket like a door/pipe which connects client and server together
                    // data from client would be read from clientSocket
                    assert dataInputStream != null;
                    assert dataOutputStream != null;
                    dataOutputStream.writeUTF("Please enter your Name");
                    dataOutputStream.flush();
                    
                    // Repeatedly poll for current time in relation to timeout, and whether user has entered input
                    if (!timer(System.currentTimeMillis(), dataInputStream, dataOutputStream)) {
                        break;
                    }
                    String input = dataInputStream.readUTF();
                    if (users.values().stream().anyMatch(e -> e.getUsername().equals(input))) {
                        dataOutputStream.writeUTF("nametaken");
                        break;
                    }
                    // Create new user and place it into hashmap with id.
                    user = new User(input, clientSocket);
                    users.put(clientID, user);

                    // If the user reaches this point without falling out the loop, they have entered into the game
                    // Will now begin accepting guesses.
                    broadcast(user.getUsername() + " is now hunting the word", user);
                    user.receiveMessage("Welcome to Word Bounty " + user.getUsername() + "!");
                    user.receiveMessage("Enter your guess to the prompt!");
                    long start = System.currentTimeMillis();
                    while (true) {
                        if (!timer(start, dataInputStream, dataOutputStream)) {
                            break;
                        }
                        System.out.println(dataInputStream.readUTF());
                    }
                    // Reached end of input, get out of input loop
                    clientAlive = false;

                } catch (EOFException e) {
                    System.out.println("===== the user disconnected, user - " + clientID);
                    clientAlive = false;
                } catch (IOException e) {
                    e.printStackTrace();
                    clientAlive = false;
                }
            }
            // Close the client's connection with the server, and remove them from local storage of current users
            // if they created a username.
            try {
                if (user != null) {
                    users.remove(clientID, user);
                }
                dataInputStream.close();
                dataOutputStream.close();
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            
        }
        
    }
    // Allows for starter of the server to add new prompts, broadcast messages
    private static class GameMasterThread extends Thread {
        private boolean serverOn = true;
        GameMasterThread() {}

        @Override
        public void run() {
            super.run();
            System.out.println("Welcome GameMaster to the Terminal.");
            System.out.println("Commands are:");
            System.err.println("quit: shutdown server");
            System.out.println("add <word> <prompt>: adds word and clue to bounty list");
            System.out.println("broadcast <message>: send message to all users");
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String command;
            try {
                while (serverOn) {
                    command = reader.readLine();
                    List<String> commandArgs = new ArrayList<>(Arrays.asList(command.split(" ")));
                    switch (commandArgs.get(0)) {
                        case "quit":
                            serverOn = false;
                            break;
                        case "add":
                            if (commandArgs.size() < 3) {
                                System.out.println("Usage: add <word> <prompt>");
                            } else {
                                // Remove command from argument
                                commandArgs.remove(0);
                                // Separate word from prompt, and add to database
                                addPrompt(commandArgs.remove(0), String.join(" ", commandArgs));
                                System.out.println("Successfully added word and prompt to the database");
                            }
                            break;
                        case "broadcast":
                            if (commandArgs.size() == 1) {
                                System.out.println("Usage: broadcast <message>");
                            } else {
                                commandArgs.remove(0);
                                broadcast(String.join(" ", commandArgs));
                                System.out.println("Successfully broadcast message");
                            }
                            break;
                        default:
                            System.out.println("Command not recognised");
                            break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            System.exit(0);
        }
    }

    // This uses polling in order to guage the time between when input was requested, and timeout seconds into the future
    // When that gap reaches 0, send false to boot the user out due to lack of input.
    private static boolean timer(long start, DataInputStream input, DataOutputStream output) {
        try {
            while (System.currentTimeMillis() - start < timeout && input.available() == 0) {}
            if (input.available() == 0) {
                output.writeUTF("timeout");
                output.flush();
                return false;
            }
        } catch (IOException e) {
            return false;
        }
        return true;
    }
    // Broadcasts message to all current users, excluding a particular user.
    // Used when broadcasting user entry to all other players.
    private static void broadcast(String message, User excludeUser) {
        users.forEach((id, user) -> {
            if (user != excludeUser) {
                user.receiveMessage(message);
            }
        });
    }
    // Regular broadcast too all user
    private static void broadcast(String message) {
        users.forEach((id, user) -> user.receiveMessage(message));
    }

    private static void addPrompt(String word, String prompt) {
        Connection conn = null;
        try {
            // Given input word(which needs to be unique given constraints of the table), and a
            // prompt, insert them into the database
            conn = DriverManager.getConnection(databaseUrl);
            String sql = "INSERT INTO bounties(word, prompt) VALUES(?,?)";
            // Paramaterised statement prevents sql injection
            PreparedStatement statement = conn.prepareStatement(sql);
            // IMPORTANT: Parameters in Statements index from 1.
            statement.setString(1, word);
            statement.setString(2, prompt);

            statement.executeUpdate();
            
            
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws IOException {        
        

        // define server socket with the input port number, by default the host would be localhost i.e., 127.0.0.1
        serverSocket = new ServerSocket(serverPort);
        
        // make serverSocket listen connection request from clients
        System.out.println("===== WordBounty Server Now Running =====");
        System.out.println("===== Waiting for new Players...=====");

        GameMasterThread master = new GameMasterThread();
        master.start();

        while (true) {
            // when new connection request reaches the server, then server socket establishes connection
            Socket clientSocket = serverSocket.accept();
            // for each user there would be one thread, all the request/response for that user would be processed in that thread
            // different users will be working in different thread which is multi-threading (i.e., concurrent)
            ClientThread clientThread = new ClientThread(clientSocket);
            clientThread.start();
        }
    }
}

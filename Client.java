import java.net.*;
import java.io.*;


public class Client {
    // server host and port number, which would be acquired from command line parameter
    private static String serverHost;
    private static Integer serverPort;
    private static DataOutputStream dataOutputStream;
    private static DataInputStream dataInputStream;

    // Takes in all user input in a separate thread to the one receiving output from the server.
    // This is so no output from the server is hung up when reading user input
    public static class inputThread extends Thread {
        public inputThread() {
        }
        @Override
        public void run() {
            super.run();
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String message;
            try {
                while (true) {
                    message = reader.readLine();
                    dataOutputStream.writeUTF(message);
                    dataOutputStream.flush();
                }  
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.out.println("===== Error usage: java TCPClient SERVER_IP SERVER_PORT =====");
            return;
        }

        serverHost = args[0];
        serverPort = Integer.parseInt(args[1]);

        // define socket for client
        Socket clientSocket = new Socket(serverHost, serverPort);

        // define DataInputStream instance which would be used to receive response from the server
        // define DataOutputStream instance which would be used to send message to the server
        dataInputStream = new DataInputStream(clientSocket.getInputStream());
        dataOutputStream = new DataOutputStream(clientSocket.getOutputStream());

        inputThread input = new inputThread();
        input.start();

        // define a BufferedReader to get input from command line i.e., standard input from keyboard
        try {
            while (true) {
                String responseMessage = (String) dataInputStream.readUTF();
                // If timeout received, then close all sockets and inputs and exit the program.
                if (responseMessage.equals("timeout") || responseMessage.equals("nametaken")) {
                    System.out.println(responseMessage);
                    clientSocket.close();
                    dataInputStream.close();
                    dataOutputStream.close();
                    // Nuking the program so I don't have to deal with fiddly thread interruptions
                    System.exit(0);
                }
                System.out.println("[recv] " + responseMessage);
    
            }
        } catch (Exception e) {
            System.out.println("Connection Terminated");
            System.exit(0);
        }
       
    }
}

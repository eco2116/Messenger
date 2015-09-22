

import java.lang.*;
import java.io.*;
import java.net.*;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.locks.*;
import java.util.HashMap;
import java.util.TimerTask;
import java.util.Timer;
/**
 * Created by evanoconnor on 2/21/15.
 */
public class Client {

    private ObjectOutputStream cOOS;
    private Socket socket;

    private String address;

    private Socket peerSocket;
    private ObjectOutputStream pOOS;

    private String server;
    private static String username;
    private static User user;
    private int listenPort;
    private int port;

    private static HashMap<String, SessionDetails> allSessionDetails;

    private static Lock lock;
    private static Condition condition;

    // TODO: Can this go in the user class?
    private static int clientStatus;

    Client(String server, int port) {
        this.server = server;
        this.port = port;
        this.lock = new ReentrantLock();
        this.condition = lock.newCondition();
        this.clientStatus = User.PENDING_USERNAME;
        this.allSessionDetails = new HashMap<String, SessionDetails>();
    }


    private void connectToServer() {
        try {
            this.socket = new Socket(this.address, this.port);
            this.cOOS = new ObjectOutputStream(socket.getOutputStream());

            // Send port number and user object to server to connect
            cOOS.writeObject(new Message(Message.PORT, String.valueOf(this.listenPort)));
            Message sendUser = new Message(Message.USER, "");

            sendUser.setRecipient(this.user);
            cOOS.writeObject(sendUser);

        } catch(IOException e) {
            e.printStackTrace();
            System.out.println("connectToServer() failed with IOException");
        }
    }


    private void sendMessage(Message m) {
        try {
            cOOS.writeObject(m);

        } catch(IOException e) {
            System.out.println("Exception writing to cOOS");
        }
    }

    private void openSocket() {
        try {
            socket = new Socket(server, port);
            cOOS = new ObjectOutputStream(socket.getOutputStream());
        } catch (Exception e) {
            System.out.println("Error connecting: ");
            e.printStackTrace();
        }
        System.out.println("socket accepted");

    }

    private void closeSocket() {

        try {
            if(cOOS != null) { cOOS.close(); }
            if(socket != null) { socket.close(); }
        } catch(Exception e) {
            System.out.println("Socket close failed");
        }
    }

    private synchronized void setUpClient() {
        try {
            this.lock.lock();

            String clientUsername = "";
            // Provide username credential
            while (clientStatus == User.PENDING_USERNAME) {
                System.out.print("Username: ");
                Scanner scanner = new Scanner(System.in);
                clientUsername = scanner.next();

                // Send message containing username to server
                cOOS.writeObject(new Message(Message.USERNAME, clientUsername));
                try {
                    condition.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            // TODO: Log in from different IP doesn't initially block
            // Provide password credential
            while (clientStatus != User.LOGGED_IN) {
                System.out.print("Password: ");
                Scanner scanner = new Scanner(System.in);
                String clientPassword = scanner.next();

                // Send message containing password to server
                cOOS.writeObject(new Message(Message.PASSWORD, clientPassword));
                if(clientStatus == User.LOGIN_FAILED) {
                    System.out.println("You have been timed out\n");
                }
                try {
                    condition.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    System.out.println("InterruptedException while receiving password");
                }
            }

        } catch(IOException e) {
            e.printStackTrace();
            System.out.println("IOException in setUpClient()");
        } finally {
            lock.unlock();
        }
        // Begin heartbeat
        new ClientBeat().start();
        System.out.println("Welcome to the simple chat server!");
    }

    private void startClientInteraction() {

        ClientThread ct = new ClientThread();
        ct.start();
        this.listenPort = ct.getPort();
        connectToServer();
        setUpClient();
        closeSocket();

    }

    private void connect(int port, InetAddress address) {
        try {
            this.peerSocket = new Socket(address, port);
            this.pOOS = new ObjectOutputStream(peerSocket.getOutputStream());
            this.pOOS.flush();
        } catch(IOException e) {
            e.printStackTrace();
            System.out.println("Failure to connect to peer");
        }
    }

    private boolean performPrivateMessage(Message m) {
        SessionDetails sd;
        if ((sd = allSessionDetails.get(m.getRecipient())) != null) {
            try {
                connect(sd.getPort(), sd.getAddr());
                pOOS.writeObject(new Message(Message.MESSAGE, this.user.getUsername() + " privately says "+ m.getMsg()));
                return true;
            } catch(IOException e) {
                e.printStackTrace();
                System.out.println("IOException in performPrivateMessage");
            }
        }
        return false;

    }

    private void readCommand() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String userCmd;

        // Wait for user input and parse into message object
        try {
            while(!reader.ready()) {
                Thread.sleep(200);
            }
            userCmd = reader.readLine();
            String[] cmdSplit = userCmd.split(" ");
            Message cmdMessage = Message.parseMessage(cmdSplit);
            System.out.println("user line: " + userCmd + " cmd: " + cmdSplit[0]);
            if(cmdMessage != null) {
                cmdMessage.setSender(this.user);

                // Send the message to the server with a temporary connection
                // TODO: Private message handling goes here
                if (cmdMessage.getType() == Message.PRIVATE) {
                    if(!performPrivateMessage(cmdMessage)) {
                        System.out.println("You have to request that user's address for P2P chat");
                    }
                }
                connectToServer();
                cOOS.writeObject(cmdMessage);
                System.out.println("command sent to server..");
                closeSocket();

                // Logout if user requests to do so
                if (cmdMessage.getType() == Message.LOGOUT)
                    ClientThread.handleExit();

            } else {
                System.out.println("Not a proper command");
            }

        } catch(InterruptedException e) {
            e.printStackTrace();
            System.out.println("InterruptedException while reading command");
        } catch(IOException e) {
            e.printStackTrace();
            System.out.println("IOException while using BufferedReader");
        }
    }

    private void runChat() {

        startClientInteraction();
        while(clientStatus == User.LOGGED_IN) {
            System.out.print(">");
            readCommand();
        }
    }

    public static void main(String args[]) {
        Client c = new Client(args[0], Integer.parseInt(args[1]));
        c.runChat();
    }


    public static class ClientThread extends Thread {

        private ServerSocket serverSocket;
        private ObjectInputStream ctOIS;
        private int port;

        public ClientThread() {

            // Allow ServerSocket to handle assigning port numbers
            try {
                this.serverSocket = new ServerSocket(0);
                this.ctOIS = null;
                this.port = serverSocket.getLocalPort();
            } catch(IOException e) {
                e.printStackTrace();
                System.out.println("IOException in Client Thread");
            }
        }

        public void run() {
            try {
                while(true) {

                    // Read and parse message from the input stream
                    Socket socket = serverSocket.accept();
                    ctOIS = new ObjectInputStream(socket.getInputStream());
                    Message received = (Message) ctOIS.readObject();

                    switch(received.getType()) {
                        case Message.MESSAGE:
                            System.out.println(received.getMsg() + "\n");
                            break;

                        // Send signal and update status if client status changes
                        case Message.SESSION_STATE:
                            try {
                                lock.lock();
                                clientStatus = Integer.parseInt(received.getMsg());
                                if (clientStatus == User.NOT_LOGGED_IN) {
                                    handleExit();
                                }
                                synchronized(condition) {
                                    condition.signalAll();
                                }
                            } finally {
                                lock.unlock();
                            }
                            break;

                        // Send signal and update status if new user logs in
                        case Message.USER:
                            try {
                                lock.lock();
                                clientStatus = User.LOGGED_IN;
                                user = received.getRecipient();
                                synchronized (condition) {
                                    condition.signalAll();
                                }
                            } finally {
                                lock.unlock();
                            }
                            break;


                        case Message.TIMEOUT:
                            System.out.println("Due to multiple login failures your account has been blocked. " +
                                    "\nTry again later with correct credentials.");
                            handleExit();
                            break;


                        case Message.CONNECTION:
                            AbstractMap.SimpleEntry<String, SessionDetails> conn = received.getEntry();
                            Client.allSessionDetails.put(conn.getKey(), conn.getValue());

                    }
                }
            } catch(IOException e) {
                e.printStackTrace();
                System.out.println("IO Exception in Client Thread run");
            } catch(ClassNotFoundException e) {
                e.printStackTrace();
                System.out.println("Failed to parse message in client thread");
            }
        }

        private int getPort() {
            return this.port;
        }

        private static void handleExit() {
            System.out.println("You are being logged out...");
            System.exit(0);
        }

    }

    class ClientBeat extends Thread {
        private final int BEAT_TIME_MILLIS = 30000;

        class HeartBeat extends TimerTask {
            public void run() {
                try {

                    // Send a HeartBeat message to the server
                    Message beat = new Message(Message.BEAT, "");
                    connectToServer();
                    cOOS.writeObject(beat);
                    closeSocket();
                } catch(IOException e) {
                    e.printStackTrace();
                    System.out.println("HeartBeat failed with IOException");
                }
            }
        }
        // Schedule HeartBeats to run on a given schedule
        public void run() {
            Timer schedule = new Timer();
            schedule.schedule(new HeartBeat(), 0, BEAT_TIME_MILLIS);
        }
    }


}

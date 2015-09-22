

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.HashMap;
import java.lang.*;
import java.io.*;
import java.net.*;
import java.util.TimerTask;
import java.util.Timer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Date;

/**
 * Created by evanoconnor on 2/21/15.
 */

public class Server {

    private static int id;
    private ServerSocket serverSocket;
    private ArrayList<HandleUserThread> clientList;
    private int port;
    private boolean keepRunning;
    private ArrayList<User> users;
    private static HashMap<String, User> credentials;
    private HashMap<String, SessionDetails> allSessionDetails;
    private HashMap<User, LinkedList<Message>> msgQueueMap;
    private HashMap<String, LinkedList<String>> blockedMap;
    private HashMap<String, LinkedList<String>> privateMap;


    public Server(int port) {
        this.port = port;
        this.clientList = new ArrayList<HandleUserThread>();
        this.users = new ArrayList<User>();
        this.credentials = parseCredentials();
        this.allSessionDetails = new HashMap<String, SessionDetails>();
        this.msgQueueMap = new HashMap<User, LinkedList<Message>>();
        this.blockedMap = new HashMap<String, LinkedList<String>>();
        this.privateMap = new HashMap<String, LinkedList<String>>();

        // Begin monitoring client heartbeats
        new HeartBeatChecker().start();

        try {
            this.serverSocket = new ServerSocket(port);
        } catch(IOException e) {
            e.printStackTrace();
            System.out.println("IOException in Server constructor");
        }
    }

    // TODO: Args
    public static void main(String[] args) {
        Server server = new Server(Integer.parseInt(args[0]));
        ServerSocket ss = server.serverSocket;

        // Continuously accept sockets from client and handle users with threads
        while(true) {
            try {
                Socket clntSock = ss.accept();
                HandleUserThread ut = server.new HandleUserThread(clntSock);
                ut.start();
            } catch (IOException e) {
                System.out.println("failed on accept");
            }
        }
    }

    // Returns user object corresponding to a given username string
    public static User getUserFromUsername(String username) {
        credentials = parseCredentials();
        for(String u : credentials.keySet()) {
            if(u.equals(username)) {
                return credentials.get(u);
            }
        }
        return null;
    }

    // Checks to see if user one has blocked user two
    private boolean isBlockedPair(User one, User two) {
        LinkedList<String> pair;

        if((pair = blockedMap.get(two.getUsername())) != null) {
            if(pair.contains(one.getUsername())) {
                return true;
            }
        }
        return false;
    }


    private static HashMap<String, User> parseCredentials() {
        HashMap<String, User> credentials = new HashMap<String, User>();
        BufferedReader br = null;
        try {
            String line;
            br = new BufferedReader(new FileReader("credentials.txt"));

            // Parse credentials file into a Username -> User object HashMap
            while ((line = br.readLine()) != null) {
                String[] credential = line.split(" ");
                credentials.put(credential[0], new User(credential[0], credential[1]));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (br != null)
                    br.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return credentials;
    }

    class HandleUserThread extends Thread {

        private Socket socket;
        private Socket clientSocket;

        private ObjectInputStream uOIS;
        private ObjectOutputStream uOOS;

        private User user;
        private int port;

        private final long TIMEOUT_LENGTH_MILLIS = 60000;

        public HandleUserThread(Socket s) {

            // Initialize socket connection and output stream
            this.socket = s;
            try {
                this.uOIS = new ObjectInputStream(this.socket.getInputStream());
            } catch(IOException e) {
                e.printStackTrace();
                System.out.println("IOException creating input stream in HandleUserThread constructor");
            }
        }

        public void run() {
            try {
                // Check for port number
                Message runMsg = (Message) uOIS.readObject();
                if(runMsg.getType() == Message.PORT) {
                    this.port = Integer.parseInt(runMsg.getMsg());
                } else {
                    // Should not happen
                    System.out.println("Expected message with port number");
                }

                // Check if user is logged in and on what IP
                try {
                    Message received = (Message) uOIS.readObject();
                    System.out.println("recieved"+received.getMsg());
                    if((this.user = received.getRecipient()) == null) {
                        SessionDetails sd = new SessionDetails(this.socket.getInetAddress(), this.port);

                        // Login new user and check alert client if user has been login-blocked
                        if((this.user = loginUser(sd)) != null) {

                            credentials.put(this.user.getUsername(), this.user.updateIsLoginBlocked(TIMEOUT_LENGTH_MILLIS));

                            if(this.user.isLoginBlocked()) {
                                performLoginBlocked(sd);
                                return;
                            }
                            // Check if multiple users trying to use same credentials
                            else if(allSessionDetails.get(this.user.getUsername()) != null) {
                                handleMultipleLogins(allSessionDetails, allSessionDetails.get(this.user.getUsername()));
                                Message newUser = new Message(Message.USER, this.user.getUsername());
                                newUser.setSender(this.user);
                                send(newUser, allSessionDetails.get(this.user.getUsername()));
                            }
                            // Normal session handling
                            else {
                                performNormalSession(allSessionDetails, sd);
                                performMissedMessages();
                            }
                            // Alert login of new user
                            sendAll(new Message(Message.BROADCAST, this.user.getUsername() + " just logged on."));
                        }
                    }
                    // Add back user's session details if the user was wrongly timed out
                    else if(allSessionDetails.get(this.user.getUsername()) == null) {
                        SessionDetails sd = new SessionDetails(this.socket.getInetAddress(), this.port);
                        sd.setUser(user);
                        allSessionDetails.put(this.user.getUsername(), sd);
                    }
                } catch(ClassNotFoundException e) {
                    e.printStackTrace();
                    System.out.println("Expected message with user object");
                }
            } catch(ClassNotFoundException e) {
                e.printStackTrace();
                System.out.println("ClassNotFoundException in runUserThread()");
            } catch(IOException e) {
                e.printStackTrace();
                System.out.println("IOException encountered in runUserThread()");
            }

            // Handle various types of messages
            handleMessages();
        }

        private void performMissedMessages() {
            if((msgQueueMap.get(this.user)) == null) {
                return;
            }
            send(new Message(Message.MESSAGE, "Your offline messages: "), allSessionDetails.get(this.user.getUsername()));
            while(!msgQueueMap.get(this.user).isEmpty()) {
                Message m = msgQueueMap.get(this.user).remove();
                this.performMessage(m);
            }
        }

        private void handleMessages() {
            try {
                System.out.println("handling messages for ");
                // Perform appropriate functionality based on the message type

                Message messageToHandle = (Message) uOIS.readObject();
                System.out.println("MESSAGE: " + messageToHandle.getType() + " " +messageToHandle.getMsg());
                // TODO: Did I already do this in the client??
                messageToHandle.setSender(this.user);

                switch (messageToHandle.getType()) {

                    case Message.MESSAGE:
                        performMessage(messageToHandle);
                        break;

                    case Message.BROADCAST:
                        sendAll(messageToHandle);
                        break;

                    case Message.ONLINE:
                        performOnline();
                        break;

                    case Message.BLOCK:
                        performBlock(messageToHandle);
                        break;

                    case Message.UNBLOCK:
                        performUnblock(messageToHandle);
                        break;

                    case Message.LOGOUT:
                        performLogout(messageToHandle);
                        break;

                    case Message.ADDRESS:
                        performGetAddress(messageToHandle);
                        break;

                    case Message.BEAT:
                        performBeat();
                        break;

                    case Message.PRIVATE:
                        performPrivate(messageToHandle);
                        break;

                    case Message.REQUEST_START:
                        performTalkRequest(messageToHandle);
                        break;

                    case Message.REQUEST_CLOSE:
                        performTalkRevoke(messageToHandle);
                        break;


                }
            } catch(EOFException e) {
                closeInStreamAndSocket();

            } catch(IOException e) {
                e.printStackTrace();
                System.out.println("Encountered IOException in handleMessages()");
            }
            catch(ClassNotFoundException e) {
                e.printStackTrace();
                System.out.println("Unable to parse message in handleMessages()");
            }
        }

        private boolean isConnected(User one, User two) {

            // Checks if pairs are linked to make private connections
            LinkedList<String> pairs;
            if((pairs = privateMap.get(two.getUsername())) != null) {
                if(pairs.contains(one.getUsername())) {
                    return true;
                }
            }
            return false;
        }

        private void performPrivate(Message m) {

            // Send a private message; assumes the users have initiated contact and obtained address
            User usr;
            SessionDetails sd;
            if((usr = Server.getUserFromUsername(m.getRecipient().getUsername())) != null) {
                if(!isBlockedPair(this.user, usr)) {
                    if(isConnected(this.user, usr)) {
                        if((sd = allSessionDetails.get(usr.getUsername())) != null) {
                            AbstractMap.SimpleEntry<String, SessionDetails> conn =
                                    new AbstractMap.SimpleEntry<String, SessionDetails>(usr.getUsername(), sd);
                            Message conMsg = new Message(Message.CONNECTION, "");
                            conMsg.setEntry(conn);
                            send(conMsg, allSessionDetails.get(this.user.getUsername()));
                        } else {
                            send(new Message(Message.MESSAGE, "That user is not online"), allSessionDetails.get(this.user.getUsername()));
                        }
                    } else {
                        send(new Message(Message.MESSAGE, "Waiting for connection..."), allSessionDetails.get(this.user.getUsername()));
                        send(new Message(Message.MESSAGE, "Please request the user to connect: "+ this.user.getUsername()),
                                allSessionDetails.get(usr.getUsername()));
                    }
                } else {
                    send(new Message(Message.MESSAGE, "You can't contact that user"), allSessionDetails.get(this.user.getUsername()));
                }
            }
        }

        private void performBeat() {
            SessionDetails sd = allSessionDetails.get(this.user.getUsername());
            sd.updateLastBeat();
        }

        private void performMessage(Message m) {
            User usr;
            String text;
            SessionDetails sd;
            LinkedList<Message> offline;

            if((usr = Server.getUserFromUsername(m.getRecipient().getUsername())) != null) {
                if(!isBlockedPair(this.user, usr)) {

                    // Format message and send to recipient
                    text = m.getSender().getUsername() + " says: " + m.getMsg();

                    // Online message
                    if((sd = allSessionDetails.get(usr.getUsername())) != null)

                        send(new Message(Message.MESSAGE, text), sd);


                    // Offline Message
                    else {
                        send(new Message(Message.MESSAGE, usr.getUsername() + " is offline. " +
                                "Storing as offline message to be sent when user logs in"),
                                allSessionDetails.get(this.user.getUsername()));
                        if((offline = msgQueueMap.get(usr)) == null) {
                            offline = new LinkedList<Message>();
                            offline.add(m);
                            msgQueueMap.put(usr, offline);
                        } else
                            offline.add(m);
                    }
                } else
                    send(new Message(Message.MESSAGE, "You cannot contact that user at this time."),
                            allSessionDetails.get(this.user.getUsername()));
            }
        }

        private void performTalkRequest(Message m) {

            // Request to privately chat with a user
            User usr;
            LinkedList<String> accepted;
            SessionDetails sd;
            if((usr = Server.getUserFromUsername(m.getRecipient().getUsername())) != null) {
                if((sd = allSessionDetails.get(this.user.getUsername())) != null) {
                    AbstractMap.SimpleEntry<String, SessionDetails> requestPair =
                            new AbstractMap.SimpleEntry<String, SessionDetails>(this.user.getUsername(), sd);
                    Message conMsg = new Message(Message.CONNECTION, "");
                    conMsg.setEntry(requestPair);
                    send(conMsg, allSessionDetails.get(usr.getUsername()));
                    send(new Message(Message.MESSAGE, "Private chat initiated with"+this.user.getUsername()),
                            allSessionDetails.get(usr.getUsername()));
                } else {
                    send(new Message(Message.MESSAGE, "The user you requested to private chat with is offline"),
                            allSessionDetails.get(this.user.getUsername()));
                }
                if((accepted = privateMap.get(this.user.getUsername())) != null) {
                    accepted.add(usr.getUsername());
                } else {
                    accepted = new LinkedList<String>();
                    accepted.add(usr.getUsername());
                    privateMap.put(this.user.getUsername(), accepted);
                }
            }
        }

        private void performTalkRevoke(Message m) {

            // Revoke the request to privately chat with a user
            User usr;
            LinkedList<String> accepted;
            m.setSender(Server.getUserFromUsername(this.user.getUsername()));
            if((usr = Server.getUserFromUsername(m.getRecipient().getUsername())) != null) {
                if((accepted = privateMap.get(this.user.getUsername())) != null) {
                    accepted.remove(usr.getUsername());
                    send(m, allSessionDetails.get(usr.getUsername()));
                    send(new Message(Message.MESSAGE, "Private chat request has been ended with "+this.user.getUsername()),
                            allSessionDetails.get(usr.getUsername()));
                }
            }
        }


        // Send to client the names of online users not including self
        private void performOnline() {
            send(new Message(Message.MESSAGE, "Here are the online users..."),
                    allSessionDetails.get(this.user.getUsername()));
            for(SessionDetails sd : allSessionDetails.values()) {
                if(!sd.getUser().equals(allSessionDetails.get(this.user.getUsername())))
                    send(new Message(Message.MESSAGE, sd.getUser().getUsername()),
                            allSessionDetails.get(this.user.getUsername()));
            }
        }

        // Block a user from contacting this.user
        private void performBlock(Message m) {
            User usr;
            LinkedList<String> pair;
            if((usr = Server.getUserFromUsername(m.getRecipient().getUsername())) != null) {
                if((pair = blockedMap.get(this.user.getUsername())) != null) {
                    pair.add(usr.getUsername());
                } else {
                    pair = new LinkedList<String>();
                    pair.add(usr.getUsername());
                    blockedMap.put(this.user.getUsername(), pair);
                }
            }
        }

        // Unblock a user from contacting this.user
        private void performUnblock(Message m) {
            User usr;
            LinkedList<String> pair;
            if((usr = Server.getUserFromUsername(m.getRecipient().getUsername())) != null) {
                if((pair = blockedMap.get(this.user.getUsername())) != null) {
                    pair.remove(usr.getUsername());
                }
            }
        }

        // Log the user out on the server side
        private void performLogout(Message m) {
            allSessionDetails.remove(this.user.getUsername());
            closeInStreamAndSocket();
        }

        private void performGetAddress(Message m) {
            User usr;

            // Send user message to initiate P2P chat
            if((usr = Server.getUserFromUsername(m.getRecipient().getUsername())) != null) {
                if(!isBlockedPair(this.user, usr)) {
                    SessionDetails sd;
                    if((sd = allSessionDetails.get(usr.getUsername())) != null) {
                        AbstractMap.SimpleEntry<String, SessionDetails> connection =
                                new AbstractMap.SimpleEntry<String, SessionDetails>(usr.getUsername(), sd);

                        Message conMsg = new Message(16, "con");
                        conMsg.setEntry(connection);
                        send(conMsg, allSessionDetails.get(this.user.getUsername()));
                    } else {
                        send(new Message(Message.MESSAGE, "That user is not online."),
                                allSessionDetails.get(this.user.getUsername()));
                    }
                } else {
                    send(new Message(Message.MESSAGE, "Not permitted to contact that user."),
                            allSessionDetails.get(this.user.getUsername()));
                }
            }
        }

        private void performNormalSession(HashMap<String, SessionDetails> allSessions, SessionDetails userSD) {
            userSD.setUser(this.user);
            allSessions.put(this.user.getUsername(), userSD);
            Message userMsg = new Message(Message.USER, "");

            // TODO: Are the values getting changed or do I need to return something?
            userMsg.setRecipient(this.user);
            send(userMsg, userSD);


        }

        private void handleMultipleLogins(HashMap<String, SessionDetails> allSessions, SessionDetails userSD) {

            // Log out the user and remove their SessionDetails
            send(new Message(Message.MESSAGE, "Multiple users logged on with your credentials"), userSD);
            allSessions.remove(userSD.getUser());

            // TODO: is this necessary?
            userSD.setUser(this.user);
            allSessions.put(this.user.getUsername(), userSD);

            send(new Message(Message.SESSION_STATE, String.valueOf(User.NOT_LOGGED_IN)), userSD);
        }

        private void performLoginBlocked(SessionDetails userSD) {
            // Alert client that login has failed
            send(new Message(Message.MESSAGE, "Blocked"), userSD);
            send(new Message(Message.SESSION_STATE, String.valueOf(User.LOGIN_FAILED)), userSD);
        }

        private void closeInStreamAndSocket() {
            // Attempt to close output stream and corresponding socket
            try {
                if(this.uOIS != null) this.uOIS.close();
                if(this.socket != null) this.socket.close();
            } catch(Exception e) {
                System.out.println("Socket close failed");
            }

        }

        private void closeOutStreamAndSocket() {

            // Attempt to close output stream and corresponding socket
            try {
                if(this.uOOS != null) this.uOOS.close();
                if(this.clientSocket != null) this.clientSocket.close();
            } catch(Exception e) {
                System.out.println("Socket close failed");
            }

        }

        private void connect(int clntPort, InetAddress clntAddr) {

            // Create client socket connection and output stream from given address/port
            try {
                this.clientSocket = new Socket(clntAddr, clntPort);
                this.uOOS = new ObjectOutputStream(this.clientSocket.getOutputStream());
                this.uOOS.flush();
            } catch(IOException e) {
                e.printStackTrace();
                System.out.println("IOException while connecting in HandleUserThread");
            }
        }

        private User loginUser(SessionDetails sessionDetails) {
            try {

                String clientUsername;
                // Wait for valid username from client
                while (true) {

                    Message uMsg = (Message) uOIS.readObject();
                    if(uMsg.getType() != Message.USERNAME) {
                        return null;
                    }
                    clientUsername = uMsg.getMsg();

                    if (isValidUsername(clientUsername)) {
                        send(new Message(Message.SESSION_STATE, String.valueOf(User.PENDING_PASSWORD)), sessionDetails);
                        break;
                    } else {
                        send(new Message(Message.MESSAGE, "Not a valid username. Try again."), sessionDetails);
                        send(new Message(Message.SESSION_STATE, String.valueOf(User.PENDING_USERNAME)), sessionDetails);
                    }
                }

                // Wait for valid password, temporarily blocking user if necessary
                int remainingAttempts = 3;
                while (this.user == null) {

                    String clientPassword = ((Message) uOIS.readObject()).getMsg();

                    User loginUser = checkUserCredentials(clientUsername, clientPassword);


                    if (isUserTimedOut(clientUsername, credentials)) {

                        send(new Message(Message.TIMEOUT, "timeout"), sessionDetails);
                        return null;
                    }

                    if (loginUser != null) {
                        send(new Message(Message.SESSION_STATE, String.valueOf(User.LOGGED_IN)), sessionDetails);
                        return loginUser;
                    }


                    if (--remainingAttempts == 0) {

                        User blockedUser = credentials.get(clientUsername);
                        blockedUser.setTimeBlocked(System.currentTimeMillis());
                        credentials.remove(clientUsername);
                        credentials.put(clientUsername, blockedUser);
                        send(new Message(Message.TIMEOUT, "timeout"), sessionDetails);
                        remainingAttempts = 3;
                    } else {

                        send(new Message(Message.SESSION_STATE, String.valueOf(User.PENDING_PASSWORD)), sessionDetails);
                    }
                }

            } catch(EOFException e) {
                System.out.println("socket has been closed - EOF");
            } catch(ClassNotFoundException e) {
                e.printStackTrace();
                System.out.println("Failed to parse as message in loginUser from HandleUserThread");
            } catch(IOException e) {
                e.printStackTrace();
                System.out.println("IOException in loginUser from HandleUserThread");
            }

            return null;
        }

        private boolean isUserTimedOut(String username, HashMap<String, User> credentials) {
            for(User check : credentials.values()) {
                if(check.getUsername().equals(username)) {
                    credentials.put(username, check.updateIsLoginBlocked(TIMEOUT_LENGTH_MILLIS));
                }
            }
            if(credentials.get(username).isLoginBlocked()) {
                return true;
            } else {
                return false;
            }
        }

        private User checkUserCredentials(String checkUsername, String checkPassword) {
            for(User u : credentials.values()) {
                if(u.getUsername().equals(checkUsername) && u.getPassword().equals(checkPassword))
                    return u;
            }
            return null;
        }

        private void send(Message m, SessionDetails s) {
            try {
                // Connect to client, send message, and close the connection
                connect(s.getPort(), s.getAddr());
                uOOS.writeObject(m);
                closeOutStreamAndSocket();
                if(s.getUser() != null)
                    System.out.println("SENT for user: "+ s.getUser().getUsername());
            } catch(IOException e) {
                e.printStackTrace();
                System.out.println("IOException encountered while sending message from server to client");
            }
        }

        private void sendAll(Message message) {

            for(SessionDetails details : allSessionDetails.values()) {

                // Ensure that the pair is not blocked and not sending to self
                if(!this.user.getUsername().equals(details.getUser().getUsername())
                        && !isBlockedPair(this.user, details.getUser())) {

                    // Format message based on whether sender is null or a user
                    String messageText = message.getSender() == null ? message.getMsg() :
                            (message.getSender().getUsername() + " says " + message.getMsg());
                    send(new Message(Message.MESSAGE, messageText), details);
                }

            }
        }

        private boolean isValidUsername(String checkUsername) {
            // Run through credentials and see if the username is valid
            for(String username : credentials.keySet()) {
                if(username.equals(checkUsername))
                    return true;
            }
            return false;
        }
    }

    class HeartBeatChecker extends Thread {
        private final int CHECK_TIME_MILLIS = 35000;

        class MonitorBeats extends TimerTask {
            public void run() {
                for(SessionDetails sd : allSessionDetails.values()) {
                    long delta = new Date().getTime() - sd.getLastBeat().getTime();
                    if(delta >= CHECK_TIME_MILLIS) {
                        User usr = Server.getUserFromUsername(sd.getUser().getUsername());
                        System.out.println("Timing out "+ usr.getUsername()+ " due to no heartbeat.");
                        allSessionDetails.remove(usr.getUsername());
                    }
                }
            }
        }
        public void run() {
            Timer schedule = new Timer();
            schedule.schedule(new MonitorBeats(), 0, CHECK_TIME_MILLIS);
        }
    }
}



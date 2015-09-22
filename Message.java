

import java.io.*;
import java.util.AbstractMap;

/**
 * Created by evanoconnor on 2/22/15.
 */
public class Message implements Serializable  {

    protected static final long serialVersionUID = 1112122200L;

    public static final int ONLINE = 0, MESSAGE = 1, LOGOUT = 2, BROADCAST = 3, BLOCK = 4, UNBLOCK = 5,
            ADDRESS = 6, PRIVATE = 7, USERNAME = 8, PASSWORD = 9, BEAT = 10, INIT = 11, SESSION_STATE = 12, PORT = 13,
            USER = 14, TIMEOUT = 15, CONNECTION = 16, REQUEST_START = 17, REQUEST_CLOSE = 18;
    private int type;
    private String msg;
    private User recipient;
    private User sender;
    private AbstractMap.SimpleEntry<String, SessionDetails> connection;

    public Message() {
        this.msg = null;
        this.recipient = null;
    }

    public Message(int type, String msg) {
        this.type = type;
        this.msg = msg;
        this.recipient = null;
    }

    public static Message parseMessage(String[] cmdArgs) {

        // Parse user's request arguments into a message object of a given type
        Message parsed = new Message();
        String text = "";

        // Set recipient, message text
        if(cmdArgs[0].equalsIgnoreCase("message")) {
            parsed.setType(Message.MESSAGE);
            parsed.setRecipient(Server.getUserFromUsername(cmdArgs[1]));
            for(int i=2; i<cmdArgs.length; i++) {
                text += cmdArgs[i] + " ";
            }
            parsed.setMsg(text);
        }

        // Set message text, recipients are implied by type (all online that aren't blocked)
        else if(cmdArgs[0].equalsIgnoreCase("broadcast")) {
            parsed.setType(Message.BROADCAST);
            for(int i=1; i<cmdArgs.length; i++) {
                text += cmdArgs[i] + " ";
            }
            parsed.setMsg(text);
        }

        // Message to notify user of all online users
        else if(cmdArgs[0].equalsIgnoreCase("online")) {
            parsed.setType(Message.ONLINE);
        }

        // Block a specific user
        else if(cmdArgs[0].equalsIgnoreCase("block")) {
            parsed.setType(Message.BLOCK);
            parsed.setRecipient(Server.getUserFromUsername(cmdArgs[1]));
        }

        // Unblock a specific user
        else if(cmdArgs[0].equalsIgnoreCase("unblock")) {
            parsed.setType(Message.UNBLOCK);
            parsed.setRecipient(Server.getUserFromUsername(cmdArgs[1]));
        }

        // Log the user out of the chat service
        else if(cmdArgs[0].equalsIgnoreCase("logout")) {
            parsed.setType(Message.LOGOUT);
        }

        // Get the address to attempt to send a private P2P message
        else if(cmdArgs[0].equalsIgnoreCase("getaddress")) {
            parsed.setType(Message.ADDRESS);
            parsed.setRecipient(Server.getUserFromUsername(cmdArgs[1]));
        }

        else if(cmdArgs[0].equalsIgnoreCase("request")) {
            parsed.setType(Message.REQUEST_START);
            parsed.setRecipient(Server.getUserFromUsername(cmdArgs[1]));
        }

        else if(cmdArgs[0].equalsIgnoreCase("endrequest")) {
            parsed.setType(Message.REQUEST_CLOSE);
            parsed.setRecipient(Server.getUserFromUsername(cmdArgs[1]));
        }

        // Send private P2P message (assuming user is online and address known)
        else if(cmdArgs[0].equalsIgnoreCase("private")) {
            parsed.setType(Message.PRIVATE);
            parsed.setRecipient(Server.getUserFromUsername(cmdArgs[1]));
            for(int i=2; i<cmdArgs.length; i++) {
                text += cmdArgs[i] + " ";
            }
            parsed.setMsg(text);
        }
        // If proper command, return the new Message object, otherwise return null
        else {
            return null;
        }
        return parsed;
    }

    public int getType() { return this.type; }

    public void setType(int t) { this.type = t; }

    public void setMsg(String m) { this.msg = m; }

    public String getMsg() {
        return this.msg;
    }

    public void setRecipient(User u) { this.recipient = u; }

    public User getRecipient() { return this.recipient; }

    public void setSender(User u) { this.sender = u; }

    public User getSender() { return this.sender; }

    public AbstractMap.SimpleEntry<String, SessionDetails> getEntry() { return this.connection; }

    public void setEntry(AbstractMap.SimpleEntry<String, SessionDetails> e) { this.connection = e; }

    @Override public String toString() {
        return msg + "-- of type " + type;
    }

}

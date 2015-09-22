

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Created by evanoconnor on 2/21/15.
 */
public class User implements Serializable {

    protected static final long serialVersionUID = 1112122200L;

    public static final int LOGGED_IN = 0, NOT_LOGGED_IN = 1, LOGIN_FAILED = 2, PENDING_USERNAME = 3,
            PENDING_PASSWORD = 4;

    private int status;

    // TODO: Possibly make final
    private String username;
    private String password;

    private long timeBlocked;

    private Boolean isLoginBlocked;
    private Boolean isLoggedIn;
    private ArrayList<User> blockedUsers;

    public User(String u, String p) {
        this.isLoginBlocked = false;
        this.isLoggedIn = false;
        this.username = u;
        this.password = p;
        this.timeBlocked = 0;
        this.blockedUsers = new ArrayList<User>();
        this.status = User.NOT_LOGGED_IN;
    }

    public int getStatus() { return this.status; }

    public String getUsername() {
        return this.username;
    }

    public String getPassword() {
        return this.password;
    }

    public boolean isLoginBlocked() { return this.isLoginBlocked; }

    public void setTimeBlocked(long timeBlocked) { this.timeBlocked = timeBlocked; }

    public User updateIsLoginBlocked(long timeout) {
        if((System.currentTimeMillis() - this.timeBlocked) > timeout) {
            this.isLoginBlocked = false;
        } else {
            this.isLoginBlocked = true;
        }
        return this;
    }

}

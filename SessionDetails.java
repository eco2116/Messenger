

import java.io.Serializable;
import java.net.InetAddress;
import java.util.Date;
/**
 * Created by evanoconnor on 3/15/15.
 */
public class SessionDetails implements Serializable {

    protected static final long serialVersionUID = 1112122200L;

    private Date lastBeat;
    private User user;
    private InetAddress addr;
    private int port;

    public SessionDetails(InetAddress ip, int p) {
        this.addr = ip;
        this.port = p;
        this.user = null;
        this.lastBeat = new Date();
    }

    public Date getLastBeat() { return this.lastBeat; }

    public void updateLastBeat() { this.lastBeat = new Date(); }

    public User getUser() {
        return this.user;
    }

    public void setUser(User u) {
        this.user = u;
    }

    public InetAddress getAddr() {
        return this.addr;
    }

    public void setAddr(InetAddress a) {
        this.addr = a;
    }

    public int getPort() {
        return this.port;
    }

    public void setPort(int p) {
        this.port = p;
    }


}

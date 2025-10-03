import java.io.Serializable;
import java.util.Date;

public class Message implements Serializable {
    // Types: "CHAT", "CONNECT_REQUEST", "CONNECT_ACCEPT", "CONNECT_DENY",
    //        "SYSTEM", "SYSTEM_LIST", "SYSTEM_ERROR"
    private String type;
    private String text;
    private String from;   // clientId or "Server"
    private String to;     // clientId or "Server"
    private Date created;

    public Message(String type, String text, String from, String to) {
        this.type = type;
        this.text = text;
        this.from = from;
        this.to = to;
        this.created = new Date();
    }

    public String getType() { return type; }
    public String getText() { return text; }
    public String getFrom() { return from; }
    public String getTo() { return to; }
    public Date getCreated() { return created; }

    @Override
    public String toString() {
        return "[" + type + "][" + created + "] " + from + " -> " + to + ": " + text;
    }
}

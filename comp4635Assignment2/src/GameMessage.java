
import java.awt.TrayIcon.MessageType;
import java.io.Serializable;

public class GameMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int senderId;
    private final String senderName;
    private final String content;    // e.g. description of action or guess result
    private final int lamportTimestamp;
    private final LamportMessageHandler.MessageType type;  // optional: enum for type of message (GUESS, JOIN, etc.)

    public GameMessage(int senderId, String senderName, String content, int timestamp, LamportMessageHandler.MessageType gameEvent) {
        this.senderId = senderId;
        this.senderName = senderName;
        this.content = content;
        this.lamportTimestamp = timestamp;
        this.type = gameEvent;
    }

    public int getSenderId() { return senderId; }
    public String getSenderName() { return senderName; }
    public String getContent() { return content; }
    public int getLamportTimestamp() { return lamportTimestamp; }
    public LamportMessageHandler.MessageType getType() { return type; }
    
    @Override
    public String toString() {
        return "GameMessage [sender=" + senderName + ", content=" + content +
               ", timestamp=" + lamportTimestamp + ", type=" + type + "]";
    }
}

import mcgui.*;

/**
 * Message implementation for ExampleCaster.
 *
 * @author Tomas Hasselquist (tomasha@student.chalmers.se) & David Gardtman (gardtman@student.chalmers.se)
 */
public class McuiMessage extends Message {
        
    private String text;
    private int originalSender;
    private int localSeq;
    private int globalSeq;

    /**
     * Initial constructor to create an initial message with a local sequence number
     * @param sender  The sender that created the message
     * @param localSeq  The local sequence number connected to the message
     * @param text  The text of the message
     */
    public McuiMessage(int sender, int localSeq, String text) {
        super(sender);
        originalSender = sender;
        this.text = text;
        this.localSeq = localSeq;
        globalSeq = -1;
    }

    /**
     * Constructor used to clone another message, adding new sender and a globalSeq.
     * This is only done by the sequencer to send out the message with a global sequence number
     * @param msg  The old message that needs to be copied
     * @param sender  The sender that created the new message
     * @param globalSeq  The global sequence number connected to the message
     */
    public McuiMessage(McuiMessage msg, int sender, int globalSeq) {
        super(sender);
        text = msg.getText();
        originalSender = msg.getOriginalSender();
        localSeq = msg.getLocalSeq();
        this.globalSeq = globalSeq;
    }

    /**
     * Constructor used to clone another message, adding new sender and a globalSeq.
     * This is only done by nodes reBroadcasting messages received from sequencer
     * @param msg  The old message that needs to be copied
     * @param sender  The sender that created the new message
     */
    public McuiMessage(McuiMessage msg, int sender) {
        super(sender);
        text = msg.getText();
        originalSender = msg.getOriginalSender();
        localSeq = msg.getLocalSeq();
        globalSeq = msg.getGlobalSeq();
    }
    
    /**
     * Returns the text of the message only. The toString method can
     * be implemented to show additional things useful for debugging
     * purposes.
     */
    public String getText() {
        return text;
    }

    /**
     * Returns the original creator of the message
     */
    public int getOriginalSender() {
        return originalSender;
    }

    /**
     * Returns the local sequence number
     */
    public int getLocalSeq() {
        return localSeq;
    }

    /**
     * Returns the global sequence number
     */
    public int getGlobalSeq() {
        return globalSeq;
    }
    
    public static final long serialVersionUID = 0;

    @Override
    public boolean equals(Object other){
        if (other == null) return false;
        if (other == this) return true;
        if (!(other instanceof McuiMessage)) return false;
        McuiMessage otherMcuiMessage = (McuiMessage)other;
        if (!text.equals(otherMcuiMessage.getText())) return false;
        if (originalSender != otherMcuiMessage.getOriginalSender()) return false;
        if (localSeq != otherMcuiMessage.getLocalSeq()) return false;
        return true;
    }
}


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

    public int getOriginalSender() {
        return originalSender;
    }

    public int getLocalSeq() {
        return localSeq;
    }

    public int getGlobalSeq() {
        return globalSeq;
    }

    public void setGlobalSeq(int seq) {
        globalSeq = seq;
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

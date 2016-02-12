
import mcgui.*;

/**
 * Message implementation for ExampleCaster.
 *
 * @author Tomas Hasselquist (tomasha@student.chalmers.se) & David Gardtman ()
 */
public class McuiMessage extends Message {
        
    private String text;
    private int seq;

        
    public McuiMessage(int sender, int seq, String text) {
        super(sender);
        this.text = text;
        this.seq = seq;
    }
    
    /**
     * Returns the text of the message only. The toString method can
     * be implemented to show additional things useful for debugging
     * purposes.
     */
    public String getText() {
        return text;
    }

    public int getSeq() {
        return seq;
    }
    
    public static final long serialVersionUID = 0;
}

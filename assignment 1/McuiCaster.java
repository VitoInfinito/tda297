
import mcgui.*;
import java.util.ArrayList;

/**
 * Simple example of how to use the Multicaster interface.
 *
 * @author Tomas Hasselquist (tomasha@student.chalmers.se) & David Gardtman ()
 */
public class McuiCaster extends Multicaster {
    private int seq;
    private int expectedSeq;
    private ArrayList<McuiMessage> msgBag;
    //private int[] next;
    private int sequencerID = 0;

    /**
     * No initializations needed for this simple one
     */
    public void init() {
        //Initial sequence confirmation (Will be remade later to handle dynamic selection)
        if (id == sequencerID) {
            mcui.debug("I am the sequencer");
        }


        seq = 0;
        msgBag = new ArrayList<McuiMessage>();
        /*next = new int[hosts];
        for(int i=0; i<hosts; i++) {
            next[i] = 1;
        }*/
        expectedSeq = 1;
        mcui.debug("The network has " + hosts + " hosts!");
    }
        
    /**
     * The GUI calls this module to multicast a message
     */
    public void cast(String messagetext) {
        McuiMessage msg = new McuiMessage(id, ++seq, messagetext);
        for(int i=0; i < hosts; i++) {
            /* Sends to everyone except itself */
            if(i != id) {
                bcom.basicsend(i, msg);
            }
        }
        mcui.debug("Sent out: \"" + messagetext + "\"");
        mcui.deliver(id, messagetext, "from myself!");
    }
    
    /**
     * Receive a basic message
     * @param message  The message received
     */
    public void basicreceive(int peer, Message message) {

        mcui.deliver(peer, ((McuiMessage)message).getText());
        
    }

    /**
     * Sending the message to the neighbour that is not the orignal sender nor itself
     * Used to resend messages in case some node crashed in the middle of earlier transmit.
     * @param message The message received
     */
    public void redeliver(Message message) {

    }

    /**
     * Signals that a peer is down and has been down for a while to
     * allow for messages taking different paths from this peer to
     * arrive.
     * @param peer	The dead peer
     */
    public void basicpeerdown(int peer) {
        mcui.debug("Peer " + peer + " has been dead for a while now!");
    }
}


import mcgui.*;
import java.util.ArrayList;

/**
 * Simple example of how to use the Multicaster interface.
 *
 * @author Tomas Hasselquist (tomasha@student.chalmers.se) & David Gardtman (gardtman@student.chalmers.se)
 */
public class McuiCaster extends Multicaster {
    private int seq;
    private int expectedSeq;
    private ArrayList<McuiMessage> msgBag;
    private ArrayList<McuiMessage> delivered;
    private ArrayList<McuiMessage> notDelievered;
    private int[] next;
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
        delivered = new ArrayList<McuiMessage>();
        notDelievered = new ArrayList<McuiMessage>();
        next = new int[hosts];
        for(int i=0; i<hosts; i++) {
            next[i] = 1;
        }
        expectedSeq = 1;
        mcui.debug("The network has " + hosts + " hosts!");
    }
        
    /**
     * The GUI calls this module to multicast a message
     */
    public void cast(String messagetext) {
        // Creating message with its own id as sender and its own expected 
        // next local sequence number as local sequence number to message
        McuiMessage msg = new McuiMessage(id, next[id]++, messagetext);
        /*for(int i=0; i < hosts; i++) {
            if(i != id) {
                bcom.basicsend(i, msg);
            }
        }*/
        // Send message to sequencer first
        bcom.basicsend(sequencerID, msg);
        mcui.debug("Sent out: \"" + messagetext + "\"");
        mcui.deliver(id, messagetext, "from myself!");
    }
    
    /**
     * Receive a basic message
     * @param message  The message received
     */
    public void basicreceive(int peer, Message message) {
        // If I am sequencer and I did not send the message
        if (id == sequencerID && id != message.getSender()) {
            sequencerBroadcast((McuiMessage) message);
        } else {
            deliver((McuiMessage) message;
        }        
    }

    /**
     * Used by sequencer to add global sequence number and then broadcast the message
     */
    private void sequencerBroadcast(McuiMessage message) {
        McuiMessage globalMsg = new McuiMessage(message, id, ++seq);
        for (int i=0; i<hosts; i++) {
            bcom.basicsend(i, globalMsg);
        }
    }

    /**
     * Sending the message to the neighbour that is not the orignal sender nor itself
     * Used to resend messages in case some node crashed in the middle of earlier transmit.
     * Also used to retransmit message first time it was received
     * @param message The message received
     */
    private void reBroadcast(Message message) {
        McuiMessage globalMsg = new McuiMessage((McuiMessage) message, id);
        for (int i=0; i<hosts; i++) {
            //Rework to this (somewhat)
            /*
                // if i am not sender of m
                if (message.getSender() != id) {
                    for (int i = 0; i < hosts; i++) {
                        bcom.basicsend(i, message);
                    }
                }
            */
            if (i != id && i != message.getSender()) {
                bcom.basicsend(i, globalMsg);
            }
        }
    }

    private void deliver(McuiMessage msg) {
        if (!delivered.contains(msg)) {
            

            checkBagsAndDeliver(msg);
        }
        


        
    }

    private void checkBagsAndDeliver(McuiMessage msg) {
        msgBag.add(msg);

        for (McuiMessage msgInBag: msgBag) {
            if (msgInBag.getGlobalSeq() == expectedSeq) {
                mcui.deliver(msg.getOriginalSender(), msg.getText());
                // Incremementing expected local and global sequence numbers
                next[msg.getOriginalSender]++;
                expectedSeq++;
                // Adding message to delievered list, removing from msgBag and non-delivered bag
                delivered.add(msgInBag);
                msgBag.remove(msgInBag);
                notDelievered.remove(msgInBag);
            } else if (msgInBag.getGlobalSeq() < expectedSeq) {
                // If message already delivered earlier, remove it from msgBag
                msgBag.remove(msgInBag);
            }
        }
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

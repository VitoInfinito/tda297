
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
    //private ArrayList<McuiMessage> noGlobalSequence;
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
        //noGlobalSequence = new ArrayList<McuiMessage>();
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
        notDelievered.add(msg);
        /*for(int i=0; i < hosts; i++) {
            if(i != id) {
                bcom.basicsend(i, msg);
            }
        }*/
        // Send message to sequencer first
        bcom.basicsend(sequencerID, msg);
        //mcui.debug("Sent out: \"" + messagetext + "\"");
        //mcui.deliver(id, messagetext, "from myself!");
    }
    
    /**
     * Receive a basic message
     * @param message  The message received
     */
    public void basicreceive(int peer, Message message) {
        //mcui.debug("received");
        // If I am sequencer and there is no global sequence number in message
        if (id == sequencerID && ((McuiMessage)message).getGlobalSeq() == -1) {
            sequencerBroadcast((McuiMessage) message);
        } else {
            deliver((McuiMessage) message);
        }        
    }

    /**
     * Used by sequencer to add global sequence number and then broadcast the message
     * Then send message to everyone, including itself
     */
    private void sequencerBroadcast(McuiMessage message) {
        //mcui.debug("S-broad");
        //if (next[message.getOriginalSender()] == message.getLocalSeq()) {
            McuiMessage globalMsg = new McuiMessage(message, id, ++seq);
            for (int i=0; i<hosts; i++) {
                bcom.basicsend(i, globalMsg);
            }
        //} else {

        //}   
    }

    /**
     * Sending the message to the neighbour that is not the orignal sender nor itself
     * Used to resend messages in case some node crashed in the middle of earlier transmit.
     * Also used to retransmit message first time it was received
     * @param message The message received
     */
    private void reBroadcast(McuiMessage msg) {
        //mcui.debug("RE-broad");
        McuiMessage globalMsg = new McuiMessage(msg, id);
        if (id != msg.getSender()) {
            for (int i=0; i<hosts; i++) {
                if (i != id && i != msg.getSender() && i != sequencerID) {
                    bcom.basicsend(i, globalMsg);
                }
            }
        }
    }

    /**
     * Delivers message if its not already delivered
     */
    private void deliver(McuiMessage msg) {
        //mcui.debug("deliver");
        if (!isDelivered(msg)) {
            bagDelivery(msg);
            reBroadcast(msg);
        }
    }

    /**
     * Used as a helper function for deliver. Goes through bag to see if any message is the
     * next one to be delievered
     */
    private void bagDelivery(McuiMessage msg) {
        int sender = msg.getSender();
        msgBag.add(msg);
        int i = 0;
        //mcui.debug("Pre-stuck?: ");
        while (i < msgBag.size()) {
            McuiMessage msgInBag = msgBag.get(i);
            //mcui.debug("Stuck?: " + msgInBag.getGlobalSeq() + " " + expectedSeq);
            i++;
            if (msgInBag.getGlobalSeq() == expectedSeq) {
                //mcui.debug("Found expected");
                mcui.deliver(msg.getOriginalSender(), msg.getText());
                expectedSeq++;

                delivered.add(msgInBag);
                msgBag.remove(msgInBag);
                //Remake removal of nondelivered to handle only local sequence number
                notDelievered.remove(msgInBag);

                // Since message was delivered, go through entire bag again in search for next expected
                i = 0;
            }/* else if (msgInBag.getGlobalSeq() < expectedSeq) {
                msgBag.remove(msgInBag);
            }*/
        }
    }

    /**
     * Checks if message has been delivered
     */
    private boolean isDelivered(McuiMessage msg) {
        mcui.debug("isDel: " + delivered.contains(msg));
        return delivered.contains(msg);
        //mcui.debug("isDel: " + (msg.getGlobalSeq() < expectedSeq));
        //return msg.getGlobalSeq() < expectedSeq;
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

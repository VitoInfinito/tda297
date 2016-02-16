
import mcgui.*;
import java.util.ArrayList;

/**
 * Simple example of how to use the Multicaster interface.
 *
 * @author Tomas Hasselquist (tomasha@student.chalmers.se) & David Gardtman (gardtman@student.chalmers.se)
 */
public class McuiCaster extends Multicaster {
    private int seq, localSeq, expectedSeq;
    private ArrayList<McuiMessage> msgBag;
    private ArrayList<McuiMessage> delivered;
    private ArrayList<McuiMessage> notDelivered;
    private ArrayList<McuiMessage> noGlobalSequence;
    private int[] next;
    private boolean[] active;
    private int sequencerID = 0;

    /**
     * No initializations needed for this simple one
     */
    public void init() {
        if (id == sequencerID) {
            mcui.debug("I am the sequencer");
        }


        seq = localSeq = 0;
        expectedSeq = 1;
        msgBag = new ArrayList<McuiMessage>();
        delivered = new ArrayList<McuiMessage>();
        notDelivered = new ArrayList<McuiMessage>();
        noGlobalSequence = new ArrayList<McuiMessage>();
        next = new int[hosts];
        active = new boolean[hosts];
        for(int i=0; i<hosts; i++) {
            next[i] = 1;
            active[i] = true;
        }
        
        mcui.debug("The network has " + hosts + " hosts!");
    }
        
    /**
     * The GUI calls this module to multicast a message
     */
    public void cast(String messagetext) {
        // Creating message with its own id as sender and its own expected 
        // next local sequence number as local sequence number to message
        McuiMessage msg = new McuiMessage(id, ++localSeq, messagetext);
        notDelivered.add(msg);
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

    private void sendHelper(int peer, McuiMessage msg) {
        if (active[peer]) {
            bcom.basicsend(peer, msg);
        }
    }

    /**
     * Used by sequencer to add global sequence number and then broadcast the message
     * Then send message to everyone, including itself
     */
    private void sequencerBroadcast(McuiMessage message) {
        //mcui.debug("S-broad");
        if (next[message.getOriginalSender()] == message.getLocalSeq()) {
            next[message.getOriginalSender()]++;
            McuiMessage globalMsg = new McuiMessage(message, id, ++seq);
            for (int i=0; i<hosts; i++) {
                sendHelper(i, globalMsg);
            }
            noGlobalSequence.remove(message);
        } else {
            noGlobalSequence.add(message);
        }
        checkLocalSequenceNumber(message.getOriginalSender(), message);
    }

    private void checkLocalSequenceNumber(int peer, McuiMessage mesg) {
        mcui.debug("No local sequence check: " + mesg.getOriginalSender() + " " + mesg.getLocalSeq());
        for (int i=0; i<noGlobalSequence.size(); i++) {
            McuiMessage msg = noGlobalSequence.get(i);
            if (msg.getOriginalSender() == peer && msg.getLocalSeq() == next[peer]) {
                sequencerBroadcast(msg);
                break;
            }
        }
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
                if (i != id && i != msg.getSender()) {
                    sendHelper(i, globalMsg);
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
                
                // Updating non-sequensers local number in case they become sequencers
                if (id != sequencerID) {
                   next[msg.getOriginalSender()]++;
                }

                delivered.add(msgInBag);
                msgBag.remove(msgInBag);
                notDelivered.remove(msgInBag);

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
        //mcui.debug("isDel: " + delivered.contains(msg));
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
        active[peer] = false;
        mcui.debug("Peer " + peer + " has been dead for a while now!");
        if (peer == sequencerID) {
            for(int i=sequencerID+1; i<hosts; i++) {
                if (active[i]) {
                    sequencerID = i;
                    break;
                }
            }

            for(int i=0; i<notDelivered.size(); i++) {
                sendHelper(sequencerID, notDelivered.get(i));
            }
        }
    }
}

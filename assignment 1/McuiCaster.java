
import mcgui.*;
import java.util.ArrayList;

/**
 * Simple example of how to use the Multicaster interface.
 *
 * @author Tomas Hasselquist (tomasha@student.chalmers.se) & David Gardtman (gardtman@student.chalmers.se)
 */
public class McuiCaster extends Multicaster {
    private int seq, localSeq, expectedSeq; //Global, local and expected sequence numbering
    private ArrayList<McuiMessage> msgBag; //Holding messages to check if the caster holds the expected message
    private ArrayList<McuiMessage> delivered; //History of all delivered messages
    private ArrayList<McuiMessage> notDelivered; //All not-delivered messages a caster created in case sequencer goes down
    private ArrayList<McuiMessage> noGlobalSequence; //All received messages a sequencer got that is not the expected from a specific node
    private int[] next; //Next expected local sequence number from each node
    private boolean[] active; //Information about what nodes are active "alive"
    private int sequencerID; //What node is the current sequencer

    /**
     * Initialization of the caster
     */
    public void init() {
        sequencerID = 0;
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
     * @param messagetext  The text to be cast
     */
    public void cast(String messagetext) {
        // Creating message with its own id as sender and its local sequence number attached
        McuiMessage msg = new McuiMessage(id, ++localSeq, messagetext);
        notDelivered.add(msg);
        // Send message to sequencer first
        bcom.basicsend(sequencerID, msg);
    }
    
    /**
     * Receive a basic message
     * @param peer  unused
     * @param message  The message received
     */
    public void basicreceive(int peer, Message message) {
        //Check if I am not sequencer and if I received a message without global sequence number
        if (((McuiMessage)message).getGlobalSeq() == -1) {
            if (id == sequencerID) {
                sequencerBroadcast((McuiMessage) message);
            } else {
                noGlobalSequence.add((McuiMessage) message);
            }
        } else {
            deliver((McuiMessage) message);
        }        
    }

    /**
     * Used to check if node is active before sending message
     * @param peer  The node to send to
     * @param msg  The message to send
     */
    private void sendHelper(int peer, McuiMessage msg) {
        if (active[peer]) {
            bcom.basicsend(peer, msg);
        }
    }

    /**
     * Used by sequencer to add global sequence number and then broadcast the message
     * Then send message to everyone, including itself
     * @param message  The message to attach sequence number to and broadcast
     */
    private void sequencerBroadcast(McuiMessage message) {
        if (next[message.getOriginalSender()] == message.getLocalSeq()) {
            McuiMessage globalMsg = new McuiMessage(message, id, ++seq);
            next[message.getOriginalSender()]++;
            for (int i=0; i<hosts; i++) {
                if (i != id) {
                    sendHelper(i, globalMsg);
                }
            }
            noGlobalSequence.remove(message);
            deliver(globalMsg);
        } else {
            noGlobalSequence.add(message);
        }
        checkLocalSequenceNumber(message.getOriginalSender());
    }

    /**
     * Function used to check through all active received messages 
     * not yet having received a global sequence number
     */
    private void checkAllLocalSequenceNumbers() {
        for(int i=0; i<hosts; i++) {
            if (active[i]) {
                checkLocalSequenceNumber(i);
            }
        }
    }

    /**
     * Checking through received messages without a global sequence number to see if we have
     * received the expected next message from that node yet
     * @param peer  The peer to check
     */
    private void checkLocalSequenceNumber(int peer) {
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
        if (id != msg.getSender()) {
            McuiMessage globalMsg = new McuiMessage(msg, id);
            for (int i=0; i<hosts; i++) {
                if (i != id && i != msg.getSender()) {
                    sendHelper(i, globalMsg);
                }
            }
        }
    }

    /**
     * Delivers message if its not already delivered
     * @param msg  The message to deliver
     */
    private void deliver(McuiMessage msg) {
        if (!isDelivered(msg)) {
            bagDelivery(msg);
            reBroadcast(msg);
        }
    }

    /**
     * Used as a helper function for deliver. Goes through bag to see if any message is the
     * next one to be delievered
     * @param msg  The message to deliver
     */
    private void bagDelivery(McuiMessage msg) {
        int sender = msg.getSender();
        msgBag.add(msg);
        int i = 0;
        while (i < msgBag.size()) {
            McuiMessage msgInBag = msgBag.get(i);
            i++;
            if (msgInBag.getGlobalSeq() == expectedSeq) {
                mcui.deliver(msg.getOriginalSender(), msg.getText());
                expectedSeq++;
                
                // Updating non-sequensers local number in case they become sequencers
                if (id != sequencerID) {
                   next[msg.getOriginalSender()] = msg.getLocalSeq() + 1;
                }

                delivered.add(msgInBag);
                msgBag.remove(msgInBag);
                notDelivered.remove(msgInBag);

                // Since message was delivered, go through entire bag again in search for next expected
                i = 0;
            }
        }
    }

    /**
     * Checks if message has been delivered
     * @param msg  The message to check
     */
    private boolean isDelivered(McuiMessage msg) {
        return delivered.contains(msg);
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
                    if (id == i) {
                        seq = expectedSeq-1;
                        checkAllLocalSequenceNumbers();
                    }
                    break;
                }
            }

            for(int i=0; i<notDelivered.size(); i++) {
                sendHelper(sequencerID, notDelivered.get(i));
            }
        }
    }
}

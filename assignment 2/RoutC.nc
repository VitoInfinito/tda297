/* =========================================================== *
 * 
 * =========================================================== */

#include "Timer.h"

#include "Rout.h"

module RoutC
{
  uses {
    interface Boot;
    interface Timer<TMilli> as PeriodTimer;
    
    interface Random;
    interface ParameterInit<uint16_t> as RandomSeed;
    interface Init as RandomInit;
    
    interface AMSend  as MessageSend;
    interface Packet  as MessagePacket;
    interface Receive as MessageReceive;

    interface Queue<rout_msg_t> as RouterQueue;

    interface SplitControl as MessageControl;
  }
  
}

implementation
{

  /* ==================== GLOBALS ==================== */
  /* Common message buffer*/
  message_t packet;
  rout_msg_t *message;

  /* Node to send messages to for routing towards sink */
  int16_t router = -1; 
  /* Node to use as cluster head */
  int16_t myClusterHead = -1;

  bool routerlessreported = FALSE;

  /* If node is looking for a new router */
  bool switchrouter = TRUE;

  /* If the message buffer is in use*/
  bool locked = FALSE;

  /* Battery level */
  uint16_t battery = 0;

  /* Cluster head */
  bool isClusterHead = FALSE;

  /* Cluster collector data */
  uint16_t clusterData = 0;

  static uint32_t roundcounter = 0;

  /* ==================== HELPER FUNCTIONS ==================== */

  /* Returns a random number between 0 and n-1 (both inclusive)	*/
  uint16_t random(uint16_t n) {
      /* Modulu is a simple but bad way to do it! */
      return (call Random.rand16()) % n;
  }

  bool isSink() {
    return TOS_NODE_ID == SINKNODE;
  }

  int16_t distanceBetweenXY(int16_t ax,int16_t ay,int16_t bx,int16_t by) {
    return (bx - ax) * (bx-ax) + (by - ay) * (by-ay);
  }

  int16_t distanceBetween(int16_t aid,uint16_t bid) {
    int16_t ax = aid % COLUMNS;
    int16_t ay = aid / COLUMNS;
    int16_t bx = bid % COLUMNS;
    int16_t by = bid / COLUMNS;
    return distanceBetweenXY(ax, ay, bx, by);
  }
  
  int16_t distance(int16_t id) {
    return distanceBetween(SINKNODE, id);
  }
  
  char *messageTypeString(int16_t type) {
    switch(type) {
    case TYPE_ANNOUNCEMENT:
      return "ANNOUNCEMENT";
    case TYPE_CONTENT:
      return "CONTENT";
    case TYPE_CLUSTER:
      return "CLUSTER";
    case TYPE_CLUSTER_ANNOUNCEMENT:
      return "CLUSTER ANNOUNCEMENT";
    default:
      return "Unknown";
    }
  }

  void findClusterHeads() {
    int16_t d = distance(TOS_NODE_ID);
    isClusterHead = FALSE;

    //isClusterHead = TOS_NODE_ID%3 == roundcounter%ROUNDS;
    //isClusterHead = TOS_NODE_ID%3 == random(3);
    //dbg("Error","Current distance %d\n", (roundcounter/ROUNDS) % ROUNDS);
    if (d%3  == ((roundcounter/ROUNDS) % ROUNDS)) {
      isClusterHead = TRUE;
    }
    /*if(isClusterHead) {
      dbg("Error","%d is collector\n", TOS_NODE_ID);
    } else {
      dbg("Error","%d is not collector\n", TOS_NODE_ID);
    }*/

    /*if (TOS_NODE_ID%5 == ((roundcounter/ROUNDS)/2)%5) {
      isClusterHead = TRUE;
    }*/
  }

#define dbgMessageLine(channel,str,mess) dbg(channel,"%s{%d, %s, %d}\n", str, mess->from, messageTypeString(mess->type),mess->seq);
#define dbgMessageLineInt(channel,str1,mess,str2,num) dbg(channel,"%s{%d, %s, %d}%s%d\n", str1, mess->from, messageTypeString(mess->type),mess->seq,str2,num);

  /* ==================== STARTUP ==================== */

  void startnode() {
    battery = BATTERYSTART;
    findClusterHeads();
    call PeriodTimer.startPeriodic(PERIOD);
  }

  void stopnode() {
    battery = 0;
    call PeriodTimer.stop();
  }

  event void Boot.booted() {
    call RandomInit.init();
    call MessageControl.start();
    message = (rout_msg_t*)call MessagePacket.getPayload(&packet, sizeof(rout_msg_t));
  }

  event void MessageControl.startDone(error_t err) {
    if (err == SUCCESS) {
      startnode();
    } else {
      call MessageControl.start();
    }
  }

  event void MessageControl.stopDone(error_t err) {
    ;
  }

  /* ==================== BATTERY ==================== */

  /* Returns whether battery has run out */
  uint16_t batteryEmpty() {
    return USEBATTERY && battery == 0;
  }
  
  /**/
  void batteryCheck() {
    if(batteryEmpty()) {
      dbg("Battery","Battery: Node ran out of battery\n");
      stopnode();
    }
  }

  /* Uses the stated level of battery. 
   * Returns wether it was enough or not. 
   * Shuts the node down if battery is emptied
   */
  bool batteryUse(uint16_t use) {
    bool send = (use <= battery);
    if(battery == 0) {
      return FALSE;
    }
    if(send) {
      battery -= use;
      dbg("BatteryUse","BatteryUse: Decreased by %d down to %d\n",use,battery);
    } else {
      battery = 0;
      batteryCheck();
      dbg("BatteryUse","BatteryUse: Ran out when trying to send\n");
    }
    return send;
  }

  uint16_t batteryRequiredForSend(am_addr_t receiver) {
    if(receiver == AM_BROADCAST_ADDR) {
      return MAXDISTANCE;
    } else {
      return distanceBetween(TOS_NODE_ID,receiver);
    }
  }

  /* Uses up battery for sending a message to receiver and returns whether
   * enough battery was left to complete the send. */
  bool batteryUseForSend(am_addr_t receiver) {
    if(USEBATTERY) {
      return batteryUse(batteryRequiredForSend(receiver));
    } else {
      return TRUE;
    }
  }

  /* ==================== ROUTING ==================== */

  void sendMessage(am_addr_t receiver) {
    if(!batteryUseForSend(receiver)) {
      return;
    }
    if (call MessageSend.send(receiver, &packet, sizeof(rout_msg_t)) == SUCCESS) {
      locked = TRUE;
      
      switch(message->type) {
      case TYPE_ANNOUNCEMENT:
      case TYPE_CLUSTER_ANNOUNCEMENT:
      	dbgMessageLine("Announcement","Announcement: Sending message ",message);
      	break;
      case TYPE_CONTENT:
      case TYPE_CLUSTER:
      	dbgMessageLineInt("Content","Content: Sending message ",message," via ",receiver);
      	break;
      default:
   	    dbg("Error","ERROR: Unknown message type");
      }
    } else {
   	  dbg("Error","ERROR: MessageSend failed");
    }
    batteryCheck();
  }

  void rout() {
    if(call RouterQueue.empty()) {
      dbg("RoutDetail", "Rout: Rout called with empty queue\n");
    } else if(locked) {
      dbg("RoutDetail", "Rout: Message is locked.\n");
    } else if(batteryEmpty()) {
      dbg("RoutDetail", "Rout: Battery is empty.\n");
    } else {
      am_addr_t receiver;
      bool send = FALSE;
      rout_msg_t m = call RouterQueue.head();
      uint8_t type = m.type;
      dbg("RoutDetail", "Rout: Message will be sent.\n");
      switch(type) {
        case TYPE_CLUSTER_ANNOUNCEMENT:
        case TYPE_ANNOUNCEMENT:
        	receiver = AM_BROADCAST_ADDR;
        	send = TRUE;
        	break;
        case TYPE_CONTENT:
        case TYPE_CLUSTER:
        	if((router == -1 && type == TYPE_CLUSTER) || (myClusterHead == -1 && type == TYPE_CONTENT)) {
          //if (router == -1) {
        	  dbg("RoutDetail", "Rout: No router.\n");
        	  if(!routerlessreported) {
        	    dbg("Rout", "Rout: No router to send to %d\n", type);
        	    routerlessreported = TRUE;
        	  }
        	} else {
            if (type == TYPE_CONTENT) {
              receiver = myClusterHead;
            } else {
              receiver = router;
            }
        	  send = TRUE;
        	}
        	break;
        default:
        	dbg("Error", "ERROR: rout() Unknown message type %d\n", type);
      }
      if(send) {
      	*message = call RouterQueue.dequeue();
      	sendMessage(receiver);
      }
    }
  }

  void routMessage() {
    if(call RouterQueue.enqueue(*message) != SUCCESS) {
      dbgMessageLine("Rout", "Rout: queue full, message dropped:", message);
    }
    /* Stupid way to put in front of queue */
    if(message->type == TYPE_ANNOUNCEMENT || message->type == TYPE_CLUSTER_ANNOUNCEMENT) {
      rout_msg_t m = call RouterQueue.head();
      while(m.type != TYPE_ANNOUNCEMENT && m.type != TYPE_CLUSTER_ANNOUNCEMENT) {
      	m = call RouterQueue.dequeue();
      	call RouterQueue.enqueue(m);
      	m = call RouterQueue.head();
      }
    }
    rout();
  }

  /* ==================== ANNOUNCEMENT ==================== */

  void sendAnnounceGeneric(uint8_t type) {
    message->from = TOS_NODE_ID;
    message->type = type;
    routMessage();
  }

  /*
   * Here is what is sent in an announcement
   */
  void sendAnnounce() {
    sendAnnounceGeneric(TYPE_ANNOUNCEMENT);
  }

  void sendClusterAnnounce() {
    sendAnnounceGeneric(TYPE_CLUSTER_ANNOUNCEMENT);
  }
  
  /*
   * This it what a node does when it gets an announcement from
   * another node. Here is where the node chooses which node to use as
   * its router.
   */
  void announceReceive(rout_msg_t *mess) {
    int16_t metos, dtos, mecr, rtos, mecd;
    if(switchrouter) {
      /* We need updated router information */
      switchrouter = FALSE;
      router = -1;
      myClusterHead = -1;
    }

    /* Here is the Basic routing algorithm. You will do a better one below. */
    if(BASICROUTER) {
      metos = distance(TOS_NODE_ID);
      dtos   = distance(mess->from);
      if(router == -1 && metos > dtos) {
	      router = mess->from;
      }
    } 

    /* Here is where you take a better decision. 
     * Set BASICROUTER to 0 and your algorithm runs istead.
     * You have to change in other places as well of course.
     * It's nice if you can switch back and forth by setting
     * BASICROUTER, but it's not a requirement.
     */
    else {
      metos = distance(TOS_NODE_ID);
      dtos   = distance(mess->from);
      if (router != -1) {
        rtos = distance(router);
        mecr = batteryRequiredForSend(router);
        mecd = batteryRequiredForSend(mess->from);

        if (dtos <= metos && mecd <= mecr) {
          router = mess->from;
        }
      } else if (dtos < metos) {
        router = mess->from;
      }

      // Also if announcement is from cluster head and i am not a cluster head
      if (mess->type == TYPE_CLUSTER_ANNOUNCEMENT && !isClusterHead) {
        // Since all clusters will rout a message to sink
        // We only send to closest one to preserve as much battery as possible
        //Set first found as cluster head or if closer to self
        if (myClusterHead != -1) {
          mecr = batteryRequiredForSend(myClusterHead);
          mecd = batteryRequiredForSend(mess->from);
          if (mecd <= mecr) {
            myClusterHead = mess->from;
          }
        } else {
          myClusterHead = mess->from;
        }
      }
    }
  }

  /* ==================== CONTENT ==================== */
  
  void sendContent() {
    static uint32_t sequence = 0;
    message->from    = TOS_NODE_ID;       /* The ID of the node */
    message->type    = TYPE_CONTENT;
    message->content = 1;
    message->seq     = sequence++;
    routMessage();
    switchrouter = TRUE; /* Ready for another router round */
  }

  void sendClusterContent() {
    static uint32_t sequence = 0;
    message->from    = TOS_NODE_ID;       /* The ID of the node */
    message->type    = TYPE_CLUSTER;
    message->content = clusterData;
    message->seq     = sequence++;
    clusterData = 0;
    routMessage();
    switchrouter = TRUE; /* Ready for another router round */
  }


  void contentReceive(rout_msg_t *mess) {
    if(call RouterQueue.enqueue(*mess) == SUCCESS) {
      dbg("RoutDetail", "Rout: Message from %d enqueued\n", mess-> from);
    } else {
      dbgMessageLine("Rout", "Rout: queue full, message dropped:", mess);
    }
    rout();
  }

  /*
   * This is what the sink does when it gets content:
   * It just collects it.
   */
  void contentCollect(rout_msg_t *mess) {
    static uint16_t collected = 0;
    if(mess->content > 0) {
      collected += mess->content;
    }
    dbg("Sink", "Sink: Have now collected %d pieces of information\n", collected);
  }

  /* ==================== EVENT CENTRAL ==================== */

  /* This is what drives the rounds
   * We assume that the nodes are synchronized
   */
  event void PeriodTimer.fired() {
    if(batteryEmpty()) {
      return;
    }

    dbg("Event","--- EVENT ---: Timer @ round %d\n",roundcounter);
    switch(roundcounter % ROUNDS) {
      case ROUND_ANNOUNCEMENT: /* Announcement time */
        if(isSink()) {
  	      dbg("Round","========== Round %d ==========\n",roundcounter/ROUNDS);
        }
        // Finding new cluster collectors for the following round
        findClusterHeads();
        // If a cluster collector. send announcement
        if (isClusterHead) {
          sendClusterAnnounce();
        } else {
          sendAnnounce();
        }
        break;
      case ROUND_CONTENT: /* Message time */
        if(!isSink()) {
          if (!isClusterHead) {
            sendContent();
          } else {
            clusterData++;
          }
  	      
        }
        break;
      case ROUND_CLUSTER:
        if(!isSink() && isClusterHead) {
          sendClusterContent();
        }
        break;
      default:
        dbg("Error", "ERROR: Unknown round %d\n", roundcounter);
    }
    roundcounter++;
  }
  
  event message_t* MessageReceive.receive(message_t* bufPtr, void* payload, uint8_t len) {
    rout_msg_t* mess = (rout_msg_t*)payload;
    if(batteryEmpty()) {
      return bufPtr;
    }

    dbgMessageLine("Event","--- EVENT ---: Received ",mess);
    switch(mess->type) {
    case TYPE_ANNOUNCEMENT:
    case TYPE_CLUSTER_ANNOUNCEMENT:
      dbgMessageLine("Announcement","Announcement: Received ",mess);
      announceReceive(mess);
      break;
    case TYPE_CLUSTER:
    //case TYPE_CONTENT:
      dbgMessageLine("Content","Content: Received ",mess);
      if(isSink()) {
	      contentCollect(mess);
      } else {
	      contentReceive(mess);
      }
      break;
    case TYPE_CONTENT:
      if(isClusterHead) {
        clusterData++;
      }
      break;
    default:
      dbg("Error", "ERROR: messageReceive() Unknown message type %d\n",mess->type);
    }

    /* Because of lack of memory in sensor nodes TinyOS forces us to
     * maintain an equilibrium by givin a buffer back for every
     * message we get. In this case we give it back immediately.  
     * So do not try to save a pointer somewhere to this or the
     * payload */
    return bufPtr;
  }
  
  /* Message has been sent and we are ready to send another one. */
  event void MessageSend.sendDone(message_t* bufPtr, error_t error) {
    dbgMessageLine("Event","--- EVENT ---: sendDone ",message);
    if (&packet == bufPtr) {
      locked = FALSE;
      rout();
    } else {
      dbg("Error", "ERROR: Got sendDone for another message\n");
    }
  }
  

}



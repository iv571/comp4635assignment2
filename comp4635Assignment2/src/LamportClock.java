import java.io.Serializable;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LamportBroadcastNode implements a Lamport Clock based broadcast algorithm
 * ensuring FIFO-total order of message delivery in a distributed system.
 */
public class LamportClock extends UnicastRemoteObject implements LamportClockImpl, Serializable {
    private final int nodeId; // Unique ID of this node
    private final AtomicInteger lamportClock; // Lamport logical clock (thread-safe)
    private final PriorityBlockingQueue<Message> holdBackQueue; // Priority queue for messages
    private final Map<MessageKey, Set<Integer>> ackBuffer; // Buffer for early acks (if message not yet received)
    private final Map<Integer, Integer> deliveredClock; // Tracks the latest delivered timestamp per sender (for FIFO
                                                        // check)
    private List<LamportClock> peers; // Peers (other nodes) to broadcast to
    private final AtomicInteger timestamp;

    /**
     * Constructs a LamportBroadcastNode.
     * 
     * @param nodeId Unique identifier for this node.
     */
    public LamportClock(int nodeId) throws RemoteException {
        this.timestamp = new AtomicInteger(0);
        this.nodeId = nodeId;
        this.lamportClock = new AtomicInteger(0);
        // Priority queue sorted by (timestamp, senderId) for total
        // order&#8203;:contentReference[oaicite:5]{index=5}
        this.holdBackQueue = new PriorityBlockingQueue<>();
        this.ackBuffer = Collections.synchronizedMap(new HashMap<>());
        this.deliveredClock = new HashMap<>();
    }

    /**
     * Increments the Lamport clock (for local events).
     * 
     * @return The new timestamp.
     */
    public synchronized int tick() throws RemoteException {
        return timestamp.incrementAndGet();
    }

    /**
     * Updates the clock based on a received timestamp.
     * Applies the rule: clock = max(localClock, receivedTimestamp) + 1.
     * 
     * @param receivedTimestamp The timestamp received in a message.
     * @return The updated timestamp.
     */
    public synchronized int update(int receivedTimestamp) throws RemoteException {
        timestamp.set(Math.max(timestamp.get(), receivedTimestamp) + 1);
        return timestamp.get();
    }

    /**
     * Gets the current Lamport timestamp.
     * 
     * @return The current logical clock value.
     */
    public synchronized int getTime() throws RemoteException {
        return timestamp.get();
    }

    /**
     * Sets the list of peer nodes to broadcast messages and acknowledgments to.
     * (In a real system, this might be replaced by networking code.)
     */
    public void setPeers(List<LamportClock> peers) {
        // Exclude itself from peers list if present
        this.peers = new ArrayList<>();
        for (LamportClock p : peers) {
            if (p.nodeId != this.nodeId) {
                this.peers.add(p);
            }
        }
    }

    /**
     * Broadcasts a new application message to all peers (and adds it to local
     * queue).
     * 
     * @param content The application-level content of the message.
     */
    public synchronized void send(String content, GameRoom gameRoom) throws RemoteException {
        // Step 1: Increment Lamport clock for the send event
        int timestamp = lamportClock.incrementAndGet();
        // Create a Message for this event
        Message msg = new Message(timestamp, this.nodeId, content);
        // Add message to own queue (it's as if this node "received" its own send)
        holdBackQueue.add(msg);
        // Initialize deliveredClock for this sender if not present
        deliveredClock.putIfAbsent(this.nodeId, -1);
        // **Simulate self-receive**: Update clock (again) and broadcast acknowledgment
        // (The sender acts like a receiver of the message, per Lamport's multicast
        // algorithm&#8203;:contentReference[oaicite:6]{index=6})
        updateLamportOnReceive(timestamp);
        // Broadcast the message to all other peers
        for (LamportClock peer : peers) {
            peer.onReceiveMessage(timestamp, this.nodeId, content, gameRoom);
        }

        /*
         * Upon "receiving" its own message, this node will also send an ack in
         * onReceiveMessage.
         * Delivery check after sending (in case message had no predecessors and a
         * single node scenario)
         */
        attemptDeliver(gameRoom);
    }

    /**
     * Handles an incoming application message (from some sender).
     * This method is thread-safe and updates the Lamport clock, queues the message,
     * and broadcasts an ack.
     * 
     * @param timestamp The Lamport timestamp of the incoming message.
     * @param senderId  The ID of the sender of the message.
     * @param content   The content of the message.
     */
    public synchronized void onReceiveMessage(int timestamp, int senderId, String content, GameRoom gameRoom) {
        updateLamportOnReceive(timestamp); // ✅ Increments Lamport clock
        // Create Message object and add to queue
        Message msg = new Message(timestamp, senderId, content);

        holdBackQueue.add(msg); // ✅ Store in priority queue. Internally, the PriorityQueue uses compareTo
                                // function to sort
        msg.addAck(this.nodeId); // ✅ Acknowledge it yourself

        // Initialize deliveredClock tracking for this sender if not present
        deliveredClock.putIfAbsent(senderId, -1);

        // If any acknowledgments for this message arrived before the message, process
        // them
        MessageKey key = new MessageKey(senderId, timestamp);

        if (ackBuffer.containsKey(key)) { // ✅ Apply any early ACKs
            Set<Integer> earlyAckSenders = ackBuffer.remove(key);
            for (int ackSenderId : earlyAckSenders) {
                msg.addAck(ackSenderId);
            }
        }

        // Broadcast an acknowledgment to all peers (including the original sender):
        // Ack contains this node's ID and current Lamport time.
        int ackTimestamp = lamportClock.get(); // ✅ Use updated Lamport time for ACK
        for (LamportClock peer : peers) {
            peer.onReceiveAck(senderId, timestamp, this.nodeId, ackTimestamp, gameRoom); // ✅ Broadcast ACK
        }
        attemptDeliver(gameRoom); // ✅ Try to deliver in total order
    }

    /**
     * Handles an incoming acknowledgment for a message.
     * 
     * @param origSenderId  The ID of the original sender of the message being
     *                      acknowledged.
     * @param origTimestamp The Lamport timestamp of the original message.
     * @param ackSenderId   The ID of the node sending this acknowledgment.
     * @param ackTimestamp  The Lamport timestamp at the acknowledger when sending
     *                      the ack.
     */
    public synchronized void onReceiveAck(int origSenderId, int origTimestamp, int ackSenderId, int ackTimestamp,
            GameRoom gameRoom) {

        updateLamportOnReceive(ackTimestamp);
        MessageKey key = new MessageKey(origSenderId, origTimestamp);
        // Find the message in queue, if present, and mark this ack
        for (Message m : holdBackQueue) {
            if (m.matches(origSenderId, origTimestamp)) {
                m.addAck(ackSenderId);
                // We break after marking, as each ack corresponds to one message
                break;
            }
        }
        // If message not yet received (not in queue), store the ack in buffer for
        // later&#8203;:contentReference[oaicite:8]{index=8}
        if (!ackBuffer.containsKey(key) && !hasMessageInQueue(origSenderId, origTimestamp)) {
            // Create a set to hold ack senders if this is the first ack for that message
            ackBuffer.putIfAbsent(key, Collections.synchronizedSet(new HashSet<>()));
        }
        if (ackBuffer.containsKey(key)) {
            ackBuffer.get(key).add(ackSenderId);
        }
        // Try delivering any messages that might now meet the conditions
        attemptDeliver(gameRoom);
    }

    /**
     * Attempts to deliver messages from the head of the queue if they satisfy
     * the FIFO and total order conditions.
     */
    private synchronized void attemptDeliver(GameRoom gameRoom) {
        while (true) {
            Message head = holdBackQueue.peek();
            if (head == null) {
                break; // no messages to deliver
            }
            int senderId = head.senderId;
            int ts = head.timestamp;

            /*
             * FIFO Check: Prior messages from the same sender are delivered
             * If the message was already delivered → skip
             * 
             * If an earlier one hasn't been delivered → wait
             */
            int lastDeliveredTs = deliveredClock.getOrDefault(senderId, -1);
            if (lastDeliveredTs >= 0 && ts <= lastDeliveredTs) {
                // This message (ts) or older was already delivered; remove duplicates if any
                holdBackQueue.poll();
                continue;
            }

            /*
             * If we suspect a prior message exists that is not delivered yet, we should
             * // wait.
             * // (In practice, if channels are FIFO, this won't happen. Otherwise, a
             * sequence
             * // check is needed.)
             */
            if (lastDeliveredTs == -1 && senderId != this.nodeId) {
                // If we've never delivered anything from this sender, assume no prior message
                // or it's waiting to arrive.
                // (This check can be refined if message sequence numbers are used to detect
                // gaps.)
            }

            // Total Order Check: Message has been ACKed by all
            if (!head.isFullyAcked(getTotalProcessCount())) {
                // Not all acknowledgments received yet, cannot deliver
                break;
            }
            // If both conditions are satisfied, deliver the message
            holdBackQueue.poll(); // remove from queue
            deliveredClock.put(senderId, ts); // update delivered timestamp for FIFO tracking
            deliverToApplication(head, gameRoom);
            // After delivering, continue loop in case next message is now deliverable
        }
    }

    /**
     * Delivers a message to the application (game state update, etc.).
     * In this implementation, we simply print the delivery as a demonstration.
     */
    private void deliverToApplication(Message msg, GameRoom gameRoom) {
        System.out.println("Node " + nodeId + " delivered message from Node "
                + msg.senderId + " (timestamp=" + msg.timestamp + "): "
                + msg.content);
        
        String playerName = gameRoom.getPlayerName(msg.senderId);

        try {
			gameRoom.processGuess(msg.content, playerName);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }

    /**
     * Helper to update the Lamport clock on message/ack receive events.
     * Sets clock = max(current, receivedTimestamp) + 1.
     * 
     * @param receivedTs The timestamp received from another process.
     */
    private void updateLamportOnReceive(int receivedTs) {
        // Atomically update the clock based on a received
        // timestamp&#8203;:contentReference[oaicite:9]{index=9}
        lamportClock.updateAndGet(current -> Math.max(current, receivedTs) + 1);
    }

    /**
     * Checks if a message from a given sender with a given timestamp exists in the
     * queue.
     */
    private boolean hasMessageInQueue(int senderId, int timestamp) {
        for (Message m : holdBackQueue) {
            if (m.matches(senderId, timestamp)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the total number of processes in the system (including this node and
     * peers).
     */
    private int getTotalProcessCount() {
        // total processes = this node + all peers
        return peers.size() + 1;
    }

    // Inner classes for Message and MessageKey:

    /**
     * Message represents a broadcasted application message with a Lamport timestamp
     * and sender ID.
     * It implements Comparable to be ordered by (timestamp, senderId) in the
     * priority queue.
     */
    private static class Message implements Comparable<Message> {
        final int timestamp;
        final int senderId;
        final String content;
        private final Set<Integer> ackedBy; // set of node IDs that have acknowledged this message

        Message(int timestamp, int senderId, String content) {
            this.timestamp = timestamp;
            this.senderId = senderId;
            this.content = content;
            this.ackedBy = new HashSet<>();
            // Initially, only the node holding this Message knows about it (itself).
            // We count self acknowledgment implicitly.
            this.ackedBy.add(senderId);
        }

        /** Mark that a given node has acknowledged this message. */
        synchronized void addAck(int ackSenderId) {
            ackedBy.add(ackSenderId);
        }

        /** Checks if the message has been acknowledged by all expected nodes. */
        synchronized boolean isFullyAcked(int totalNodes) {
            // If ackedBy contains all process IDs (of size totalNodes), message is fully
            // acknowledged
            return ackedBy.size() == totalNodes;
        }

        /** Helper to identify message by original sender and timestamp. */
        boolean matches(int origSenderId, int origTs) {
            return this.senderId == origSenderId && this.timestamp == origTs;
        }

        @Override
        public int compareTo(Message other) {
            // Order by timestamp, then by senderId to break
            // ties&#8203;:contentReference[oaicite:10]{index=10}
            if (this.timestamp != other.timestamp) {
                return Integer.compare(this.timestamp, other.timestamp);
            }
            return Integer.compare(this.senderId, other.senderId);
        }
    }
    
    @Override
    public synchronized int getId() throws RemoteException {
        return nodeId;
    }

    /**
     * MessageKey is used as a lookup key for messages using (senderId, timestamp).
     * This helps in buffering and matching acknowledgments to messages.
     */
    private static class MessageKey {
        final int origSenderId;
        final int origTimestamp;

        MessageKey(int origSenderId, int origTimestamp) {
            this.origSenderId = origSenderId;
            this.origTimestamp = origTimestamp;
        }

        @Override
        public int hashCode() {
            return Objects.hash(origSenderId, origTimestamp);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof MessageKey))
                return false;
            MessageKey other = (MessageKey) obj;
            return this.origSenderId == other.origSenderId && this.origTimestamp == other.origTimestamp;
        }
    }
}
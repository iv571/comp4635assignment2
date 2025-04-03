
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class LamportMessageHandler {
    private final int nodeId;                             // ID of this node (e.g. server or a specific player if used on client)
    private final AtomicInteger logicalClock = new AtomicInteger(0);
    private final PriorityBlockingQueue<MessageEnvelope> holdBackQueue;
    private final Map<MessageKey, Set<Integer>> ackBuffer;
    private final Map<Integer, Integer> deliveredClock;
    private final int totalNodes;                         // total number of participants expected to ack
    private Map<String, ClientCallback> clientCallbacks;  // references to clients for sending messages
    
 // Map for assigning unique IDs to players.
    private Map<String, Integer> playerIdMap = new HashMap<>();
    
 // Custom enum for game message types
    enum MessageType {
        GAME_EVENT;  // Add additional types if needed
    }

    public LamportMessageHandler(int nodeId, int totalNodes, Map<String, ClientCallback> callbacks) {
        this.nodeId = nodeId;
        this.totalNodes = totalNodes;
        this.clientCallbacks = callbacks;  // all player callbacks in this game room
        this.holdBackQueue = new PriorityBlockingQueue<>();
        this.ackBuffer = Collections.synchronizedMap(new HashMap<>());
        this.deliveredClock = new HashMap<>();
    }

    /** Increment the Lamport clock for a local event (send) */
    private synchronized int tick() {
        return logicalClock.incrementAndGet();
    }

    /** Update local clock on receiving a timestamp from a message or ack */
    private synchronized void updateClock(int receivedTimestamp) {
        int current = logicalClock.get();
        int newTime = Math.max(current, receivedTimestamp) + 1;
        logicalClock.set(newTime);
    }

    /**
     * Broadcast a new game event (message) to all players in the room.
     * @param messageContent The content of the message (already determined, e.g. guess result or notification).
     * @param senderName The name of the player who initiated the event (or null/host for system events).
     */
    public synchronized void broadcastEvent(String messageContent, String senderName) {
        // Determine sender ID
        int senderId = (senderName == null ? nodeId : getPlayerId(senderName));
        // Lamport timestamp for send event
        int ts = tick();
        // Create GameMessage payload
        GameMessage gameMsg = new GameMessage(senderId, senderName, messageContent, ts, MessageType.GAME_EVENT);
        // Wrap in internal envelope and queue it
        MessageEnvelope env = new MessageEnvelope(gameMsg);
        holdBackQueue.add(env);
        // Mark self acknowledgment (the sender implicitly acks its own message)
        deliveredClock.putIfAbsent(senderId, -1);
        env.addAck(nodeId);

        // Send the message to all clients via their callbacks
        for (Map.Entry<String, ClientCallback> entry : clientCallbacks.entrySet()) {
            try {
                entry.getValue().receiveMessage(gameMsg.toString());  // remote RMI call to client
            } catch (RemoteException e) {
                System.err.println("Failed to send message to " + entry.getKey());
                // handle disconnect if needed
            }
        }
        // After sending, check if the message can be delivered (perhaps single-node case or immediate)
        attemptDeliver();
    }

    /**
     * Handle an incoming message from a player (if players directly broadcast events).
     * In a client-server model, this might be called when the server receives a guess from a client.
     * @param messageContent Content of the player's message (e.g. guess).
     * @param senderName Name of the player who sent it.
     */
    public synchronized void onReceiveExternal(String messageContent, String senderName) {
        // Update clock for receive event (no timestamp sent from client, so treat as a new event)
        int ts = tick();  // increment as if we received a new event
        int senderId = getPlayerId(senderName);
        GameMessage gameMsg = new GameMessage(senderId, senderName, messageContent, ts, MessageType.GAME_EVENT);
        MessageEnvelope env = new MessageEnvelope(gameMsg);
        holdBackQueue.add(env);
        deliveredClock.putIfAbsent(senderId, -1);
        // Mark this node's ack (server has "received" it)
        env.addAck(nodeId);

        // Broadcast an acknowledgment to others if needed (in this centralized model,
        // we will instead wait for client acks rather than send ack messages to clients)
        // attemptDeliver will be called after actual client acks are received.
    }

    /** Called when a client sends back an acknowledgment for a message it received. */
    public synchronized void onReceiveAck(int origSenderId, int origTimestamp, int ackSenderId) {
        // Update the logical clock for the ack event
        tick();
        MessageKey key = new MessageKey(origSenderId, origTimestamp);
        // Find the message in the queue and record this acknowledgment
        for (MessageEnvelope env : holdBackQueue) {
            if (env.matches(origSenderId, origTimestamp)) {
                env.addAck(ackSenderId);
                break;
            }
        }
        // If message not in queue yet (unlikely in this design since server queues immediately), buffer the ack
        if (!hasMessageInQueue(origSenderId, origTimestamp)) {
            ackBuffer.putIfAbsent(key, Collections.synchronizedSet(new HashSet<>()));
            ackBuffer.get(key).add(ackSenderId);
        }
        attemptDeliver();
    }

    /** Attempt to deliver any queued messages that meet FIFO and total order conditions. */
    private synchronized void attemptDeliver() {
        while (true) {
            MessageEnvelope head = holdBackQueue.peek();
            if (head == null) break;
            GameMessage msg = head.getGameMessage();
            int senderId = msg.getSenderId();
            int ts = msg.getLamportTimestamp();
            // FIFO check: ensure we have delivered any prior message from this sender
            int lastDeliveredTs = deliveredClock.getOrDefault(senderId, -1);
            if (ts <= lastDeliveredTs) {
                // This message (or an older one) was already delivered; remove duplicates
                holdBackQueue.poll();
                continue;
            }
            // If there is a gap in FIFO order (a previous message from sender not delivered), break
            // (In our setting, this is handled by sequential logic or network FIFO; otherwise, use sequence numbers)
            // Total order check: message fully acknowledged by all participants
            if (!head.isFullyAcked(totalNodes)) {
                break;  // wait until all acks received
            }
            // Both conditions satisfied: deliver the message
            holdBackQueue.poll();
            deliveredClock.put(senderId, ts);
            deliverToClients(msg);
            // Loop to check next message in queue
        }
    }

    /** Deliver message to application - here we notify clients (in order) that the message is final. */
    private void deliverToClients(GameMessage msg) {
        // In a centralized server model, clients have already received the message content.
        // This could be used to trigger any final actions or logging.
        System.out.println("Delivering message from " + msg.getSenderName() +
                           " (ts=" + msg.getLamportTimestamp() + ") to application.");
        // (If clients were buffering messages until deliver, we would notify them to display now.
        // In this design, we already sent the message, so this might just confirm the order.)
    }

    // Helper methods ...
    private boolean hasMessageInQueue(int senderId, int timestamp) {
        for (MessageEnvelope env : holdBackQueue) {
            if (env.matches(senderId, timestamp)) return true;
        }
        return false;
    }
    
    // Simple implementation for getPlayerId using a map.
    private int getPlayerId(String playerName) {
        if (!playerIdMap.containsKey(playerName)) {
            int newId = playerIdMap.size() + 1;
            playerIdMap.put(playerName, newId);
        }
        return playerIdMap.get(playerName);
    }

    private static class MessageEnvelope implements Comparable<MessageEnvelope> {
        private final GameMessage gameMessage;
        private final Set<Integer> ackedBy = Collections.synchronizedSet(new HashSet<>());
        MessageEnvelope(GameMessage msg) {
            this.gameMessage = msg;
            // If sender itself is effectively acknowledging its message:
            // addAck(msg.getSenderId()); -- we handle this outside based on context
        }
        GameMessage getGameMessage() { return gameMessage; }
        synchronized void addAck(int nodeId) { ackedBy.add(nodeId); }
        synchronized boolean isFullyAcked(int totalNodes) { return ackedBy.size() == totalNodes; }
        boolean matches(int origSenderId, int origTs) {
            return gameMessage.getSenderId() == origSenderId && gameMessage.getLamportTimestamp() == origTs;
        }
        @Override
        public int compareTo(MessageEnvelope other) {
            // Order by Lamport timestamp, then by sender ID to break ties
            int tsCompare = Integer.compare(this.gameMessage.getLamportTimestamp(), other.gameMessage.getLamportTimestamp());
            if (tsCompare != 0) return tsCompare;
            return Integer.compare(this.gameMessage.getSenderId(), other.gameMessage.getSenderId());
        }
    }
    
    // Inner class for MessageKey with proper equals and hashCode.
    private static class MessageKey {
        final int origSenderId;
        final int origTimestamp;
        MessageKey(int id, int ts) { this.origSenderId = id; this.origTimestamp = ts; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MessageKey)) return false;
            MessageKey other = (MessageKey) o;
            return this.origSenderId == other.origSenderId &&
                   this.origTimestamp == other.origTimestamp;
        }
        @Override
        public int hashCode() {
            return Objects.hash(origSenderId, origTimestamp);
        }
    }
}
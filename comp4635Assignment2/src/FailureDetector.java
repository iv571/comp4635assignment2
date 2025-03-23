import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class FailureDetector {

    public enum ClientState { ALIVE, SUSPECTED, FAILED }

    public static class FailureRecord {
        // Use a monotonic clock (nanoseconds)
        private long lastInteraction; 
        private ClientState state;
        private int suspectCount;

        public FailureRecord(long timeNano) {
            this.lastInteraction = timeNano;
            this.state = ClientState.ALIVE;
            this.suspectCount = 0;
        }

        public synchronized long getLastInteraction() {
            return lastInteraction;
        }

        public synchronized void updateInteraction(long timeNano) {
            this.lastInteraction = timeNano;
            this.state = ClientState.ALIVE;
            this.suspectCount = 0;
        }

        public synchronized ClientState getState() {
            return state;
        }

        public synchronized void setState(ClientState state) {
            this.state = state;
        }

        public synchronized int getSuspectCount() {
            return suspectCount;
        }

        public synchronized void incrementSuspectCount() {
            this.suspectCount++;
        }
    }

    private final ConcurrentHashMap<String, FailureRecord> records = new ConcurrentHashMap<>();
    private final long toleranceFreq;       // Tolerance in ms
    private final int xFactor;                // Number of suspect cycles before marking FAILED
    private final long checkIntervalFreq;   // Check frequency 
    private volatile boolean running = true;
    
    // Callback reference to the server
    private final CrissCrossImpl server;

    // Modified constructor with callback
    public FailureDetector(long toleranceFreq, int xFactor, long checkIntervalFreq, CrissCrossImpl server) {
        this.toleranceFreq = toleranceFreq;
        this.xFactor = xFactor;
        this.checkIntervalFreq = checkIntervalFreq;
        this.server = server;
        if (checkIntervalFreq > toleranceFreq) {
            throw new IllegalArgumentException("checkIntervalFreq must be <= toleranceFreq");
        }
        new Thread(new FailureDetectorTask()).start();
    }

    // Update client activity using the monotonic clock.
    public void updateClientActivity(String clientName) {
        FailureRecord record = records.get(clientName);
        if (record != null) {
            record.updateInteraction(System.nanoTime());
        }
    }

    // Register a new client with the current nanoTime.
    public void registerClient(String clientName) {
        records.put(clientName, new FailureRecord(System.nanoTime()));
        System.out.println("Registered client " + clientName + " for failure detection.");
    }

    // Unregister a client
    public void unregisterClient(String clientName) {
        records.remove(clientName);
        System.out.println("Unregistered client " + clientName + " from failure detection.");
    }

    // Query client state
    public ClientState getClientState(String clientName) {
        FailureRecord record = records.get(clientName);
        return record != null ? record.getState() : null;
    }

    // Shutdown the detector
    public void shutdown() {
        running = false;
    }

    // Background task to monitor clients using monotonic time.
    private class FailureDetectorTask implements Runnable {
        @Override
        public void run() {
            // Convert tolerance and check interval from ms to ns.
            final long toleranceNanos = toleranceFreq * 1_000_000L;
            final long checkIntervalNanos = checkIntervalFreq * 1_000_000L;

            while (running) {
                try {
                    // Sleep for the specified check interval (still in ms)
                    TimeUnit.MILLISECONDS.sleep(checkIntervalFreq);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                long currentTimeNano = System.nanoTime();
                for (Iterator<Map.Entry<String, FailureRecord>> it = records.entrySet().iterator(); it.hasNext();) {
                    Map.Entry<String, FailureRecord> entry = it.next();
                    String clientName = entry.getKey();
                    FailureRecord record = entry.getValue();

                    synchronized (record) {
                        if (currentTimeNano - record.getLastInteraction() >= toleranceNanos) {
                            if (record.getState() == ClientState.ALIVE) {
                                record.setState(ClientState.SUSPECTED);
                                record.incrementSuspectCount();
                                System.out.println("Client " + clientName + " is now SUSPECTED.");
                            } else if (record.getState() == ClientState.SUSPECTED) {
                                record.incrementSuspectCount();
                                System.out.println("Client " + clientName + " remains SUSPECTED (" 
                                    + record.getSuspectCount() + "/" + xFactor + ").");
                                if (record.getSuspectCount() >= xFactor) {
                                    record.setState(ClientState.FAILED);
                                    System.out.println("Client " + clientName + " has FAILED.");
                                    // Call the server's callback to release game state
                                    server.releaseGameState(clientName);
                                    it.remove(); // Remove failed client
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
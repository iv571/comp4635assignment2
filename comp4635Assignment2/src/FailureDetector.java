import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class FailureDetector {

    public enum ClientState { ALIVE, SUSPECTED, FAILED }

    public static class FailureRecord {
        private long lastInteraction;
        private ClientState state;
        private int suspectCount;

        public FailureRecord(long time) {
            this.lastInteraction = time;
            this.state = ClientState.ALIVE;
            this.suspectCount = 0;
        }

        public synchronized long getLastInteraction() {
            return lastInteraction;
        }

        public synchronized void updateInteraction(long time) {
            this.lastInteraction = time;
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
    private final long toleranceMillis;       // Tolerance for inactivity
    private final int xFactor;                // Number of suspect cycles before marking FAILED
    private final long checkIntervalMillis;   // Frequency of checks
    private volatile boolean running = true;

    public FailureDetector(long toleranceMillis, int xFactor, long checkIntervalMillis) {
        this.toleranceMillis = toleranceMillis;
        this.xFactor = xFactor;
        this.checkIntervalMillis = checkIntervalMillis;
        if (checkIntervalMillis > toleranceMillis) {
            throw new IllegalArgumentException("checkIntervalMillis must be <= toleranceMillis");
        }
        new Thread(new FailureDetectorTask()).start();
    }

    // Update client activity
    public void updateClientActivity(String clientName) {
        FailureRecord record = records.get(clientName);
        if (record != null) {
            record.updateInteraction(System.currentTimeMillis());
        }
    }

    // Register a new client
    public void registerClient(String clientName) {
        records.put(clientName, new FailureRecord(System.currentTimeMillis()));
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

    // Background task to monitor clients
    private class FailureDetectorTask implements Runnable {
        @Override
        public void run() {
            while (running) {
                try {
                    TimeUnit.MILLISECONDS.sleep(checkIntervalMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                long currentTime = System.currentTimeMillis();
                for (Iterator<Map.Entry<String, FailureRecord>> it = records.entrySet().iterator(); it.hasNext();) {
                    Map.Entry<String, FailureRecord> entry = it.next();
                    String clientName = entry.getKey();
                    FailureRecord record = entry.getValue();

                    synchronized (record) {
                        // Changed to >= for immediate detection at toleranceMillis
                        if (currentTime - record.getLastInteraction() >= toleranceMillis) {
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
import java.rmi.Naming;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.io.Serializable;
import java.util.*;

/**
 * The PeerProcess class represents a peer in the multiplayer Criss-Cross puzzle game.
 * <p>
 * Each PeerProcess launches a Lamport clock and a Receiver as RMI remote objects.
 * It provides a command-line interface for proposing a new game, joining an existing game,
 * and checking the game state. Game updates (like word guesses) are broadcast to all peers
 * using Lamport logical timestamps to ensure FIFO-total ordering of events across the distributed game.
 * </p>
 */
public class PeerProcess {
    /** Name/ID of this peer (used as RMI registry binding name and unique identifier) */
    private String peerName;
    /** Flag to indicate if this peer is the host of the current game */
    private boolean isHost = false;
    /** Current game difficulty level (as set by host on proposal) */
    private String gameDifficulty;
    /** Target number of players for the game (as set by host on proposal) */
    private int targetPlayers;
    /** Whether the game has started (true if running, false if pending or no game) */
    private boolean gameStarted = false;
    /** List of participant names in the game (order of joining, index+1 serves as Lamport ID) */
    private List<String> participantNames = new ArrayList<>();
    /** List of participant receiver stubs (parallel to participantNames list) */
    private List<ReceiverInterface> participantStubs = new ArrayList<>();
    /** The Lamport clock for this peer (manages logical timestamps) */
    LamportClockInterface lamportClock;
    /** The Receiver remote object for this peer (handles incoming messages via RMI) */
    private ReceiverInterface receiver;
    //** Multiplayer Puzzle server
    private Mutiplayer_Puzzle puzzleServer;
    private Multiplayer multiplayer;
    private GameRoom currentGameRoom;
    
    private String localPuzzleState = "";
    
    /**
     * Inner remote interface for receiving game messages (via RMI).
     * Peers will call receiveMessage to deliver a broadcast message,
     * and joinGame to request joining a pending game on a host peer.
     */
    public interface ReceiverInterface extends Remote {
        /**
         * Receive a game message broadcast from another peer.
         * This is invoked via RMI to deliver a message (such as a guess or control event) to this peer.
         * @param message The message object containing event details and Lamport timestamp.
         * @throws RemoteException if a communication error occurs.
         */
        void receiveMessage(Message message) throws RemoteException;
        
        /**
         * Join an existing pending game on this peer (host).
         * The joining peer provides its name and remote stub so the host can add it to the game.
         * The host returns the current game state so the joining peer can synchronize.
         * @param newPeerName  Name/ID of the joining peer.
         * @param newPeerStub  Remote ReceiverInterface stub of the joining peer.
         * @return a GameState object containing the current game room state (difficulty, players, etc.).
         * @throws RemoteException if a communication error occurs.
         */
        GameState joinGame(String newPeerName, ReceiverInterface newPeerStub) throws RemoteException;
    }
    
   
    
    /**
     * Inner remote interface for the Lamport clock service.
     * Exposes methods to get or adjust the logical clock. (In practice, peers update their own clocks 
     * internally, but this interface allows the clock to be accessed via RMI if needed.)
     */
    public interface LamportClockInterface extends Remote {
        /** @return the current Lamport timestamp of this peer. */
        int getTime() throws RemoteException;
        /** Increment the Lamport clock for a local event and return the new timestamp. */
        int tick() throws RemoteException;
        /**
         * Update the Lamport clock based on a received timestamp.
         * Uses Lamport's rule: localTime = max(localTime, receivedTime) + 1.
         * @param receivedTime the timestamp received from another peer's message.
         * @return the updated Lamport time after the merge.
         */
        int update(int receivedTime) throws RemoteException;
        /** Set/assign the unique Lamport ID (used for tie-break in ordering) for this peer's clock. */
        void setId(int id) throws RemoteException;
        /** @return the Lamport ID (unique peer identifier for ordering) of this peer. */
        int getId() throws RemoteException;
    }
    
    /**
     * Implementation of the LamportClockInterface.
     * Manages a logical timestamp and a unique ID for the peer.
     * Each peer has one LamportClockImpl, which is registered in the RMI registry.
     */
    private class LamportClockImpl extends UnicastRemoteObject implements LamportClockInterface {
        private static final long serialVersionUID = 1L;
        private int clockValue = 0;     // current Lamport logical time
        private int clockId;           // unique ID for this clock (peer's ID for tie-break)
        
        protected LamportClockImpl(int id) throws RemoteException {
            this.clockId = id;
        }
        
        @Override
        public synchronized int getTime() throws RemoteException {
            return clockValue;
        }
        
        @Override
        public synchronized int tick() throws RemoteException {
            // Increment clock for a local event (e.g., sending a message or internal action)
            clockValue++;
            return clockValue;
        }
        
        @Override
        public synchronized int update(int receivedTime) throws RemoteException {
            // Merge a received timestamp into local clock and advance
            clockValue = Math.max(clockValue, receivedTime) + 1;
            return clockValue;
        }
        
        @Override
        public synchronized void setId(int id) throws RemoteException {
            this.clockId = id;
        }
        
        @Override
        public synchronized int getId() throws RemoteException {
            return clockId;
        }
    }
    
    /**
     * GameState is a serializable snapshot of the game room state, used to transfer state to joining peers.
     * It includes game settings and current participant info so a joining peer can synchronize its local state.
     */
    public static class GameState implements Serializable {
        private static final long serialVersionUID = 1L;
        public int gameId;
        public String difficulty;
        public int targetPlayers;
        public boolean started;
        public List<String> participants;            // Names of all players in the game
        public List<ReceiverInterface> participantStubs;  // Remote stubs for all players
        public int assignedId;                      // Lamport clock ID assigned to the joining peer
        public int hostCurrentLamport;              // Host's current Lamport time (to help sync clocks)
        // (Additional game data like puzzle grid or scores can be included as needed)
    }
    
    /**
     * Message is a serializable container for game events that are broadcast between peers.
     * Each Message includes the type of event, an optional content payload, and metadata for ordering:
     * the Lamport timestamp and sender's ID (and name for reference).
     */
    public static class Message implements Serializable {
        private static final long serialVersionUID = 1L;
        public enum Type { JOIN, START, GUESS, PUZZLE, TEXT  }
        public Type type;              // Type of the message/event
        public String content;        // Event details (e.g., guess word, or joining player name)
        public ReceiverInterface newPeerStub; // (Optional) used for JOIN messages to carry the new peer's stub
        public String senderName;     // Name of the peer who sent the message
        public int senderId;          // Lamport ID of the sender peer
        public int timestamp;         // Lamport timestamp of the event (assigned by sender)
        
        public Message(Type type, String content) {
            this.type = type;
            this.content = content;
        }
        
        @Override
        public String toString() {
            if (type == Type.GUESS) {
                return "[Guess] " + senderName + " guessed \"" + content + "\"";
            } else if (type == Type.JOIN) {
                return "[Join] " + content + " has joined the game";
            } else if (type == Type.START) {
                return "[Start] Game is starting (difficulty: " + content + ")";
            }
            return "[Unknown Message]";
        }
    }
    
    /**
     * Implementation of the ReceiverInterface.
     * This remote object receives messages from peers and applies them to the local game state in Lamport order.
     * It also handles joinGame requests from peers when this peer is the host of a pending game.
     */
    private class ReceiverImpl extends UnicastRemoteObject implements ReceiverInterface {
        private static final long serialVersionUID = 1L;
        // A priority queue (min-heap) to hold received messages until they can be delivered in total order
        private final PriorityQueue<Message> holdBackQueue;
        
        protected ReceiverImpl() throws RemoteException {
            // Initialize a priority queue sorted by Lamport timestamp, then by sender ID (for tie-break)
            super();  // export this remote object
            this.holdBackQueue = new PriorityQueue<>(50, new Comparator<Message>() {
                public int compare(Message m1, Message m2) {
                    if (m1.timestamp != m2.timestamp) {
                        return Integer.compare(m1.timestamp, m2.timestamp);
                    }
                    // If timestamps are equal, compare by sender ID to break ties (ensures a total order)
                    return Integer.compare(m1.senderId, m2.senderId);
                }
            });
        }
        
        /**
         * Remote method for peers to call when broadcasting a game message (guess, join-notification, etc.).
         * This method updates the local Lamport clock, enqueues the message, and then delivers any messages
         * that are next in order (according to Lamport timestamps and tie-break on peer ID).
         * The synchronized keyword ensures FIFO delivery order for messages from a single sender and avoids race conditions.
         */
        @Override
        public synchronized void receiveMessage(Message message) throws RemoteException {
            // Update local Lamport clock with the timestamp of the received message
            int prevTime = lamportClock.getTime();
            lamportClock.update(message.timestamp);
            int newTime = lamportClock.getTime();
            // Log receipt for debugging (could be removed or adjusted as needed)
            System.out.println("<< Received " + message.type + " message from " + message.senderName +
                               " (timestamp=" + message.timestamp + ", localClock was " + prevTime + " -> now " + newTime + ")");
            // Enqueue the message in the hold-back queue for ordering
            holdBackQueue.offer(message);
            // Attempt to deliver all messages that are in order (FIFO-total order delivery)
            deliverAvailableMessages();
        }
        
        /**
         * Remote method for a peer to join a game hosted by this peer.
         * Only valid if this peer is the host and the game is still pending (not yet started).
         * The new peer's receiver stub is added to the participants list and the current game state is returned.
         */
        @Override
        public synchronized GameState joinGame(String newPeerName, ReceiverInterface newPeerStub) throws RemoteException {
            // Allow join only if this peer is hosting and there is space
            if (!isHost) {
                throw new RemoteException("Cannot join: This peer is not hosting a game.");
            }
            if (participantNames.contains(newPeerName)) {
                throw new RemoteException("Peer name " + newPeerName + " is already in the game.");
            }
            if (participantNames.size() >= targetPlayers) {
                throw new RemoteException("Cannot join: no pending game hosted here.");
            }
            
            // Add the new player to the game room
            participantNames.add(newPeerName);
            participantStubs.add(newPeerStub);
            int newPeerId = participantNames.size();  // Assign Lamport ID (1-based)
            System.out.println("** New peer joined: " + newPeerName + " (assigned ID=" + newPeerId + ")");
            
            // Add player to GameRoom as well
            if (isHost) {
                currentGameRoom.addPlayer(newPeerName, null); // No ClientCallback in PeerProcess
            }
            
            // Prepare game state to send back to the joining peer
            GameState state = new GameState();
            state.gameId = currentGameRoom.gameId;
            state.difficulty = gameDifficulty;
            state.targetPlayers = targetPlayers;
            state.started = false; // Game is still pending
            state.participants = new ArrayList<>(participantNames);
            state.participantStubs = new ArrayList<>(participantStubs);
            state.assignedId = newPeerId;
            state.hostCurrentLamport = lamportClock.getTime();
            
            // Broadcast join notification to all peers
            Message joinMsg = new Message(Message.Type.JOIN, newPeerName);
            joinMsg.newPeerStub = newPeerStub;
            joinMsg.senderName = peerName;
            joinMsg.senderId = 1;  // Host's ID
            try {
                int sendTs = lamportClock.tick();
                joinMsg.timestamp = sendTs;
            } catch (RemoteException e) {
                joinMsg.timestamp = lamportClockFallbackTick();
            }
            broadcastMessageToAll(joinMsg);
            
            // If the room is now full, mark the game as started and initialize the puzzle
            if (participantNames.size() == targetPlayers) {
                gameStarted = true;
                
             // Set the hostPeer to the actual running host (the enclosing PeerProcess instance)
                currentGameRoom.setHostPeer(PeerProcess.this);
                System.out.println("** Game room is full. Starting game...");
                
                // Broadcast START message
                Message startMsg = new Message(Message.Type.START, gameDifficulty);
                startMsg.senderName = peerName;
                startMsg.senderId = 1;
                try {
                    int startTs = lamportClock.tick();
                    startMsg.timestamp = startTs;
                } catch (RemoteException e) {
                    startMsg.timestamp = lamportClockFallbackTick();
                }
                broadcastMessageToAll(startMsg);
                
                // Initialize the puzzle server and generate the initial puzzle view
                int level = 0;
                try {
                    level = Integer.parseInt(gameDifficulty) + participantNames.size();
                } catch (NumberFormatException nfe) {
                    level = participantNames.size(); // Fallback if difficulty is not numeric
                }
                WordRepositoryServer wordServer = null;
                try {
                    wordServer = (WordRepositoryServer) Naming.lookup("rmi://localhost:1099/WordRepositoryServer");
                } catch (Exception ex) {
                    System.err.println("Failed to lookup WordRepositoryServer: " + ex.getMessage());
                }
                
                // **Added: Initialize puzzleServer in GameRoom**
                if (wordServer != null) {
                    currentGameRoom.initializePuzzle(wordServer);
                } else {
                    System.err.println("Cannot initialize puzzle: WordRepositoryServer is null.");
                }
                
                puzzleServer = new Mutiplayer_Puzzle(participantNames.size(), level, wordServer);
                String puzzleView = currentGameRoom.getPuzzleServer().render_player_view_puzzle();
                String solvedPuzzleView = currentGameRoom.getPuzzleServer().render_puzzle_solution();
                
                // Update the server-side puzzle state via RMI
                try {
                    CrissCrossPuzzleServer gameServer = (CrissCrossPuzzleServer) Naming.lookup("rmi://localhost:1099/GameServer");
                    gameServer.updateRevealedPuzzle(solvedPuzzleView);
                    System.out.println("** Initial puzzle sent to server for display.");
                } catch (Exception e) {
                    System.err.println("Failed to update revealed puzzle on server: " + e.getMessage());
                }
                
                // Broadcast the initial puzzle view to all peers
                Message puzzleMsg = new Message(Message.Type.PUZZLE, puzzleView);
                puzzleMsg.senderName = peerName;
                puzzleMsg.senderId = 1;
                try {
                    int puzzleTs = lamportClock.tick();
                    puzzleMsg.timestamp = puzzleTs;
                } catch (RemoteException e) {
                    puzzleMsg.timestamp = lamportClockFallbackTick();
                }
                broadcastMessageToAll(puzzleMsg);
                
                state.started = true;
            }
            
            return state;
        }
        
        /**
         * Helper method to deliver any messages from the hold-back queue that are ready in Lamport total order.
         * It checks the smallest timestamp message and delivers it if no other message in the queue has a smaller 
         * timestamp (or equal timestamp with a smaller sender ID). This ensures messages are delivered in a 
         * globally consistent order across all peers.
         */
        private void deliverAvailableMessages() {
            // Continuously deliver from the head of the queue while the head is the next in order
            while (!holdBackQueue.isEmpty()) {
                Message head = holdBackQueue.peek();
                // Check if this message is deliverable: no other message in queue has a smaller (timestamp,ID)
                boolean deliverable = true;
                for (Message m : holdBackQueue) {
                    if (m.timestamp < head.timestamp || 
                       (m.timestamp == head.timestamp && m.senderId < head.senderId)) {
                        // Found a message that should be delivered before the head, so head must wait
                        deliverable = false;
                        break;
                    }
                }
                if (!deliverable) {
                    break;  // can't deliver head yet, exit loop and wait for more messages to arrive
                }
                // Remove the message from queue and apply its effects to local game state
                holdBackQueue.poll();
                try {
					applyMessage(head);
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
            }
        }
        
        /**
         * Applies a delivered message to the local game state. This updates the game room information or puzzle state 
         * in response to the event, ensuring the local state stays consistent with the rest of the peers.
         * @param message the Message to apply (already determined to be in-order).
         * @throws RemoteException 
         */
        private void applyMessage(Message message) throws RemoteException {
            switch (message.type) {
            case JOIN:
                // A new peer has joined: update local participants list and stubs.
                String newPlayer = message.content;
                ReceiverInterface newStub = message.newPeerStub;
                if (!participantNames.contains(newPlayer)) {
                    participantNames.add(newPlayer);
                    participantStubs.add(newStub);
                    System.out.println(">> " + newPlayer + " joined the game (added to local state).");
                }
                break;
            case START:
                // The host has signaled the game start: update state and notify user.
                gameStarted = true;
                System.out.println(">> Game has started! Difficulty: " + message.content);
                break;
            case GUESS:
                if (currentGameRoom != null) {
                    if (message.senderName.equals(peerName)) {
                        // **NEW:** This peer was the origin of the guess, so it has already processed it locally.
                        // Avoid processing it again to prevent duplicate broadcasts or state updates.
                        System.out.println(">> You guessed: " + message.content);
                    } else {
                        // **NEW:** Another peer's guess. Process it locally to update state, but do NOT broadcast (already handled by origin).
                        System.out.println(">> " + message.senderName + " guessed: " + message.content);
                        try {
                            // Temporarily disable broadcasting to prevent duplicate outcome messages
                            PeerProcess peerRef = currentGameRoom.getHostPeer();  // reference to local PeerProcess
                            currentGameRoom.setHostPeer(null);
                            currentGameRoom.processGuess(message.content, message.senderName);
                            // Restore the PeerProcess reference
                            currentGameRoom.setHostPeer(peerRef);
                        } catch (Exception e) {
                            System.err.println("Error applying guess from " + message.senderName + ": " + e.getMessage());
                        }
                    }
                }
                break;
            case PUZZLE:
                // --- Modified PUZZLE handling ---
                System.out.println("PUZZLE msg received: " + message.content + " (TS=" + message.timestamp + ")");
                // Synchronize to ensure thread-safe update of the puzzle state
                synchronized(PeerProcess.this) {
                    localPuzzleState = message.content;   // Update the local puzzle state
                    System.out.println("Local puzzle state updated to: " + localPuzzleState);
                    displayPuzzle();                        // Refresh the UI (console output)
                }
                if (message.content.contains("Game over")) {
                    gameStarted = false;
                    System.out.println(">> Game has ended.");
                }
                break;
            case TEXT:
                // Display general text messages (e.g., guess results)
                System.out.println(">> " + message.content);
                break;
            }
        }
    }
    
    
    /**
     * Constructor for PeerProcess. It sets up RMI registry, initializes the Lamport clock and Receiver,
     * and binds them in the RMI registry so other peers can connect. It also starts the user interface loop.
     * @param name the unique name/identifier for this peer (used for RMI lookup by others).
     * @throws RemoteException if RMI setup fails.
     */
    public PeerProcess(String name) throws RemoteException {
        this.peerName = name;
        this.multiplayer = new Multiplayer();
        
        // Start or connect to local RMI registry on port 1099
        try {
            LocateRegistry.createRegistry(1099);
            System.out.println("RMI registry started on port 1099.");
        } catch (RemoteException e) {
            // Registry already running
        }
        
        // Initialize Lamport clock and Receiver remote objects
        lamportClock = new LamportClockImpl(0);  // give a temporary ID 0, will set real ID when known
        receiver = new ReceiverImpl();
        
        // Bind the Lamport clock and Receiver in the RMI registry with unique names
        // Other peers will use these names to lookup the remote objects.
        try {
            Naming.rebind(peerName + "_clock", lamportClock);
            Naming.rebind(peerName, receiver);
            System.out.println(">> Bound RMI objects: '" + peerName + "' (Receiver), '" + peerName + "_clock' (LamportClock)");
        } catch (Exception e) {
            System.err.println("RMI binding error: " + e.getMessage());
            e.printStackTrace();
            throw new RemoteException("Failed to bind RMI objects for peer " + peerName);
        }
        
        // Start the command-line interface loop for user commands (p2pcheck, p2ppropose, p2pjoin, etc.)
        startCommandInterface();
    }
    
    /**
     * Begins the interactive command-line interface, allowing the user to enter p2p commands.
     * Commands:
     *  - p2pcheck              (check current game state: no game, pending, or running)
     *  - p2ppropose <d> <n>    (propose a new game with difficulty d and expecting n players)
     *  - p2pjoin <hostName>    (join a pending game hosted by the peer identified by hostName)
     *  - p2pguess <word>       (make a guess in the running game, broadcast to all peers)
     *  - quit                  (exit the application)
     */
    private void startCommandInterface() {
    	// Fancier menu header using ANSI escape codes for color (if supported)
        System.out.println("\u001B[34m=============================================\u001B[0m");
        System.out.println("\u001B[32m          Welcome to the P2P Game Menu        \u001B[0m");
        System.out.println("\u001B[34m=============================================\u001B[0m");
        System.out.println("Enter one of the following commands:");
        System.out.println("  \u001B[33mp2pcheck\u001B[0m    - Check current game state");
        System.out.println("  \u001B[33mp2ppropose <difficulty> <numPlayers>\u001B[0m - Propose a new game");
        System.out.println("  \u001B[33mp2pjoin <hostName>\u001B[0m     - Join a pending game hosted by a peer");
        System.out.println("  \u001B[33mp2pguess <word>\u001B[0m   - Make a guess in the running game");
        System.out.println("  \u001B[33mquit\u001B[0m         - Exit the application");
        Scanner scanner = new Scanner(System.in);
        while (true) {
        	System.out.print("\u001B[36m" + peerName + " > \u001B[0m");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;
            String[] tokens = input.split("\\s+");
            String command = tokens[0];
            try {
                if ("p2pcheck".equalsIgnoreCase(command)) {
                    doP2PCheck();
                } else if ("p2ppropose".equalsIgnoreCase(command)) {
                    if (tokens.length != 3) {
                        System.out.println("Usage: p2ppropose <difficulty> <numPlayers>");
                    } else {
                        String difficulty = tokens[1];
                        int numPlayers = Integer.parseInt(tokens[2]);
                        doP2PPropose(difficulty, numPlayers);
                    }
                } else if ("p2pjoin".equalsIgnoreCase(command)) {
                    if (tokens.length != 2) {
                        System.out.println("Usage: p2pjoin <hostName>");
                    } else {
                        String hostName = tokens[1];
                        doP2PJoin(hostName);
                    }
                } else if ("p2pguess".equalsIgnoreCase(command)) {
                    if (tokens.length < 2) {
                        System.out.println("Usage: p2pguess <word_or_letter>");
                        continue;
                    }
                    String guess = input.substring(input.indexOf(" ") + 1).trim();
                    if (guess.length() == 1 && !Character.isLetter(guess.charAt(0))) {
                        System.out.println("Invalid guess: please enter a valid letter.");
                        continue;
                    }
                    try {
                        doP2PGuess(guess);
                    } catch (RemoteException e) {
                        System.err.println("Error submitting guess: " + e.getMessage());
                    }
                } else if ("quit".equalsIgnoreCase(command) || "exit".equalsIgnoreCase(command)) {
                    System.out.println("Exiting PeerProcess...");
                    break;
                } else {
                    System.out.println("Unknown command. Available commands: p2pcheck, p2ppropose, p2pjoin, p2pguess, quit");
                }
            } catch (Exception e) {
                // Catch any exceptions from command execution (RemoteException, NumberFormatException, etc.)
                System.err.println("Error executing command: " + e.getMessage());
            }
        }
        scanner.close();
        // Clean up RMI objects before exiting (optional in this context)
        try {
            Naming.unbind(peerName);
            Naming.unbind(peerName + "_clock");
        } catch (Exception ignore) {}
        System.exit(0);
    }
    
    /** Handler for the `p2pcheck` command: displays the current game state (no game, pending, or running). */
    private void doP2PCheck() {
        if (participantNames.isEmpty()) {
            System.out.println("No game present.");
        } else if (gameStarted) {
            System.out.println("Game running: Difficulty \"" + gameDifficulty + "\", Players = " + participantNames);
        } else {
            System.out.println("Game pending: Difficulty \"" + gameDifficulty + "\", Joined " 
                + participantNames.size() + "/" + targetPlayers + " players (" + participantNames + ")");
        }
    }
    
    /**
     * Handler for the `p2ppropose <difficulty> <numPlayers>` command.
     * Initiates a new game as the host, if no game is currently active. Sets the game difficulty and expected number of players.
     */
    private void doP2PPropose(String difficulty, int numPlayers) throws RemoteException {
        if (!participantNames.isEmpty() && !gameStarted) {
            System.out.println("Cannot propose a new game: A game is already pending or running.");
            return;
        }
        // Initialize local state for hosting
        this.isHost = true;
        this.gameDifficulty = difficulty;
        this.targetPlayers = numPlayers;
        this.gameStarted = false;
        participantNames.clear();
        participantStubs.clear();
        participantNames.add(peerName);
        // Set your own Lamport clock ID as 1
        ((LamportClockImpl)lamportClock).setId(1);
        
        System.out.println("** Proposed new game with difficulty \"" + difficulty + "\" for " + numPlayers + " players. (You are the host)");
        System.out.println("** Waiting for players to join...");

        // Create a new GameRoom instance and assign it to currentGameRoom.
        // Here we generate a simple gameId using a random value for demonstration.
        int gameId = new Random().nextInt(1000) + 1000; 
        // Parse difficulty to an int for the game level (adjust as needed)
        int gameLevel = 0;
        try {
            gameLevel = Integer.parseInt(difficulty);
        } catch (NumberFormatException e) {
            System.out.println("Difficulty must be numeric. Using default level 1.");
            gameLevel = 1;
        }
        gameId = multiplayer.createGame(peerName, numPlayers, gameLevel, this);
        this.currentGameRoom = multiplayer.getGameRoom(gameId);
        currentGameRoom.addPlayer(peerName, null);
    }
    
    /**
     * Handler for the `p2pjoin <hostName>` command.
     * Allows this peer to join an existing pending game hosted by another peer.
     * Connects to the host via RMI, invokes joinGame, and syncs local state with the returned game state.
     */
    private void doP2PJoin(String hostName) throws Exception {
        if (!participantNames.isEmpty() && !gameStarted) {
            System.out.println("Cannot join a new game: You are already in a pending or running game.");
            return;
        }
        // Assume hostName is on localhost if not fully qualified
        String lookupHost = hostName.contains(".") ? hostName : "localhost";
        String hostUrl = "rmi://" + lookupHost + "/" + hostName;
        ReceiverInterface hostReceiver = (ReceiverInterface) Naming.lookup(hostUrl);
        System.out.println("** Found host peer: " + hostName + ". Attempting to join their game...");
        
        // Call the host's joinGame method remotely.
        GameState state = hostReceiver.joinGame(peerName, this.receiver);
        
        // Instead of rejecting when the game has started, update local state:
        this.isHost = false;
        this.gameDifficulty = state.difficulty;
        this.targetPlayers = state.targetPlayers;
        this.gameStarted = state.started;
        this.participantNames = new ArrayList<>(state.participants);
        this.participantStubs = new ArrayList<>(state.participantStubs);
        // Remove our own entry if present.
        int selfIndex = participantNames.indexOf(peerName);
        if (selfIndex >= 0) {
            participantNames.remove(selfIndex);
            if (participantStubs.size() > selfIndex) {
                participantStubs.remove(selfIndex);
            }
        }
        ((LamportClockImpl) lamportClock).setId(state.assignedId);
        lamportClock.update(state.hostCurrentLamport);
        System.out.println("** Joined game hosted by " + hostName + ". Difficulty: \"" + gameDifficulty 
            + "\", Participants: " + participantNames);
        System.out.println("** Game is pending, waiting for more players (" 
            + participantNames.size() + "/" + targetPlayers + " joined).");
        
        // Retrieve the current GameRoom from Multiplayer.
        // (Assuming Multiplayer is a singleton or is accessible.)
        this.currentGameRoom = multiplayer.getGameRoom(state.gameId);
    }
    
    /**
     * Handler for the `p2pguess <word>` command.
     * Broadcasts a guess (a word or letter in the puzzle) to all peers in the game.
     * This will be delivered in FIFO-total order across the distributed game.
     */
    private void doP2PGuess(String guess) throws RemoteException {
        if (participantNames.isEmpty()) {
            System.out.println("You are not in a game. Join or propose a game first.");
            return;
        }
        if (!gameStarted) {
            System.out.println("Game has not started yet. Cannot make guesses until the game is running.");
            return;
        }
        String guessContent = guess.trim();
        // Create and prepare the GUESS message
        Message guessMsg = new Message(Message.Type.GUESS, guessContent);
        guessMsg.senderName = peerName;
        guessMsg.senderId = ((LamportClockImpl) lamportClock).getId();
        try {
            int ts = lamportClock.tick();             // Lamport: increment for the guess event
            guessMsg.timestamp = ts;
        } catch (RemoteException e) {
            guessMsg.timestamp = lamportClockFallbackTick();
        }
        System.out.println("** Broadcasting guess \"" + guessContent + "\" to all peers...");
        broadcastMessageToAll(guessMsg);
        
        // **NEW:** Immediately process the guess locally if this peer is in the game.
        // This updates the puzzle and (if correct) broadcasts outcome from this peer.
        if (currentGameRoom != null) {
            try {
                currentGameRoom.processGuess(guessContent, peerName);
            } catch (Exception e) {
                System.err.println("Error processing local guess: " + e.getMessage());
            }
        }
        // Note: The guess message will still be delivered to all (including self) via the Receiver, 
        // but we handle our own guess in advance to avoid waiting for host.
    }
    /**
     * Broadcasts a message to all participants in the game (all other peers).
     * It sends the message via RMI to each peer's Receiver, and also enqueues it locally for this peer's Receiver to process.
     * This ensures the message is delivered to everyone, including the sender, in the Lamport total order.
     * @param message the Message to broadcast.
     */
    void broadcastMessageToAll(Message message) {
        // Send the message to each remote peer (skip our own receiver)
        for (ReceiverInterface stub : participantStubs) {
            try {
                // Use a comparison based on a unique identifier (here we compare string representations)
                if (stub.toString().equals(receiver.toString())) {
                    continue; // Skip sending to ourselves
                }
                stub.receiveMessage(message);
            } catch (RemoteException e) {
                System.err.println("Failed to send message to a peer: " + e.getMessage());
            }
        }
        // Also deliver the message locally (loopback)
        try {
            receiver.receiveMessage(message);
        } catch (RemoteException e) {
            System.err.println("Local message delivery error: " + e.getMessage());
        }
    }
    
    /**
     * Fallback method to increment Lamport clock if the remote interface call fails.
     * This should rarely be needed since we call tick() on a local LamportClockImpl, but it's here for completeness.
     * @return the incremented Lamport timestamp.
     */
    private synchronized int lamportClockFallbackTick() {
        try {
            // If lamportClock is still accessible remotely, call it
            return lamportClock.tick();
        } catch (RemoteException rex) {
            // As a last resort, manually increment the internal clock value
            if (lamportClock instanceof LamportClockImpl) {
                LamportClockImpl impl = (LamportClockImpl) lamportClock;
                // Directly increment the internal clockValue (bypassing RMI)
                try {
                    // Use reflection to access the clock (not ideal, but demonstration of fallback)
                    java.lang.reflect.Field f = LamportClockImpl.class.getDeclaredField("clockValue");
                    f.setAccessible(true);
                    int current = f.getInt(impl);
                    int newVal = current + 1;
                    f.setInt(impl, newVal);
                    return newVal;
                } catch (Exception ignore) {
                    // If reflection fails, just set a large dummy timestamp to avoid ordering conflicts
                    return Integer.MAX_VALUE;
                }
            } else {
                // If lamportClock is not our impl (should not happen), return a dummy value
                return Integer.MAX_VALUE;
            }
        }
    }
   
    /**
     * Helper method to refresh or display the current puzzle state.
     * In a console-based UI, this simply prints the state.
     * For a GUI, you might instead call repaint() or updateUI().
     */
    private void displayPuzzle() {
        System.out.println("Current puzzle: " + localPuzzleState);
    }

    
    /** Main method to launch the peer process. Accepts an optional argument for peer name; otherwise prompts for it. */
    public static void main(String[] args) {
        try {
            String name = (args.length > 0) ? args[0] : null;
            if (name == null || name.trim().isEmpty()) {
                System.out.print("Enter a unique name for this peer: ");
                Scanner sc = new Scanner(System.in);
                name = sc.nextLine().trim();
                sc.close();
            }
            // Create and run the peer process
            new PeerProcess(name);
        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
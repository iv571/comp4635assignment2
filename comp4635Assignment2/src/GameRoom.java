import java.rmi.Naming;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Iterator;
import java.util.Random;


public class GameRoom {
    int gameId;
    private int numPlayers;
    private int gameLevel;
    private boolean isStarted;
    private boolean isRun;
    private boolean isFinished;
    private String host;
    private List<Player> players;
    private Map<String, ClientCallback> playerCallbacks;
    private Map<String, Boolean> activePlayers; // player name
    private Map<String, FailureDetector> failureDetector;
    private Map<String, Integer> playerID;
    private Map<String, LamportClock> playerClocks; // player name and clock
    private List<String> guessedWords = new ArrayList<>(); // Persistent list of guessed words
    private LamportClock lamportClock;
    private int currentTurnIndex = 0;
    private Mutiplayer_Puzzle puzzleServer;
    private CrissCrossPuzzleServer gameServer;
    PeerProcess.Message msg;
 // NEW: Store a reference to the actual host's PeerProcess instance
    private PeerProcess hostPeer;
 // Map to store node ID to player name associations
    private Map<Integer, String> nodeIdToPlayerName = new HashMap<>();

    public GameRoom(int gameId, int numPlayers, int gameLevel, String host, PeerProcess hostPeer) {
        this.gameId = gameId;
        this.numPlayers = numPlayers;
        this.gameLevel = gameLevel;
        this.isStarted = false;
        this.isRun = false;
        this.isFinished = false;
        this.host = host;
        this.hostPeer = hostPeer;
        this.playerID = new HashMap<>();
        this.playerClocks = new HashMap<>();
        this.players = new ArrayList<>();
        this.playerCallbacks = new HashMap<>();
        this.activePlayers = new HashMap<>();
        this.failureDetector = new HashMap<>();
        
     // Look up the GameServer and store its reference
        try {
            gameServer = (CrissCrossPuzzleServer) Naming.lookup("rmi://localhost:1099/GameServer");
        } catch (Exception e) {
            System.err.println("Error looking up GameServer: " + e.getMessage());
        }

    }
    
    // Setter method for the host peer
    public void setHostPeer(PeerProcess hostPeer) {
        this.hostPeer = hostPeer;
    }
    

    public boolean addPlayer(String playerName, ClientCallback callback) throws RemoteException {
        Random rand = new Random();
        if (players.size() < numPlayers) {
            Player player = new Player(playerName);
            players.add(player);
            playerCallbacks.put(playerName, callback);

            // Notes for Stanley: Set up the clock for each player
            int id = rand.nextInt(100);
            playerID.put(playerName, id);
            playerClocks.put(playerName, new LamportClock(id)); // player's size is the id
            // set peers
            List<LamportClock> allClocks = new ArrayList<>(playerClocks.values());
            for (LamportClock c : allClocks) {
                c.setPeers(allClocks);
            }
            return true;
        }
        return false;
    }
    
    // Method to retrieve a player's name by their node ID
    public String getPlayerName(int nodeId) {
        return nodeIdToPlayerName.get(nodeId);
    }

    public String startGame(String hostName) {
        StringBuilder response = new StringBuilder();

        if (hostName == null || host == null) {
            response.append("Invalid host name.\n");
            return response.toString();
        }

        if (isStarted && hostName.equals(host)) {
            response.append("You already started the game!\n");
        } else if (!hostName.equals(host)) {
            response.append("Only host can start the game!\n");
        } else if (!isStarted && hostName.equals(host)) {
            isStarted = true;
            activePlayers.put(host, true);
            response.append("You have successfully started the game room.\n");
            broadcastMessage("Host <" + hostName
                    + "> has started the game: \n"
                    + "1.Type 'ready <game id>' if you are ready\n"
                    + "2.Type 'leave <game id>' if you want to quit the room\n"
                    + "NOTE:\n- Once host runs the game, you will be removed from the game room if you are not ready\n"
                    + "- Once you are ready, you cannot the quit the game\n");
        }

        return response.toString();
    }

    public synchronized String runGame(String player, WordRepositoryServer wordServer) {
        StringBuilder response = new StringBuilder();
        if (!player.equals(host)) {
            response.append("Only the host can run the game!\n");
            return response.toString();
        }

        // set up the game
        isRun = true;
        warningRunGame();
        getCurrentActivePlayers();
        shufflePlayers();

        puzzleServer = new Mutiplayer_Puzzle(players.size(), gameLevel + players.size(), wordServer);
        puzzleServer.print_solution_puzzle();

        String result = startTurns();
        response.append(result);

        endGame();

        return response.toString();
    }

    private void warningRunGame() {
        broadcastMessage("Host has run the game - Initializing the game...\n"
                + "Inactive player(s) will be removed from the game room\n");
    }

    private void endGame() {
        isStarted = false;
        isRun = false;
        isFinished = true;
        broadcastMessage("Game is terminated\n");
    }

    private int check_curr_player_state(String player_name) {

        FailureDetector detector = failureDetector.get(player_name);

        ClientCallback callback = playerCallbacks.get(player_name);

        if (detector == null) {
            detector = CrissCrossImpl.loadConfigAndInitializeFailureDetector(callback);
            detector.registerClient(player_name);
            failureDetector.put(player_name, detector);

        }
        FailureDetector.ClientState currentState = detector.getClientState(player_name);

        switch (currentState) {
            case ALIVE:
                return 1;

            case SUSPECTED:
                return 0;

            case FAILED:
                return -1;

            default:
                System.out.println("Unknown client state.");
                return -99;
        }

    }

    public void submitGuess(String playerName, String word) throws RemoteException {
        if (puzzleServer == null) {
            System.out.println("Game has not started yet. Cannot accept guesses.");
            return;
        }
        if (puzzleServer.is_All_words_are_guessed())
            return;

        LamportClock clock = playerClocks.get(playerName);
        if (clock != null) {
            clock.send(word, this);
        }
    }

    public synchronized void processGuess(String word, String senderName) throws RemoteException {
        if (puzzleServer == null) {
            System.err.println("Puzzle server is not initialized yet. Ignoring guess: " + word);
            return;
        }
        Player currPlayer = getPlayerByName(senderName);
        if (currPlayer == null) {
            System.err.println("Player not found: " + senderName);
            return;
        }
        if (!guessedWords.contains(word)) {
            if (puzzleServer.is_guessed_word_correct(word)) {
                System.out.println("Guess correct: " + word);
                guessedWords.add(word);
                currPlayer.increaseScore();
                // Broadcast a TEXT message announcing the correct guess
                broadcastMessage(senderName + " guessed correctly: " + word);
                // Prepare and broadcast updated puzzle state
                String updatedView = puzzleServer.render_player_view_puzzle();
                PeerProcess.Message puzzleMsg = new PeerProcess.Message(PeerProcess.Message.Type.PUZZLE, updatedView);
                // **MODIFIED:** Use local peer's identity for the message
                if (hostPeer != null) {
                    puzzleMsg.senderName = senderName;  // label with the guesser's name (origin)
                    puzzleMsg.senderId = hostPeer.lamportClock.getId();
                    try {
                        int ts = hostPeer.lamportClock.tick();
                        puzzleMsg.timestamp = ts;
                    } catch (Exception e) {
                        puzzleMsg.timestamp = new Random().nextInt(1000);
                    }
                    hostPeer.broadcastMessageToAll(puzzleMsg);
                } else {
                    // No hostPeer (probably processing a peer guess on a non-origin node), so skip broadcast
                    System.out.println(">> Puzzle state updated locally for guess '" + word + "' (no broadcast).");
                }
                // Update central server puzzle state if applicable
                if (gameServer != null) {
                    gameServer.updateRevealedPuzzle(updatedView);
                }
                if (puzzleServer.is_All_words_are_guessed()) {
                    broadcastMessage("Game over! Puzzle solved.");
                    isFinished = true;
                }
            } else {
                System.out.println("Guess incorrect: " + word);
                currPlayer.decrementFailAttempt();
                broadcastMessage(senderName + " guessed wrong: " + word);
            }
        } else {
            // Word was already guessed
            currPlayer.decrementFailAttempt();
            broadcastMessage(senderName + " guessed a duplicate: " + word);
        }
    }
    
    
    // Helper method to broadcast messages via PeerProcess
    private void broadcastMessageToAll(PeerProcess.Message message) {
        try {
            PeerProcess peer = new PeerProcess(host); // This assumes host can access its own PeerProcess instance
            peer.broadcastMessageToAll(message);
        } catch (RemoteException e) {
            System.err.println("Broadcast failed: " + e.getMessage());
        }
    }

    private Player getPlayerByName(String name) {
        for (Player player : players) {
            if (player.getName().equals(name)) {
                return player;
            }
        }
        return null; // Not found
    }

    private String getPlayerNameById(int id) {
        for (Map.Entry<String, Integer> entry : playerID.entrySet()) {
            if (entry.getValue() == id) {
                return entry.getKey(); // playerName
            }
        }
        return null; // not found
    }

    private String startTurns() {
        boolean singlePlayerCase = false;
        Player winner = null;
        StringBuilder response = new StringBuilder();
        List<String> addedWord = new ArrayList<>();

        while (!puzzleServer.is_All_words_are_guessed()) {
            // Count active players
            int activePlayers = 0;
            for (Player p : players) {
                if (p.getCurrentFailAttempt() > 0) {
                    activePlayers++;
                    winner = p; // Last-standing player if only one remains
                }
            }

            // If only one player is left, declare them the winner
            if (activePlayers == 1) {
                singlePlayerCase = true;
                broadcastMessage("Game over! " + winner.getName() + " is the winner!");
                break;
            }

            // Find the next available player
            int attempts = players.size(); // Prevent infinite loops if all players are out
            while (players.get(currentTurnIndex).getCurrentFailAttempt() == 0 && attempts > 0) {
                broadcastMessage(
                        players.get(currentTurnIndex).getName() + " has no remaining fail attempts and is skipped.");
                currentTurnIndex = (currentTurnIndex + 1) % players.size();
                attempts--;
            }

            // Safety check: If no valid players exist, end the game
            if (attempts == 0) {
                broadcastMessage("No active players left. Ending game.");
                break;
            }

            Player currentPlayer = players.get(currentTurnIndex);
            String currentPlayerName = currentPlayer.getName();

            // int active = check_curr_player_state (currentPlayerName);
            /*
             * if (active == -1) {
             * // If the player is inactive (active == -1), remove them from the game
             * broadcastMessage(currentPlayerName + " is inactive and has been removed.");
             * removePlayer(currentPlayerName);
             * // Move to the next player
             * currentTurnIndex = (currentTurnIndex + 1) % players.size();
             * continue; // Skip the rest of the loop and give turn to the next player
             * } else if (active == 0) {
             * // If the player is inactive but not removed (active == 0), skip their turn
             * broadcastMessage(currentPlayerName +
             * "'s turn is skipped due to inactivity.");
             * // Move to the next player
             * currentTurnIndex = (currentTurnIndex + 1) % players.size();
             * continue; // Skip the rest of the loop and give turn to the next player
             * }
             */

            broadcastMessage(puzzleServer.render_player_view_puzzle());
            broadcastMessage(currentPlayerName + ", it's your turn! Please type your word.");

            try {
                ClientCallback callback = playerCallbacks.get(currentPlayerName);
                if (callback != null) {
                    if (!callback.isInputBufferEmpty()) {
                        callback.flushInputBuffer();
                    }
                    String playerInput = callback.requestPlayerInput(currentPlayerName);

                    if ("ERROR".equals(playerInput) || "NO_INPUT".equals(playerInput)) {
                        broadcastMessage(currentPlayerName + " did not enter a valid word.");
                    } else {
                        // broadcastMessage(currentPlayerName + " typed: " + playerInput);

                        // ==============================================================================
                        // Step 1: Increment Lamport clock
                        LamportClock clock = playerClocks.get(currentPlayerName);
                        int timestamp = clock.tick();

                        clock.send(playerInput, this);

                        broadcastLamportMessage(currentPlayerName, playerInput, timestamp);
                        // ==============================================================================

                        if (!addedWord.contains(playerInput)) {
                            if (puzzleServer.is_guessed_word_correct(playerInput)) {
                                addedWord.add(playerInput);
                                currentPlayer.increaseScore();
                                broadcastMessage("Player " + currentPlayerName + "'s guess is correct! Add 1 score");
                                broadcastMessage(puzzleServer.render_player_view_puzzle());
                            } else {
                                currentPlayer.decrementFailAttempt();
                                broadcastMessage(
                                        "Player " + currentPlayerName + "'s guess is incorrect! Deduct 1 Fail Attempt");
                            }
                        } else {
                            currentPlayer.decrementFailAttempt();
                            broadcastMessage(
                                    "Player " + currentPlayerName + "'s guess is duplicated! Deduct 1 Fail Attempt");
                        }
                        broadcastMessage(
                                "Player " + currentPlayerName + " - Earned Scores: " + currentPlayer.getScore());
                        broadcastMessage("Player " + currentPlayerName + " - Remaining Fail Attempts: "
                                + currentPlayer.getCurrentFailAttempt() + "\n");
                    }
                } else {
                    broadcastMessage("Player " + currentPlayerName + " is unavailable and has been removed.");
                    removePlayer(currentPlayerName);
                }
            } catch (RemoteException e) {
                broadcastMessage("Error communicating with " + currentPlayerName + ". Removing player...");
                removePlayer(currentPlayerName);
            }

            currentTurnIndex = (currentTurnIndex + 1) % players.size();
        }

        // Determine winner if not decided by single-player elimination
        if (!singlePlayerCase) {
            winner = findWinner();
        } else {
            if (winner.getScore() == 0) {
                winner = null;
            }
        }

        // Ensure winner is valid before broadcasting
        if (winner != null) {
            response.append("WINNER: ").append(winner.getName()).append(" - Total Scores: ").append(winner.getScore())
                    .append("\n");
            broadcastMessage(response.toString());
        } else {
            response.append("No winner. Game ended with no active players.\n");
            broadcastMessage("No winner. Game ended with no active players.");
        }

        return response.toString();
    }

    private Player findWinner() {
        if (players.isEmpty()) {
            return null; // No players available
        }

        Player winner = players.get(0);
        for (Player p : players) {
            if (p.getScore() > winner.getScore()) {
                winner = p;
            } else if (p.getScore() == winner.getScore()) {
                // If scores are the same, compare fail attempts
                if (p.getCurrentFailAttempt() > winner.getCurrentFailAttempt()) {
                    winner = p;
                }
            }
        }
        return winner;
    }

    public synchronized String getCurrentPlayerTurn() {
        if (players.isEmpty()) {
            return null; // No players in the game
        }
        return players.get(currentTurnIndex).getName();
    }

    private void getCurrentActivePlayers() {
        Iterator<Player> iterator = players.iterator();

        while (iterator.hasNext()) {
            Player player = iterator.next();
            String playerName = player.getName();

            if (!activePlayers.containsKey(playerName) || !activePlayers.get(playerName)) {
                iterator.remove();
                playerCallbacks.remove(playerName);
                System.out.println("Removed inactive player: " + playerName);
            }
        }
    }

    public void broadcastMessage(String message) {
        if (hostPeer != null) {
            PeerProcess.Message msg = new PeerProcess.Message(PeerProcess.Message.Type.TEXT, message);
            // **MODIFIED:** Use local peer’s Lamport clock ID for the sender
            msg.senderName = "<" + host + ">";  // (Optional: can use host name or local peer name for display)
            try {
            	msg.senderId = hostPeer.lamportClock.getId();
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
            try {
                int ts = hostPeer.lamportClock.tick();
                msg.timestamp = ts;
            } catch (RemoteException e) {
                msg.timestamp = new Random().nextInt(1000);
            }
            hostPeer.broadcastMessageToAll(msg);
        } else {
            System.err.println("hostPeer is null. Cannot broadcast message: " + message);
        }
    }
    
    private void broadcastLamportMessage(String senderName, String message, int timestamp) {
        for (Map.Entry<String, ClientCallback> entry : playerCallbacks.entrySet()) {
            String targetPlayer = entry.getKey();
            ClientCallback targetCallback = entry.getValue();

            // Skip sending back to the sender
            if (!targetPlayer.equals(senderName)) {
                try {
                    // Visual feedback to target client
                    targetCallback.receiveMessage("[Lamport Msg] " + message +
                            " (TS=" + timestamp + ", From=" + senderName + ")");

                    // Deliver to their LamportClock on server side
                    LamportClock receiverClock = playerClocks.get(targetPlayer);
                    receiverClock.onReceiveMessage(timestamp, playerID.get(senderName), message, this);

                } catch (RemoteException e) {
                    System.out.println("Could not send Lamport message to " + targetPlayer + ": " + e.getMessage());
                }
            }
        }
    }

    private void shufflePlayers() {
        System.out.println("Before Shuffle: \n");
        for (int i = 0; i < players.size(); i++) {
            System.out.println((i + 1) + ". " + players.get(i).getName());
        }

        Collections.shuffle(players);
        broadcastMessage("The players have been shuffled.");

        System.out.println("After Shuffle: \n");
        for (int i = 0; i < players.size(); i++) {
            System.out.println((i + 1) + ". " + players.get(i).getName());
            broadcastMessage((i + 1) + ". " + players.get(i).getName());
        }
    }

    public String setActivePlayer(String player) {
        StringBuilder response = new StringBuilder();
        boolean playerExists = playerExists(player);
        if (!playerExists) {
            response.append("You have not joined this game room\n");
            return response.toString();
        }
        if (!activePlayers.containsKey(player)) {
            isRun = true;
            activePlayers.put(player, true);
            broadcastMessage("Player " + player + " is ready\nWaiting for the host to run the game...\n");
            response.append("You have been marked as ready.\n");
        } else {
            response.append("You are already marked as ready.\n");
        }
        return response.toString();
    }

    public synchronized String leaveRoom(String player) {
        StringBuilder response = new StringBuilder();

        boolean playerExists = playerExists(player);
        if (!playerExists) {
            response.append("You have not joined this game room\n");
            return response.toString();
        }

        if (!activePlayers.containsKey(player)) {
            removePlayer(player);
            activePlayers.remove(player);
            playerCallbacks.remove(player);
            broadcastMessage("Player " + player + " has left the room\n");
        }
        response.append("You have left the room.\n");

        return response.toString();
    }

    public boolean playerExists(String playerName) {
        for (Player p : players) {
            if (p.getName().equals(playerName)) {
                return true;
            }
        }
        return false;
    }

    private synchronized boolean removePlayer(String playerName) {
        return players.removeIf(player -> player.getName().equals(playerName));
    }
    
    public void initializePuzzle(WordRepositoryServer wordServer) {
        if (puzzleServer == null) {
            puzzleServer = new Mutiplayer_Puzzle(players.size(), gameLevel + players.size(), wordServer);
        }
    }

    public boolean isStarted() {
        return this.isStarted;
    }

    public boolean isGameFinished() {
        return this.isFinished;
    }

    public synchronized boolean isGameRun() {
        return this.isRun;
    }

    public int getRemainingSpot() {
        return this.numPlayers - this.players.size();
    }

    public int getPlayerCount() {
        return players.size();
    }

    public int getGameId() {
        return this.gameId;
    }

    public String getHost() {
        return this.host;
    }

    public int getTotalPlayers() {
        return this.numPlayers;
    }
    
    // Getter for puzzleServer (optional, depending on access needs)
    public Mutiplayer_Puzzle getPuzzleServer() {
        return puzzleServer;
    }
    
    public PeerProcess getHostPeer() {
        return hostPeer;
    }
    

    private static class Player {
        private String name;
        private int currentFailedAttempts;
        private static final int TOTAL_FAILED_ATTEMPTS = 5;
        private int score;

        public Player(String name) {
            this.name = name;
            this.currentFailedAttempts = TOTAL_FAILED_ATTEMPTS;
            this.score = 0;
        }

        public String getName() {
            return this.name;
        }

        public void increaseScore() {
            score++;
        }

        public void decrementFailAttempt() {
            currentFailedAttempts -= 1;
        }

        public int getCurrentFailAttempt() {
            return currentFailedAttempts;
        }

        public int getScore() {
            return score;
        }
    }
}
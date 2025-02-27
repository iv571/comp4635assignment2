import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Iterator;

public class GameRoom {
    private int gameId;
    private int numPlayers;
    private int gameLevel;
    private boolean isStarted;
    boolean isRun;
    private String host;
    List<Player> players;
    Map<String, ClientCallback> playerCallbacks;
    private Map<String, Boolean> activePlayers; // player name
    int currentTurnIndex = 0;
    Mutiplayer_Puzzle puzzleServer;
    UserAccountServer accountServer;
    private ClientCallback hostCallback;  // new field


    public GameRoom(int gameId, int numPlayers, int gameLevel, String host, ClientCallback hostCallback) {
    	 if (hostCallback == null) {
    	        throw new IllegalArgumentException("Host callback cannot be null");
    	    }
    	
        this.gameId = gameId;
        this.numPlayers = numPlayers;
        this.gameLevel = gameLevel;
        this.isStarted = false;
        this.isRun = false;
        this.host = host;
        this.players = new ArrayList<>();
        this.playerCallbacks = new HashMap<>();
        this.activePlayers = new HashMap<>();
        this.hostCallback = hostCallback; 
        
        // Initialize accountServer so that score updates work later.
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            accountServer = (UserAccountServer) registry.lookup("UserAccountServer");
        } catch (Exception e) {
            System.err.println("Failed to lookup UserAccountServer: " + e.getMessage());
            e.printStackTrace();
        }
        
     // **Include host as a player immediately**
        Player hostPlayer = new Player(host);
        players.add(hostPlayer);
        playerCallbacks.put(host, hostCallback);
    }

    public boolean addPlayer(String playerName, ClientCallback callback) {
        if (players.size() < numPlayers) {
        	// Prevent duplicate entries
            if (playerExists(playerName)) {
                return false;
            }
            Player player = new Player(playerName);
            players.add(player);
            playerCallbacks.put(playerName, callback);
            return true;
        }
        return false;
    }

    public synchronized String startGame(String hostName) {
        StringBuilder response = new StringBuilder();

        if (hostName == null || host == null) {
            response.append("Invalid host name.\n");
            return response.toString();
        }
        
     // If the host isn't in the players list, add them.
        if (!playerExists(hostName)) {
            // Assuming you have a way to get the host's callback (e.g., stored during creation)
            addPlayer(hostName, hostCallback);
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
                    + "NOTE:\n- Once host runs the game, you will be removed from the game room\n"
                    + "- Once you are ready, you cannot the quit the game\n");
        }

        return response.toString();
    }

    public synchronized String runGame(String player, WordRepositoryServer wordServer) {
        StringBuilder response = new StringBuilder();
        
        // Check if the player is the host
        if (!player.equals(host)) {
            response.append("Only the host can run the game!\n");
            return response.toString();
        }
        
        // Remove players who haven't marked themselves as ready
        removeUnreadyPlayers();
        
        // Ensure the host is the first in the turn order
        ensureHostIsFirst();
        
        // Reset turn state
        currentTurnIndex = 0;
        
        // Set up the game
        isRun = true;
        warningRunGame();
        getCurrentActivePlayers();
        shufflePlayers();
        puzzleServer = new Mutiplayer_Puzzle(players.size(), gameLevel, wordServer);
        broadcastMessage("Puzzle:\n" + puzzleServer.render_player_view_puzzle());
        response.append("Game is running...\n");
        
        // Do not terminate the game here; let it continue until the puzzle is solved
        return response.toString();
    }
   
	private void warningRunGame() {
        broadcastMessage("Host has run the game - Initializing the game...\n"
                + "Inactive player(s) will be removed from the game room\n");
    }

    private String startTurns() {
        while (currentTurnIndex < players.size()) {
            Player currentPlayer = players.get(currentTurnIndex);
            String currentPlayerName = currentPlayer.getName();

            String message = currentPlayerName + ", it's your turn! Please type your word/letter:";
            broadcastMessage(message);

            try {
                ClientCallback callback = playerCallbacks.get(currentPlayerName);
                String playerInput = "";
                // Poll for input for up to 10 seconds.
                long startTime = System.currentTimeMillis();
                long timeout = 100000; // 100 seconds timeout
                while ((playerInput == null || playerInput.trim().isEmpty()) 
                        && (System.currentTimeMillis() - startTime < timeout)) {
                    // Request input from the player's client callback.
                    playerInput = callback.requestPlayerInput(currentPlayerName);
                    if (playerInput != null && !playerInput.trim().isEmpty()) {
                        break;
                    }
                    // Wait a short period before polling again.
                    Thread.sleep(500);
                }
                // If no valid input was received within timeout, assign a default value.
                if (playerInput == null || playerInput.trim().isEmpty()) {
                    playerInput = "NO_INPUT";
                }

                // Process the input.
                if ("ERROR".equals(playerInput) || "NO_INPUT".equals(playerInput)) {
                    broadcastMessage(currentPlayerName + " did not enter a valid word.");
                } else {
                    broadcastMessage(currentPlayerName + " typed: " + playerInput);
                    if (puzzleServer.is_guessed_word_correct(playerInput)) {
                        broadcastMessage("Player " + currentPlayerName + "'s guess is correct!");
                    } else {
                        broadcastMessage("Player " + currentPlayerName + "'s guess is not correct!");
                    }
                }
                // After processing the guess, broadcast the current puzzle state to all clients.
                broadcastMessage("Current Puzzle:\n" + puzzleServer.render_player_view_puzzle());
            } catch (RemoteException e) {
                broadcastMessage("Error communicating with " + currentPlayerName + ". Removing player...");
                removePlayer(currentPlayerName);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                broadcastMessage("Turn interrupted for " + currentPlayerName);
            }
            currentTurnIndex++; // Move to the next player.
        }
        return "End";
    }

    public synchronized String getCurrentPlayerTurn() {
        if (players.isEmpty()) {
            return null; // No players in the game
        }
        return players.get(currentTurnIndex).getName();
    }

    private void getCurrentActivePlayers() {
        // Instead of removing players who are not marked as active,
        // only remove players whose callback is null (indicating a disconnection)
        Iterator<Player> iterator = players.iterator();
        while (iterator.hasNext()) {
            Player player = iterator.next();
            String playerName = player.getName();
            if (!playerCallbacks.containsKey(playerName) || playerCallbacks.get(playerName) == null) {
                iterator.remove();
                System.out.println("Removed disconnected player: " + playerName);
            }
        }
    }
    public void broadcastMessage(String message) {
        for (Map.Entry<String, ClientCallback> entry : playerCallbacks.entrySet()) {
            String playerName = entry.getKey();
            ClientCallback callback = entry.getValue();
            if (callback == null) {
                System.err.println("Callback for player " + playerName + " is null.");
                continue;
            }
            try {
                callback.receiveMessage(message);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Remove players from the game who have not been marked as ready.
     */
    private void removeUnreadyPlayers() {
        Iterator<Player> iterator = players.iterator();
        while (iterator.hasNext()) {
            Player p = iterator.next();
          
         // Do not remove the host even if he isn't marked ready
            if (!p.getName().equals(host) && !activePlayers.containsKey(p.getName())) {
                broadcastMessage("Player " + p.getName() + " removed for not being ready.");
                iterator.remove();
            }
        }
    }

    /**
     * Ensure that the host is at the beginning of the players list.
     */
    private void ensureHostIsFirst() {
        Player hostPlayer = null;
        for (Player p : players) {
            if (p.getName().equals(host)) {
                hostPlayer = p;
                break;
            }
        }
        if (hostPlayer != null) {
            players.remove(hostPlayer);
            players.add(0, hostPlayer);
        }
    }

    /**
     * Shuffle the players so that the turn order is randomized but keep the host at index 0.
     */
    private void shufflePlayers() {
        if (players.size() <= 1) return;
        // Copy the sublist (all players except the host).
        List<Player> sublist = new ArrayList<>(players.subList(1, players.size()));
        Collections.shuffle(sublist);
        // Replace the portion after the host.
        for (int i = 1; i < players.size(); i++) {
            players.set(i, sublist.get(i - 1));
        }
        broadcastMessage("Players have been shuffled. Turn order:");
        for (int i = 0; i < players.size(); i++) {
            broadcastMessage((i + 1) + ". " + players.get(i).getName());
        }
    }

    public synchronized String setActivePlayer(String player) {
        StringBuilder response = new StringBuilder();
        boolean playerExists = playerExists(player);
        if (!playerExists) {
            response.append("You have not joined this game room\n");
            return response.toString();
        }
        if (!activePlayers.containsKey(player)) {
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

    private boolean playerExists(String playerName) {
        for (Player p : players) {
            if (p.getName().equals(playerName)) {
                return true;
            }
        }
        return false;
    }

    synchronized boolean removePlayer(String playerName) {
        return players.removeIf(player -> player.getName().equals(playerName));
    }

    public synchronized boolean isStarted() {
        return this.isStarted;
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
    
    public synchronized Map<String, Integer> getMultiplayerScores() {
        Map<String, Integer> scores = new HashMap<>();
        for (Player player : players) {
            scores.put(player.getName(), player.getScore());
        }
        return scores;
    }

    
}
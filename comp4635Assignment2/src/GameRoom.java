import java.rmi.RemoteException;
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
    private boolean isRun;
    private String host;
    private List<Player> players;
    private Map<String, ClientCallback> playerCallbacks;
    private Map<String, Boolean> activePlayers; // player name
    private int currentTurnIndex = 0;
    private Mutiplayer_Puzzle puzzleServer;

    public GameRoom(int gameId, int numPlayers, int gameLevel, String host) {
        this.gameId = gameId;
        this.numPlayers = numPlayers;
        this.gameLevel = gameLevel;
        this.isStarted = false;
        this.isRun = false;
        this.host = host;
        this.players = new ArrayList<>();
        this.playerCallbacks = new HashMap<>();
        this.activePlayers = new HashMap<>();
    }

    public boolean addPlayer(String playerName, ClientCallback callback) {
        if (players.size() < numPlayers) {
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
        if (!player.equals(host)) {
            response.append("Only the host can run the game!\n");
            return response.toString();
        }

        // set up the game
        isRun = true;
        warningRunGame();
        getCurrentActivePlayers();
        shufflePlayers();
        puzzleServer = new Mutiplayer_Puzzle(players.size(), gameLevel, wordServer);

        // puzzleServer.print2DArray1();
        // System.out.println("\n");
        // puzzleServer.print2DArray2();

        String result = startTurns();
        System.out.println(result);

        isStarted = false;
        isRun = false;
        broadcastMessage("Game is terminated\n");

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

            String message = currentPlayerName + ", it's your turn! Please type your word.";
            broadcastMessage(message);

            try {
                ClientCallback callback = playerCallbacks.get(currentPlayerName);
                if (callback != null) {
                    String playerInput = callback.requestPlayerInput(currentPlayerName);

                    if ("ERROR".equals(playerInput) || "NO_INPUT".equals(playerInput)) {
                        broadcastMessage(currentPlayerName + " did not enter a valid word.");
                    } else {
                        broadcastMessage(currentPlayerName + " typed: " + playerInput);
                        if (puzzleServer.is_guessed_word_correct(playerInput)) {
                            broadcastMessage("Player " + currentPlayerName + "'s guess is correct!");
                            // broadcastMessage(puzzleServer.render_player_view_puzzle());
                        } else {
                            broadcastMessage("Player " + currentPlayerName + "'s guess is not correct!");
                        }
                    }
                } else {
                    broadcastMessage("Player " + currentPlayerName + " is unavailable and has been removed.");
                    removePlayer(currentPlayerName);
                }
            } catch (RemoteException e) {
                broadcastMessage("Error communicating with " + currentPlayerName + ". Removing player...");
                removePlayer(currentPlayerName);
            }

            currentTurnIndex++; // Move to the next player
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

    public synchronized String setActivePlayer(String player) {
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

    private boolean playerExists(String playerName) {
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

    private static class Player {
        private String name;
        private int currentFailedAttempts;
        private static final int TOTAL_FAILED_ATTEMPTS = 5;
        private int score;

        public Player(String name) {
            this.name = name;
            this.currentFailedAttempts = 0;
            this.score = 0;
        }

        public String getName() {
            return this.name;
        }

        public void increaseScore() {
            score++;
        }
    }
}
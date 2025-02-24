import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

public class GameRoom {
    private int gameId;
    private int numPlayers;
    private int gameLevel;
    private boolean isStarted;
    private String host;
    private List<Player> players;
    private Map<String, ClientCallback> playerCallbacks;

    public GameRoom(int gameId, int numPlayers, int gameLevel, String host) {
        this.gameId = gameId;
        this.numPlayers = numPlayers;
        this.gameLevel = gameLevel;
        this.isStarted = false;
        this.host = host;
        this.players = new ArrayList<>();
        this.playerCallbacks = new HashMap<>();
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

    public synchronized void startGame() {
        if (!isStarted) {
            if (players.size() == numPlayers) {
                isStarted = true;
                broadcastMessage("***** All players joined! The game is now started *****");
                setInGame(isStarted);
                shufflePlayers();
                startTurns();
            } else {
                System.out.println("Waiting for more players...");
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

    public void shufflePlayers() {
        Collections.shuffle(players);
        broadcastMessage("The players have been shuffled.");

        // Announce player order
        for (int i = 0; i < players.size(); i++) {
            broadcastMessage((i + 1) + ". " + players.get(i).getName());
        }
    }

    private void startTurns() {
        int currentTurnIndex = 0;

        while (currentTurnIndex < players.size()) {
            Player currentPlayer = players.get(currentTurnIndex);
            String currentPlayerName = currentPlayer.getName();

            String message = currentPlayerName + ", it's your turn! Please type your word.";
            broadcastMessage(message);

            try {
                // Request input from the current player using their callback
                ClientCallback callback = playerCallbacks.get(currentPlayerName);
                if (callback != null) {
                    String playerInput = callback.requestPlayerInput(currentPlayerName);
                    System.out.println(currentPlayerName + " typed: " + playerInput);
                    broadcastMessage(currentPlayerName + " typed: " + playerInput);
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            currentTurnIndex++;
        }
        endRound();
    }

    private void endRound() {
        // Additional logic to calculate scores, check if the game is over, etc.
        broadcastMessage("The round has ended!");
        setInGame(false);
        // Example: check if the game should continue or if the game is over.
    }

    private void setInGame(boolean isStarted) {
        for (Player player : players) {
            String playerName = player.getName();
            ClientCallback callback = playerCallbacks.get(playerName);
            if (callback != null) {
                try {
                    callback.updateInGameStatus(isStarted);
                } catch (RemoteException e) {
                    System.err.println("Failed to update in-game status for player: " + playerName);
                    e.printStackTrace();
                }
            }
        }
    }

    public boolean isStarted() {
        return this.isStarted;
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
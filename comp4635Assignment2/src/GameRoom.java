import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            Player player = new Player(playerName); // Declare the player variable
            players.add(player);
            playerCallbacks.put(playerName, callback); // Use playerName as the key for the callback
            return true;
        }
        return false;
    }

    public void startGame() {
        if (!isStarted && players.size() == numPlayers) {
            isStarted = true;
            System.out.println("Game " + gameId + " has started. Starting the word puzzle...");
            // Start the word puzzle when the game begins here
        }
    }

    public void broadcastMessage(String message) {
        for (Map.Entry<String, ClientCallback> entry : playerCallbacks.entrySet()) {
            String playerName = entry.getKey();
            ClientCallback callback = entry.getValue();
            if (callback == null) {
                System.err.println("Callback for player " + playerName + " is null.");
                continue; // Skip broadcasting to this player
            }
            try {
                callback.receiveMessage(message);
            } catch (RemoteException e) {
                e.printStackTrace();
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

        public void increaseScore() {
            score++;
        }
    }
}
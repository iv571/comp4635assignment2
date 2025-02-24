import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

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
            playerCallbacks.put(playerName, callback); // Use playerName as the key for the callback
            return true;
        }
        return false;
    }

    public synchronized void startGame() {
        if (!isStarted) {
            isStarted = true;

            // Shuffle order of players
            shufflePlayers();
            // Additional logic for starting the game
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

    public void shufflePlayers() {
        // Print the order before shuffling
        System.out.println("Before Shuffle: \n");
        for (int i = 0; i < players.size(); i++) {
            System.out.println((i + 1) + ". " + players.get(i).getName());
        }

        // Shuffle the players list
        Collections.shuffle(players);

        // Broadcast the message that the players have been shuffled
        broadcastMessage("The players have been shuffled.");

        // Print the order after shuffling
        System.out.println("After Shuffle: \n");
        for (int i = 0; i < players.size(); i++) {
            System.out.println((i + 1) + ". " + players.get(i).getName());
            broadcastMessage((i + 1) + ". " + players.get(i).getName());
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
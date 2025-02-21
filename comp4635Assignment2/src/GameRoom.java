import java.util.ArrayList;
import java.util.List;

public class GameRoom {
    private int gameId;
    private int numPlayers;
    private int gameLevel;
    private boolean isStarted;
    private String host;
    private List<Player> players;

    public GameRoom(int gameId, int numPlayers, int gameLevel, String host) {
        this.gameId = gameId;
        this.numPlayers = numPlayers;
        this.gameLevel = gameLevel;
        this.isStarted = false;
        this.host = host;
        this.players = new ArrayList<>();
    }

    public boolean addPlayer(String playerName) {
        if (players.size() < numPlayers) {
            players.add(new Player(playerName));
            return true;
        }
        return false;
    }

    public void startGame() {
        if (!isStarted) {
            isStarted = true;
            System.out.println("Game " + gameId + " has started.");
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

        public void incrementFailedAttempts() {
            if (currentFailedAttempts < TOTAL_FAILED_ATTEMPTS) {
                currentFailedAttempts++;
            }
        }

        public void increaseScore() {
            score++;
        }
    }
}
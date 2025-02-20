import java.util.ArrayList;
import java.util.List;

public class GameRoom {
    private int gameId;
    private int numPlayers;
    private int gameLevel;
    private String host;
    private List<Player> players;

    public GameRoom(int gameId, int numPlayers, int gameLevel, String host) {
        this.gameId = gameId;
        this.numPlayers = numPlayers;
        this.gameLevel = gameLevel;
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
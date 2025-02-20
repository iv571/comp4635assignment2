//import java.rmi.Naming;
//import java.rmi.RemoteException;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Random;
//
//public class GameRoom {
//    private int gameId;
//    private int numPlayers;
//    private int gameLevel;
//    private List<Player> players;
//    private int currentPlayerIndex;
//    private boolean gameStarted;
//    private int failedAttempts;
//    private int totalAttempts;
//
//    public GameRoom(int gameId, int numPlayers, int gameLevel) {
//        this.gameId = gameId;
//        this.numPlayers = numPlayers;
//        this.gameLevel = gameLevel;
//        this.players = new ArrayList<>();
//        this.gameStarted = false;
//        this.failedAttempts = 0;
//        this.totalAttempts = 0;
//    }
//
//    // Adds a player to the game
//    public boolean addPlayer(Player player) {
//        if (players.size() < numPlayers) {
//            players.add(player);
//            return true;
//        }
//        return false;
//    }
//
//    // Start the game when all players have joined
//    public void startGame() {
//        if (players.size() == numPlayers) {
//            gameStarted = true;
//            assignTurnOrder();
//            createPuzzle();
//            shareGameStructure();
//        }
//    }
//
//    // Assigns a random order of turns to players
//    private void assignTurnOrder() {
//        Random random = new Random();
//        for (int i = 0; i < players.size(); i++) {
//            int randomIndex = random.nextInt(players.size());
//            Player temp = players.get(i);
//            players.set(i, players.get(randomIndex));
//            players.set(randomIndex, temp);
//        }
//        currentPlayerIndex = 0;
//    }
//
//    // Creates a puzzle based on the number of players and game level
//
//    // Generate a vertical word stem (you could use some logic to generate a word stem from a word)
//
//    // Share the game structure (puzzle) with all players
//
//    // Process a player's guess (either a letter or a word)
//    public void processGuess(Player player, String guess) {
//        if (!gameStarted) {
//            return; // Game hasn't started yet
//        }
//
//        if (!players.get(currentPlayerIndex).equals(player)) {
//            return; // It's not the player's turn
//        }
//
//        if (guess.length() == 1) {
//            // Process a letter guess
//        } else if (guess.length() > 1) {
//            // Process a word guess
//        }
//
//        // Move to the next player's turn
//        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
//    }
//
//    // Process a letter guess
//
//    // Process a word guess
//
//    // Get game history for a player
//    public String getGameHistory(Player player) {
//        // Return the game history (single-player and multi-player games)
//        return player.getGameHistory();
//    }
//
//    // Check if the game is finished
//    public boolean isGameFinished() {
//        for (String word : puzzleWords) {
//            if (!word.isEmpty()) {
//                return false; // Game is not finished if there are still words to guess
//            }
//        }
//        return true;
//    }
//
//    // Get the winner
//    public Player getWinner() {
//        Player winner = null;
//        int maxScore = -1;
//
//        for (Player player : players) {
//            if (player.getScore() > maxScore) {
//                maxScore = player.getScore();
//                winner = player;
//            }
//        }
//
//        return winner;
//    }
//}
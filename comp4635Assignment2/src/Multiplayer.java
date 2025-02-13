import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a multi-player game session and (optionally) manages all sessions.
 */
public class Multiplayer {

    // -----------------------------
    // FIELDS THAT DEFINE ONE SESSION
    // -----------------------------
    public String gameId;
    public int totalPlayers;
    public boolean gameStarted = false;

    // Players who have joined this session
    public List<String> joinedPlayers = new ArrayList<>();

    // Track how many words each player has won
    public Map<String, Integer> wordsWonMap = new HashMap<>();

    // Puzzle data
    public List<String> verticalStems = new ArrayList<>();
    public char[][] puzzle;                // The puzzle with letters
    public char[][] revealedPuzzle;        // A copy of the complete puzzle for reference
    public char[][] formattedPuzzleArray;  // The "masked" puzzle with underscores
    public String formattedPuzzle;         // Same as above but stored as a single string
    public int failAttempts;

    // Turn order
    public List<String> turnOrder = new ArrayList<>();
    private int currentTurnIndex = 0;

    // -----------------------------
    // FIELDS FOR MANAGING MULTIPLE SESSIONS
    // (Often stored in the server, not here)
    // -----------------------------
    private Map<String, Multiplayer> multiGames = new ConcurrentHashMap<>();
    private static int gameCounter = 1;

    // -----------------------------
    // CONSTRUCTOR FOR ONE SESSION
    // -----------------------------
    public Multiplayer(String gameId, int totalPlayers) {
        this.gameId = gameId;
        this.totalPlayers = totalPlayers;
    }

    /**
     * Default constructor if you want to instantiate this class
     * as a “manager” object. But typically you’d keep the manager logic
     * in your server code instead.
     */
    public Multiplayer() {
    }

    // -----------------------------
    // GET CURRENT PLAYER & ADVANCE TURN
    // -----------------------------
    public String getCurrentPlayer() {
        if (turnOrder.isEmpty()) {
            return null;
        }
        return turnOrder.get(currentTurnIndex);
    }

    public void nextTurn() {
        if (!turnOrder.isEmpty()) {
            currentTurnIndex = (currentTurnIndex + 1) % turnOrder.size();
        }
    }

    /**
     * Returns true if there are no more underscores in the puzzle
     * (meaning the puzzle is fully revealed).
     */
    public boolean isPuzzleComplete() {
        if (formattedPuzzleArray == null) {
            return false;
        }
        for (int r = 0; r < puzzle.length; r++) {
            for (int c = 0; c < puzzle[r].length; c++) {
                if (puzzle[r][c] != '.' && formattedPuzzleArray[r][c] == '_') {
                    return false;
                }
            }
        }
        return true;
    }

    // -----------------------------
    // EXAMPLE MULTI-PLAYER METHODS
    // -----------------------------

    /**
     * Host starts a multi-player session by specifying totalPlayers and level.
     * The server immediately creates the puzzle with multiple vertical stems.
     * Additional players can join via joinMultiGame(...).
     */
    public synchronized String startMultiGame(String hostPlayer, int totalPlayers, int level)
            throws RemoteException, RejectedException {

        String newGameId = "MP-" + (gameCounter++);
        Multiplayer mp = new Multiplayer(newGameId, totalPlayers);

        // Host automatically joins
        mp.joinedPlayers.add(hostPlayer);
        mp.wordsWonMap.put(hostPlayer, 0);
        System.out.println("Player " + hostPlayer + " created multi-game " + newGameId);

        // Build puzzle for multi-player (example method below)
        buildMultiPuzzle(mp, level);

        // Not started until enough players join
        mp.gameStarted = false;

        // Store in multiGames map
        multiGames.put(newGameId, mp);

        return "Multi-player game created! Game ID = " + newGameId
             + "\nWaiting for " + (totalPlayers - 1) + " more players to join...";
    }

    /**
     * Another player joins an existing multi-player game using the gameId.
     */
    public synchronized String joinMultiGame(String player, String gameId)
            throws RemoteException, RejectedException {

        Multiplayer mp = multiGames.get(gameId);
        if (mp == null) {
            return "No multi-player game found with ID " + gameId;
        }
        if (mp.gameStarted) {
            return "Game " + gameId + " has already started. You cannot join now.";
        }
        if (mp.joinedPlayers.contains(player)) {
            return "You have already joined game " + gameId;
        }
        mp.joinedPlayers.add(player);
        mp.wordsWonMap.put(player, 0);

        int needed = mp.totalPlayers - mp.joinedPlayers.size();
        if (needed > 0) {
            return "Joined game " + gameId + ". Still waiting for " + needed + " more player(s).";
        } else {
            // All players have joined => start
            mp.gameStarted = true;
            List<String> allPlayers = new ArrayList<>(mp.joinedPlayers);
            Collections.shuffle(allPlayers);
            mp.turnOrder = allPlayers;

            // Example failAttempts calculation
            mp.failAttempts = 5; // or something more elaborate

            return "All players joined! Multi-player game " + gameId + " is now started.\n"
                 + "Turn order: " + mp.turnOrder + "\n"
                 + "Current puzzle:\n" + mp.formattedPuzzle;
        }
    }

    /**
     * Player guesses a letter in the multi-player game.
     */
    public synchronized String guessMultiLetter(String player, String gameId, char letter)
            throws RemoteException, RejectedException {

        Multiplayer mp = multiGames.get(gameId);
        if (mp == null) {
            return "No multi-player game found with ID " + gameId;
        }
        if (!mp.gameStarted) {
            return "Game " + gameId + " hasn't started yet (waiting for all players).";
        }
        if (!mp.getCurrentPlayer().equals(player)) {
            return "Not your turn! Current turn: " + mp.getCurrentPlayer();
        }

        // Reveal the letter if it exists
        boolean found = false;
        char lowerLetter = Character.toLowerCase(letter);

        for (int r = 0; r < mp.puzzle.length; r++) {
            for (int c = 0; c < mp.puzzle[r].length; c++) {
                if (Character.toLowerCase(mp.puzzle[r][c]) == lowerLetter
                    && mp.formattedPuzzleArray[r][c] == '_') {
                    mp.formattedPuzzleArray[r][c] = mp.puzzle[r][c];
                    found = true;
                }
            }
        }

        if (!found) {
            mp.failAttempts--;
            if (mp.failAttempts <= 0) {
                // Game over
                multiGames.remove(gameId);
                return "No attempts left! Game over.\n"
                     + "Final puzzle solution:\n" + revealPuzzle(mp.puzzle) + "\n"
                     + "Scores: " + mp.wordsWonMap;
            }
        } else {
            // Possibly check if a word is newly completed
            int newlyCompleted = countNewlyCompletedWords(mp, player);
            if (newlyCompleted > 0) {
                mp.wordsWonMap.put(player, mp.wordsWonMap.get(player) + newlyCompleted);
            }
        }

        mp.formattedPuzzle = buildFormattedString(mp.formattedPuzzleArray);

        if (mp.isPuzzleComplete()) {
            String winner = determineWinner(mp.wordsWonMap);
            multiGames.remove(gameId);
            return "Puzzle complete! Winner is: " + winner
                 + "\nFinal scores: " + mp.wordsWonMap
                 + "\nPuzzle solution:\n" + mp.formattedPuzzle;
        }

        mp.nextTurn();
        return (found ? "Good guess! " : "Wrong guess!")
             + "\nAttempts left: " + mp.failAttempts
             + "\nCurrent puzzle:\n" + mp.formattedPuzzle
             + "\nNext turn: " + mp.getCurrentPlayer();
    }

    /**
     * Player guesses a whole word in the multi-player game.
     */
    public synchronized String guessMultiWord(String player, String gameId, String word)
            throws RemoteException, RejectedException {

        Multiplayer mp = multiGames.get(gameId);
        if (mp == null) {
            return "No multi-player game found with ID " + gameId;
        }
        if (!mp.gameStarted) {
            return "Game " + gameId + " hasn't started yet.";
        }
        if (!mp.getCurrentPlayer().equals(player)) {
            return "Not your turn! Current turn: " + mp.getCurrentPlayer();
        }

        boolean matchFound = revealWordInPuzzle(mp, word.toLowerCase());

        if (!matchFound) {
            mp.failAttempts--;
            if (mp.failAttempts <= 0) {
                multiGames.remove(gameId);
                return "No attempts left! Game over.\n"
                     + "Final puzzle solution:\n" + revealPuzzle(mp.puzzle) + "\n"
                     + "Scores: " + mp.wordsWonMap;
            }
        } else {
            mp.wordsWonMap.put(player, mp.wordsWonMap.get(player) + 1);
        }

        mp.formattedPuzzle = buildFormattedString(mp.formattedPuzzleArray);

        if (mp.isPuzzleComplete()) {
            String winner = determineWinner(mp.wordsWonMap);
            multiGames.remove(gameId);
            return "Puzzle complete! Winner is: " + winner
                 + "\nFinal scores: " + mp.wordsWonMap
                 + "\nPuzzle solution:\n" + mp.formattedPuzzle;
        }

        mp.nextTurn();
        return (matchFound ? "Correct word guess!" : "Wrong word!")
             + "\nAttempts left: " + mp.failAttempts
             + "\nCurrent puzzle:\n" + mp.formattedPuzzle
             + "\nNext turn: " + mp.getCurrentPlayer();
    }

    // -----------------------------
    // PUZZLE-BUILDING & HELPER METHODS
    // -----------------------------

    /**
     * Example method to build a puzzle with multiple vertical stems side by side.
     */
    private void buildMultiPuzzle(Multiplayer mp, int level) {
        // For each player, pick a random "vertical stem" (just a placeholder)
        for (int i = 0; i < mp.totalPlayers; i++) {
            String candidate;
            do {
                candidate = getRandomWordFromFile(Math.max(3, level));
            } while (candidate.length() < level);
            mp.verticalStems.add(candidate.toLowerCase());
        }

        // Construct a grid large enough to hold these stems side by side
        int maxLen = 0;
        for (String stem : mp.verticalStems) {
            if (stem.length() > maxLen) {
                maxLen = stem.length();
            }
        }
        int rows = maxLen;
        // Let each stem occupy 3 columns + 1 column gap => totalPlayers * 4 - 1
        int cols = mp.totalPlayers * 4 - 1;

        char[][] puzzleGrid = new char[rows][cols];
        for (char[] row : puzzleGrid) {
            java.util.Arrays.fill(row, '.');
        }

        // Place each stem in the grid, spaced out by 4 columns
        for (int stemIndex = 0; stemIndex < mp.verticalStems.size(); stemIndex++) {
            String stem = mp.verticalStems.get(stemIndex);
            int col = stemIndex * 4;
            for (int r = 0; r < stem.length(); r++) {
                puzzleGrid[r][col] = stem.charAt(r);
            }
        }

        mp.puzzle = puzzleGrid;
        mp.revealedPuzzle = cloneGrid(puzzleGrid);

        // Build masked puzzle
        char[][] masked = new char[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (puzzleGrid[r][c] == '.') {
                    masked[r][c] = '.';
                } else {
                    masked[r][c] = '_';
                }
            }
        }
        mp.formattedPuzzleArray = masked;
        mp.formattedPuzzle = buildFormattedString(masked);
    }

    /**
     * Dummy placeholder that returns a random word from a file or from a list.
     */
    private String getRandomWordFromFile(int minLength) {
        // For demonstration, let's just return a hardcoded word:
        return "example"; 
        // In your code, you likely read from words.txt, filter by minLength, etc.
    }

    /**
     * Deep copy of a 2D char array.
     */
    private char[][] cloneGrid(char[][] source) {
        char[][] copy = new char[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = java.util.Arrays.copyOf(source[i], source[i].length);
        }
        return copy;
    }

    /**
     * Convert a 2D char array to a displayable string.
     */
    private static String buildFormattedString(char[][] grid) {
        StringBuilder sb = new StringBuilder();
        for (char[] row : grid) {
            for (char c : row) {
                sb.append(c);
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Reveal the entire puzzle (for game over).
     */
    private static String revealPuzzle(char[][] puzzle) {
        StringBuilder sb = new StringBuilder();
        for (char[] row : puzzle) {
            for (char c : row) {
                sb.append(c);
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Check newly completed words after revealing a letter.
     * For brevity, return 0. Implement your own logic if needed.
     */
    private int countNewlyCompletedWords(Multiplayer mp, String player) {
        return 0;
    }

    /**
     * Reveal a word if it matches a row exactly.
     * Returns true if we found & revealed it.
     */
    private boolean revealWordInPuzzle(Multiplayer mp, String guessedWord) {
        for (int r = 0; r < mp.puzzle.length; r++) {
            // Gather letters in row (ignoring '.')
            StringBuilder rowLetters = new StringBuilder();
            for (int c = 0; c < mp.puzzle[r].length; c++) {
                if (mp.puzzle[r][c] != '.') {
                    rowLetters.append(mp.puzzle[r][c]);
                }
            }
            String rowString = rowLetters.toString().toLowerCase();
            if (rowString.equals(guessedWord)) {
                // Reveal entire row
                for (int c = 0; c < mp.puzzle[r].length; c++) {
                    if (mp.puzzle[r][c] != '.') {
                        mp.formattedPuzzleArray[r][c] = mp.puzzle[r][c];
                    }
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Pick the winner by who has the highest wordsWon count.
     */
    private String determineWinner(Map<String, Integer> wordsWonMap) {
        String winner = null;
        int maxScore = Integer.MIN_VALUE;
        for (Map.Entry<String, Integer> e : wordsWonMap.entrySet()) {
            if (e.getValue() > maxScore) {
                maxScore = e.getValue();
                winner = e.getKey();
            }
        }
        return winner;
    }
}
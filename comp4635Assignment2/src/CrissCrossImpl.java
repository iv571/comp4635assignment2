
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Implementation of the CrissCrossPuzzleServer interface.
 * <p>
 * This class is responsible for managing single-player game sessions,
 * handling word commands (add, remove, check), constructing puzzles,
 * and delegating multi-player game functionality to the Multiplayer manager.
 * It extends UnicastRemoteObject for RMI-based remote invocation.
 * </p>
 */

@SuppressWarnings("serial")
public class CrissCrossImpl extends UnicastRemoteObject implements CrissCrossPuzzleServer {

    // Map to hold a game session for each player
    private Map<String, GameSession> sessions = new ConcurrentHashMap<>();

    // Reference to the WordRepository
    private WordRepositoryServer wordServer;

    // multiplayer manager
    private Multiplayer multiplayerManager = new Multiplayer();

    /**
     * Constructs a new CrissCrossImpl instance and connects to the WordRepositoryServer.
     *
     * @param bankName an identifier (unused) for this instance.
     * @throws RemoteException if an RMI error occurs.
     */
    public CrissCrossImpl(String bankName) throws RemoteException {
        super();
        connectToWordRepository();
    }

    /**
     * Inner class representing a game session for a single player.
     * Contains the puzzle state, vertical stem, horizontal words,
     * formatted and revealed puzzles, and the number of failed attempts allowed.
     */
    private class GameSession {
        String verticalStem;
        String[] horizontalWords;
        char[][] puzzle;
        String formattedPuzzle;
        String revealedPuzzle;
        int failAttempts;
        // Additional game state (e.g., score) could be added here.
    }

    /**
     * Initial connection to the WordRepositoryServer.
     */
    private void connectToWordRepository() {
        try {
            wordServer = (WordRepositoryServer) Naming.lookup("rmi://localhost:1099/WordRepositoryServer");
            System.out.println("Connected to WordRepositoryServer in CrissCrossImpl.");
        } catch (Exception e) {
            System.err.println("Failed to connect to WordRepositoryServer: " + e.getMessage());
        }
    }

    /**
     * Attempts to reconnect to the WordRepositoryServer with multiple retries.
     * If all attempts fail, the wordServer reference is set to null.
     */
    private void reconnectWordRepository() {
        int maxAttempts = 3;
        int attempt = 0;
        while (attempt < maxAttempts) {
            try {
                System.out.println("Attempting to reconnect to WordRepositoryServer (attempt "
                        + (attempt + 1) + " of " + maxAttempts + ")...");
                // Wait a bit before retrying
                Thread.sleep(1000);
                wordServer = (WordRepositoryServer) Naming.lookup("rmi://localhost:1099/WordRepositoryServer");
                System.out.println("Reconnecting to WordRepositoryServer in CrissCrossImpl.");
                return;
            } catch (Exception e) {
                System.err.println("Reconnection attempt " + (attempt + 1) + " failed: " + e.getMessage());
            }
            attempt++;
        }
        // If all attempts fail, clear the reference.
        wordServer = null;
    }
    // ===============================
    // Implementation of word commands
    // ===============================
    
    /**
     * Adds a word to the WordRepository.
     *
     * @param word the word to be added.
     * @return true if the word was added successfully, false otherwise.
     * @throws RemoteException if a remote error occurs or the WordRepositoryServer is unavailable.
     */

    @Override
    public boolean addWord(String word) throws RemoteException {
        if (wordServer == null) {
            // Attempt an initial or lazy connect
            connectToWordRepository();
            if (wordServer == null) {
                throw new RemoteException("WordRepositoryServer is not available.");
            }
        }
        try {
            return wordServer.createWord(word);
        } catch (RemoteException e) {
            if (e.getMessage() != null && e.getMessage().contains("Connection refused")) {
                System.out.println("Lost connection to WordRepositoryServer, attempting to reconnect...");
                reconnectWordRepository();
                if (wordServer == null) {
                    throw new RemoteException("WordRepositoryServer is unavailable after reconnection attempt.");
                }
                // Retry once after reconnecting
                return wordServer.createWord(word);
            } else {
                throw e; // Some other remote error
            }
        }
    }
    
    /**
     * Removes a word from the WordRepository.
     *
     * @param word the word to be removed.
     * @return true if the word was removed successfully, false otherwise.
     * @throws RemoteException if a remote error occurs or the WordRepositoryServer is unavailable.
     */
    @Override
    public boolean removeWord(String word) throws RemoteException {
        if (wordServer == null) {
            // Attempt an initial or lazy connect
            connectToWordRepository();
            if (wordServer == null) {
                throw new RemoteException("WordRepositoryServer is not available.");
            }
        }
        try {
            return wordServer.removeWord(word);
        } catch (RemoteException e) {
            if (e.getMessage() != null && e.getMessage().contains("Connection refused")) {
                System.out.println("Lost connection to WordRepositoryServer, attempting to reconnect...");
                reconnectWordRepository();
                if (wordServer == null) {
                    throw new RemoteException("WordRepositoryServer is unavailable after reconnection attempt.");
                }
                // Retry once after reconnecting
                return wordServer.removeWord(word);
            } else {
                throw e; // Some other remote error
            }
        }
    }

    /**
     * Checks if a word exists in the WordRepository.
     *
     * @param word the word to be checked.
     * @return true if the word exists, false otherwise.
     * @throws RemoteException if a remote error occurs or the WordRepositoryServer is unavailable.
     */
    @Override
    public boolean checkWord(String word) throws RemoteException {
        if (wordServer == null) {
            // Attempt an initial or lazy connect
            connectToWordRepository();
            if (wordServer == null) {
                throw new RemoteException("WordRepositoryServer is not available.");
            }
        }
        try {
            return wordServer.checkWord(word);
        } catch (RemoteException e) {
            if (e.getMessage() != null && e.getMessage().contains("Connection refused")) {
                System.out.println("Lost connection to WordRepositoryServer, attempting to reconnect...");
                reconnectWordRepository();
                if (wordServer == null) {
                    throw new RemoteException("WordRepositoryServer is unavailable after reconnection attempt.");
                }
                // Retry once after reconnecting
                return wordServer.checkWord(word);
            } else {
                throw e; // Some other remote error
            }
        }
    }

    /**
     * Retrieves a random word from the "words.txt" file with a minimum length.
     *
     * @param minLength the minimum length required for the word.
     * @return a random word meeting the criteria, or an empty string if none found.
     */
    private String getRandomWordFromFile(int minLength) {
        List<String> words = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader("words.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim().toLowerCase();
                if (!line.isEmpty() && line.length() >= minLength) {
                    words.add(line);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading words.txt: " + e.getMessage());
        }
        if (words.isEmpty()) {
            return "";
        }
        Random rand = new Random();
        return words.get(rand.nextInt(words.size()));
    }

    /**
     * Retrieves a random word from the file that contains a specific character constraint.
     *
     * @param constraint         the character that must appear exactly once in the word.
     * @param minLength          the minimum length for the word.
     * @param verticalStemLength the length of the vertical stem.
     * @param colForStem         the column where the vertical stem is placed.
     * @return a valid word that meets the constraint, or an empty string if none found.
     */
    private String getConstrainedRandomWord(char constraint, int minLength, int verticalStemLength, int colForStem) {
        List<String> validWords = new ArrayList<>();
        char lowerConstraint = Character.toLowerCase(constraint);
        int numCols = verticalStemLength; // Calculate numCols based on vertical stem + padding

        try (BufferedReader br = new BufferedReader(new FileReader("words.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim().toLowerCase();
                if (line.isEmpty() || line.length() < minLength)
                    continue;

                int constraintIndex = line.indexOf(lowerConstraint);
                if (constraintIndex == -1)
                    continue; // Skip if constraint not found
                if (countOccurrences(line, lowerConstraint) != 1)
                    continue; // Ensure exactly one occurrence

                // Check if word fits without grid clamping
                int startCol = colForStem - constraintIndex;
                if (startCol < 0 || startCol + line.length() > numCols)
                    continue;

                validWords.add(line);
            }
        } catch (IOException e) {
            System.err.println("Error reading words.txt: " + e.getMessage());
        }

        return validWords.isEmpty() ? "" : validWords.get(new Random().nextInt(validWords.size()));
    }

    /**
     * Counts the number of times a specific character appears in a string.
     *
     * @param str the string in which to count occurrences
     * @param ch  the character to count
     * @return the number of times ch appears in str
     */
    private int countOccurrences(String str, char ch) {
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == ch) {
                count++;
            }
        }
        return count;
    }

    /**
     * Constructs the puzzle grid using a vertical stem and horizontal words.
     *
     * @param verticalStem  the vertical word forming the stem.
     * @param horizontalWords array of horizontal words.
     * @return a 2D character array representing the puzzle grid.
     */
    private char[][] constructPuzzle(String verticalStem, String[] horizontalWords) {
        int numRows = verticalStem.length();

        // Compute the longest word length among verticalStem and horizontalWords.
        int maxWordLength = verticalStem.length();
        for (String word : horizontalWords) {
            if (word != null && word.length() > maxWordLength) {
                maxWordLength = word.length();
            }
        }

        int numCols = maxWordLength;
        char[][] grid = new char[numRows][numCols];
        for (int i = 0; i < numRows; i++) {
            Arrays.fill(grid[i], '.');
        }
        int colForStem = numCols / 2;
        for (int row = 0; row < verticalStem.length(); row++) {
            grid[row][colForStem] = verticalStem.charAt(row);
        }
        for (int row = 0; row < horizontalWords.length && row < numRows; row++) {
            String hWord = horizontalWords[row];
            if (hWord.isEmpty())
                continue;
            char constraint = verticalStem.charAt(row);
            int constraintIndex = hWord.indexOf(Character.toLowerCase(constraint));
            if (constraintIndex < 0)
                continue;
            int startCol = colForStem - constraintIndex;
            startCol = Math.max(0, Math.min(startCol, numCols - hWord.length()));
            for (int j = 0; j < hWord.length() && (startCol + j) < numCols; j++) {
                char currentChar = grid[row][startCol + j];
                char newChar = hWord.charAt(j);
                if (currentChar == '.' || currentChar == newChar) {
                    grid[row][startCol + j] = newChar;
                }
            }
        }
        return grid;
    }

    /**
     * Counts the number of letters in the puzzle (non-placeholder characters).
     *
     * @param puzzle the 2D character array representing the puzzle.
     * @return the count of letters in the puzzle.
     */
    private int countPuzzleLetters(char[][] puzzle) {
        int count = 0;
        for (char[] row : puzzle) {
            for (char c : row) {
                if (c != '.') {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Formats the puzzle for display by replacing revealed letters with underscores.
     *
     * @param puzzle the 2D character array representing the puzzle.
     * @return a formatted string of the puzzle.
     */
    private String formatPuzzle(char[][] puzzle) {
        // Formats puzzle so that unrevealed letters appear as underscores ('_')
        StringBuilder sb = new StringBuilder();
        for (char[] row : puzzle) {
            for (char c : row) {
                sb.append(c == '.' ? '.' : '_');
            }
            sb.append("+\n");
        }
        return sb.toString();
    }

    /**
     * Reveals the complete puzzle solution.
     *
     * @param puzzle the 2D character array representing the puzzle.
     * @return a string representation of the fully revealed puzzle.
     */
    private String revealPuzzle(char[][] puzzle) {
        // Formats the puzzle to reveal the solution.
        StringBuilder sb = new StringBuilder();
        for (char[] row : puzzle) {
            for (char c : row) {
                sb.append(c);
            }
            sb.append("+\n");
        }
        return sb.toString();
    }

    /**
     * Synchronized helper method to add a player's game session.
     *
     * @param player  the player's name.
     * @param session the GameSession object for the player.
     */
    private void putSession(String player, GameSession session) {
        synchronized (sessions) {
            sessions.put(player, session);
        }
    }

    /**
     * Synchronized helper method to retrieve a player's game session.
     *
     * @param player the player's name.
     * @return the GameSession object for the player, or null if none exists.
     */
    private GameSession getSession(String player) {
        synchronized (sessions) {
            return sessions.get(player);
        }
    }

    /**
     * Synchronized helper method to remove a player's game session.
     *
     * @param player the player's name.
     */
    private void removeSession(String player) {
        synchronized (sessions) {
            sessions.remove(player);
        }
    }

    /**
     * Starts a new single-player game session for the given player.
     *
     * @param player            the player's name.
     * @param level             the game difficulty level.
     * @param failedAttemptFactor a factor to calculate allowed failed attempts.
     * @return a string message containing the formatted puzzle and allowed attempts.
     * @throws RemoteException if a remote error occurs.
     */
    @Override
    public String startGame(String player, int level, int failedAttemptFactor) throws RemoteException {
        // Clamp the level between 1 and 10.
        int effectiveLevel = Math.max(1, Math.min(level, 10));
        // effectiveLevel now controls the number of horizontal words generated.

        GameSession session = new GameSession();

        // Get a candidate word from the file that is at least 'effectiveLevel' long.
        String candidate;
        do {
            candidate = getRandomWordFromFile(effectiveLevel);
        } while (candidate.length() < effectiveLevel);
        // Use the entire candidate as the vertical stem.
        session.verticalStem = candidate.toLowerCase();
        // The vertical stem length is the full length of the candidate.
        int verticalStemLength = candidate.length();
        int numCols = verticalStemLength;
        int colForStem = numCols / 2;
        // Create an array for horizontal words of size 'effectiveLevel'.
        // (This means we generate horizontal words only for rows 1 ..
        // effectiveLevel-1.)
        session.horizontalWords = new String[effectiveLevel];
        Arrays.fill(session.horizontalWords, "");
        for (int i = 1; i < effectiveLevel && i < verticalStemLength; i++) {
            String hWord;
            do {
                hWord = getConstrainedRandomWord(session.verticalStem.charAt(i), effectiveLevel, verticalStemLength,
                        colForStem);
            } while (hWord.isEmpty());
            session.horizontalWords[i] = hWord.toLowerCase();
        }

        // Construct the puzzle grid using the entire vertical stem (all its
        // characters).
        session.puzzle = constructPuzzle(session.verticalStem, session.horizontalWords);
        int numLetters = countPuzzleLetters(session.puzzle);
        session.failAttempts = failedAttemptFactor * numLetters;

        session.formattedPuzzle = formatPuzzle(session.puzzle);
        session.revealedPuzzle = revealPuzzle(session.puzzle);

        // Only the update to the sessions map is synchronized.
        putSession(player, session);

        System.out.println("Completed puzzle on server:");
        System.out.println(session.revealedPuzzle);

        return "Game started for " + player + "!\n" +
                session.formattedPuzzle +
                "\nAttempts allowed: " + session.failAttempts;
    }

    /**
     * Processes a letter guess from the player.
     *
     * @param player the player's name.
     * @param letter the letter guessed.
     * @return a string message with the updated puzzle state or game over message.
     * @throws RemoteException if a remote error occurs.
     */
    @Override
    public String guessLetter(String player, char letter) throws RemoteException {
        GameSession session = getSession(player);
        if (session == null) {
            return "No active game session for " + player + ".";
        }

        char lowerLetter = Character.toLowerCase(letter);
        boolean found = false;
        char[] formattedChars = session.formattedPuzzle.toCharArray();
        char[] revealedChars = session.revealedPuzzle.toCharArray();
        for (int i = 0; i < revealedChars.length; i++) {
            if (Character.toLowerCase(revealedChars[i]) == lowerLetter && formattedChars[i] == '_') {
                formattedChars[i] = revealedChars[i];
                found = true;
            }
        }
        if (!found) {
            session.failAttempts--;
            if (session.failAttempts <= 0) {
                try {
                    Registry registry = LocateRegistry.getRegistry("localhost", 1099);
                    UserAccountServer accountServer = (UserAccountServer) registry.lookup("UserAccountServer");
                    accountServer.updateScore(player, -1);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                removeSession(player);
                return "Game over! No attempts remaining. The solution was:\n" + session.revealedPuzzle;
            }
        }
        session.formattedPuzzle = new String(formattedChars);
        if (!session.formattedPuzzle.contains("_")) {
            removeSession(player);
            // After a win, update the score by +1:
            try {
                Registry registry = LocateRegistry.getRegistry("localhost", 1099);
                UserAccountServer accountServer = (UserAccountServer) registry.lookup("UserAccountServer");
                accountServer.updateScore(player, 1);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return "Congratulations " + player + ", you completed the puzzle!\n" + session.formattedPuzzle;
        }
        return "Current puzzle state:\n" + session.formattedPuzzle +
                "\nAttempts remaining: " + session.failAttempts;
    }

    /**
     * Processes a word guess from the player.
     *
     * @param player the player's name.
     * @param word   the word guessed.
     * @return a string message with the updated puzzle state or game over message.
     * @throws RemoteException if a remote error occurs.
     */
    @Override
    public String guessWord(String player, String word) throws RemoteException {
        GameSession session = getSession(player);
        if (session == null) {
            return "No active game session for " + player + ".";
        }
        String lowerWord = word.toLowerCase();
        boolean wordFound = false;

        // Get dynamic grid dimensions.
        int gridWidth = session.puzzle[0].length;
        int colForStem = gridWidth / 2;

        // We'll use the delimiter "\n" for splitting and joining rows.
        String[] rows = session.formattedPuzzle.split("\\+\\n");

        if (lowerWord.equals(session.verticalStem.toLowerCase())) {
            // The guess is for the vertical stem.
            for (int i = 0; i < rows.length && i < session.puzzle.length; i++) {
                char[] rowChars = rows[i].toCharArray();
                if (colForStem < rowChars.length) {
                    // Reveal the vertical letter from the full puzzle.
                    rowChars[colForStem] = session.puzzle[i][colForStem];
                }
                rows[i] = new String(rowChars);
            }
            session.formattedPuzzle = String.join("+\n", rows) + "+";
            session.formattedPuzzle = session.formattedPuzzle.replace("++", "+");
            wordFound = true;
        } else {
            // Look for a matching horizontal word.
            for (int i = 0; i < session.horizontalWords.length; i++) {
                String hWord = session.horizontalWords[i];
                if (hWord.isEmpty())
                    continue;
                if (hWord.equalsIgnoreCase(lowerWord)) {
                    // Determine the constraint letter (from the vertical stem) for this row.
                    char constraint = session.verticalStem.charAt(i);
                    int constraintIndex = hWord.toLowerCase().indexOf(Character.toLowerCase(constraint));
                    if (constraintIndex == -1)
                        continue;
                    // Compute the starting column for the horizontal word.
                    int startCol = colForStem - constraintIndex;
                    startCol = Math.max(0, Math.min(startCol, gridWidth - hWord.length()));
                    // Make sure we have the correct row from the formatted puzzle.
                    if (i >= rows.length)
                        continue;
                    char[] rowChars = rows[i].toCharArray();
                    // Reveal only the segment corresponding to the horizontal word.
                    for (int j = 0; j < hWord.length() && (startCol + j) < gridWidth; j++) {
                        rowChars[startCol + j] = session.puzzle[i][startCol + j];
                    }
                    rows[i] = new String(rowChars);
                    session.formattedPuzzle = String.join("+\n", rows) + "+";
                    session.formattedPuzzle = session.formattedPuzzle.replace("++", "+");
                    wordFound = true;
                    break;
                }
            }
        }

        if (wordFound) {
            // If there are no underscores left, the puzzle is complete.
            if (!session.formattedPuzzle.contains("_")) {
                removeSession(player);
                // After a win, update the score by +1.
                try {
                    Registry registry = LocateRegistry.getRegistry("localhost", 1099);
                    UserAccountServer accountServer = (UserAccountServer) registry.lookup("UserAccountServer");
                    accountServer.updateScore(player, 1);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return "Congratulations " + player + ", you completed the puzzle!\n" + session.formattedPuzzle;
            }
            return "Word correct!\nCurrent puzzle state:\n" + session.formattedPuzzle +
                    "\nAttempts remaining: " + session.failAttempts;
        } else {
            session.failAttempts--;
            if (session.failAttempts <= 0) {
                try {
                    Registry registry = LocateRegistry.getRegistry("localhost", 1099);
                    UserAccountServer accountServer = (UserAccountServer) registry.lookup("UserAccountServer");
                    accountServer.updateScore(player, -1);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                removeSession(player);
                return "Game over! No attempts remaining. The solution was:\n" + session.revealedPuzzle;
            }
            return "Sorry, the word \"" + word + "\" is not in the puzzle.\nAttempts remaining: "
                    + session.failAttempts;
        }
    }
    
    /**
     * Ends the current game session for a player.
     *
     * @param player the player's name.
     * @return a string message with the final solution.
     * @throws RemoteException if a remote error occurs.
     */
    @Override
    public String endGame(String player) throws RemoteException {
        GameSession session = sessions.remove(player);
        if (session == null) {
            return "No active game session for " + player + ".";
        }
        return "Game ended for " + player + ".\nThe solution was:\n" + session.revealedPuzzle;
    }

    /**
     * Restarts the game session for a player with default parameters.
     *
     * @param player the player's name.
     * @return a string message from the newly started game.
     * @throws RemoteException if a remote error occurs.
     */
    @Override
    public synchronized String restartGame(String player) throws RemoteException {
        sessions.remove(player);
        // Restart with default parameters (adjust as needed)
        return startGame(player, 5, 3);
    }

    /**
     * Starts a new multi-player game room.
     *
     * @param username    the host player's name.
     * @param numPlayers  the number of players expected.
     * @param level       the game difficulty level.
     * @param hostCallback the host's callback interface.
     * @return a message indicating the status of starting the multi-player game.
     * @throws RemoteException if a remote error occurs.
     * @throws RejectedException if the request is rejected.
     */
    @Override
    public synchronized String startMultiGame(String username, int numPlayers, int level, ClientCallback hostCallback)
            throws RemoteException, RejectedException {
        return multiplayerManager.startMultiGame(username, numPlayers, level, hostCallback);
    }

    /**
     * Allows a player to join an existing multi-player game room.
     *
     * @param player   the player's name.
     * @param gameId   the game room identifier.
     * @param callback the player's callback interface.
     * @return a message indicating the status of joining the game room.
     * @throws RemoteException if a remote error occurs.
     * @throws RejectedException if the request is rejected.
     */
    @Override
    public synchronized String joinMultiGame(String player, int gameId, ClientCallback callback)
            throws RemoteException, RejectedException {
        // Delegate to the multiplayerManager instance.
        return multiplayerManager.joinMultiGame(player, gameId, callback);
    }

    /**
     * Returns a list of active multi-player game rooms.
     *
     * @return a string listing active game rooms.
     * @throws RemoteException if a remote error occurs.
     */
    @Override
    public String showActiveGameRooms() throws RemoteException {
        return multiplayerManager.showActiveGameRooms();
    }

    /**
     * Starts the game room for a multi-player game.
     *
     * @param hostName the host player's name.
     * @param gameId   the game room identifier.
     * @return a message indicating the status of starting the game room.
     * @throws RemoteException if a remote error occurs.
     */
    @Override
    public String startGameRoom(String hostName, int gameId) throws RemoteException {
        return multiplayerManager.startGameRoom(hostName, gameId);
    }

    /**
     * Sets the active player for a multi-player game room.
     *
     * @param player the player's name.
     * @param gameId the game room identifier.
     * @return a message indicating the result of marking the player as active.
     * @throws RemoteException if a remote error occurs.
     */
    @Override
    public String setActivePlayer(String player, int gameId) throws RemoteException {
        return multiplayerManager.setActivePlayer(player, gameId);
    }

    /**
     * Removes a player from a multi-player game room.
     *
     * @param player the player's name.
     * @param gameId the game room identifier.
     * @return a message indicating the result of leaving the room.
     * @throws RemoteException if a remote error occurs.
     */
    @Override
    public String leaveRoom(String player, int gameId) throws RemoteException {
        return multiplayerManager.leaveRoom(player, gameId);
    }

    /**
     * Checks if a given game room is active.
     *
     * @param gameId the game room identifier.
     * @return true if the room is active, false otherwise.
     * @throws RemoteException if a remote error occurs.
     */
    @Override
    public boolean isActiveRoom(int gameId) throws RemoteException {
        return multiplayerManager.isActiveRoom(gameId);
    }
    
    /**
     * Checks if a multi-player game is currently running.
     *
     * @param gameId the game room identifier.
     * @return true if the game is running, false otherwise.
     * @throws RemoteException if a remote error occurs.
     */
    @Override
    public boolean isGameRun(int gameId) throws RemoteException {
        return multiplayerManager.isGameRun(gameId);
    }

    /**
     * Runs the multi-player game for a specific room.
     *
     * @param player     the player's name.
     * @param roomId     the game room identifier.
     * @param wordServer the WordRepositoryServer instance.
     * @return a message indicating the game state.
     * @throws RemoteException if a remote error occurs.
     */
    @Override
    public String runGame(String player, int roomId, WordRepositoryServer wordServer) throws RemoteException {
        return multiplayerManager.runGame(player, roomId, wordServer);
    }
    
    /**
     * Processes a guess submitted by a player in a multi-player game.
     *
     * @param gameId the game room identifier.
     * @param player the player's name.
     * @param guess  the guess submitted by the player.
     * @throws RemoteException if a remote error occurs.
     */
    @Override
    public void submitGuess(int gameId, String player, String guess) throws RemoteException {
        multiplayerManager.processGuess(gameId, player, guess);
    }
}

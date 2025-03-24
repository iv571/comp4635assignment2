
import java.io.BufferedReader;
import java.io.FileInputStream;
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
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Implementation of the CrissCrossPuzzleServer interface.
 * <p>
 * This class provides methods for a word puzzle game over RMI, including support for
 * single-player and multiplayer modes. It also includes deduplication for at-most-once
 * semantics using sequence numbers, reconnect logic for a WordRepositoryServer, and
 * failure detection using a configurable FailureDetector.
 * </p>
 *
 * @see CrissCrossPuzzleServer
 */
@SuppressWarnings("serial")
public class CrissCrossImpl extends UnicastRemoteObject implements CrissCrossPuzzleServer {

    // Map to hold a game session for each player
    private Map<String, GameSession> sessions = new ConcurrentHashMap<>();

    // Reference to the WordRepository
    private WordRepositoryServer wordServer;

    // multiplayer manager
    private Multiplayer multiplayerManager = new Multiplayer();
    
    // FailureDetector instance will be created with configurable values.
    private FailureDetector failureDetector;
    
    // [At-most-once] Deduplication maps using composite keys (player:methodName)
    private Map<String, Integer> lastSeenSeq = new ConcurrentHashMap<>();
    private Map<String, Object> lastResponse = new ConcurrentHashMap<>();
    
   
    public CrissCrossImpl(String bankName) throws RemoteException {
        super();
        loadConfigAndInitializeFailureDetector();
        connectToWordRepository();
    }
    
    /**
     * Builds a composite key for deduplication purposes by concatenating the player name and method name.
     *
     * @param player     The player's name.
     * @param methodName The name of the method.
     * @return A composite key string in the form "player:methodName".
     */
    private String getCacheKey(String player, String methodName) {
        return player + ":" + methodName;
    }


    /**
     * Inner class representing a game session for an individual player.
     * <p>
     * This class holds the state of a single game, including the vertical stem,
     * horizontal words, the puzzle grid (both formatted and revealed), and the number
     * of remaining failed attempts.
     * </p>
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
     * Loads configuration from a properties file and initializes the FailureDetector.
     * <p>
     * The configuration file "failureconfig.properties" is read to obtain the tolerance,
     * xFactor, and check interval. Default values are used if the file is not found.
     * </p>
     */
    private void loadConfigAndInitializeFailureDetector() {
        Properties config = new Properties();
        try (FileInputStream fis = new FileInputStream("failureconfig.properties")) {
            config.load(fis);
        } catch (IOException e) {
            System.err.println("Could not load configuration file, using defaults.");
        }
        
        long toleranceMillis = Long.parseLong(config.getProperty("toleranceMillis", "6000"));
        int xFactor = Integer.parseInt(config.getProperty("xFactor", "3"));
        long checkIntervalMillis = Long.parseLong(config.getProperty("checkIntervalMillis", "1000"));
        
        // Pass "this" as the callback reference to the FailureDetector.
        failureDetector = new FailureDetector(toleranceMillis, xFactor, checkIntervalMillis, this);
    }

    /**
     * Static helper method to load configuration and initialize a FailureDetector.
     * <p>
     * This version accepts a ClientCallback parameter.
     * </p>
     *
     * @param callback The callback reference.
     * @return A new FailureDetector instance configured from the properties file.
     */
    public static FailureDetector loadConfigAndInitializeFailureDetector(ClientCallback callback) {
        Properties config = new Properties();
        try (FileInputStream fis = new FileInputStream("failureconfig.properties")) {
            config.load(fis);
        } catch (IOException e) {
            System.err.println("Could not load configuration file, using defaults.");
        }
        
        long toleranceMillis = Long.parseLong(config.getProperty("toleranceMillis", "6000"));
        int xFactor = Integer.parseInt(config.getProperty("xFactor", "3"));
        long checkIntervalMillis = Long.parseLong(config.getProperty("checkIntervalMillis", "1000"));
        
        // Pass "this" as the callback reference to the FailureDetector.
        return new FailureDetector(toleranceMillis, xFactor, checkIntervalMillis, callback);
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
     * Attempt to reconnect to the WordRepositoryServer with multiple retries.
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
     * Adds a word to the repository on behalf of a user.
     * <p>
     * Implements at-most-once semantics using deduplication by checking sequence numbers.
     * If the WordRepositoryServer connection is lost, it attempts to reconnect.
     * </p>
     *
     * @param username The user's name.
     * @param word     The word to add.
     * @param seq      The sequence number for deduplication.
     * @return {@code true} if the word was added successfully; {@code false} otherwise.
     * @throws RemoteException if a remote communication error occurs.
     */
    @Override
    public boolean addWord(String username, String word, int seq) throws RemoteException {
        String key = getCacheKey(username, "addWord");
        Integer last = lastSeenSeq.get(key);
        if (last != null && seq <= last) {
            System.out.println("Duplicate request: addWord('" + word + "') from " + username + " with seq " + seq + " - **IGNORED**");
            return lastResponse.containsKey(key) && lastResponse.get(key) instanceof Boolean 
                   ? (Boolean) lastResponse.get(key) : false;
        }
        if (wordServer == null) {
            connectToWordRepository();
            if (wordServer == null) {
                throw new RemoteException("WordRepositoryServer is not available.");
            }
        }
        boolean result;
        try {
            result = wordServer.createWord(word);
        } catch (RemoteException e) {
            if (e.getMessage() != null && e.getMessage().contains("Connection refused")) {
                System.out.println("Lost connection to WordRepositoryServer, attempting to reconnect...");
                reconnectWordRepository();
                if (wordServer == null) {
                    throw new RemoteException("WordRepositoryServer is unavailable after reconnection attempt.");
                }
                result = wordServer.createWord(word);
            } else {
                throw e;
            }
        }
        lastSeenSeq.put(key, seq);
        lastResponse.put(key, result);
        System.out.println("Processed addWord('" + word + "') for " + username + " [seq=" + seq + "], result=" + result);
        return result;
    }
    
    /**
     * Removes a word from the repository on behalf of a user.
     * <p>
     * Uses deduplication logic similar to {@code addWord}. If the connection is lost,
     * it attempts to reconnect.
     * </p>
     *
     * @param username The user's name.
     * @param word     The word to remove.
     * @param seq      The sequence number for deduplication.
     * @return {@code true} if the word was removed successfully; {@code false} otherwise.
     * @throws RemoteException if a remote communication error occurs.
     */
    @Override
    public boolean removeWord(String username, String word, int seq) throws RemoteException {
        String key = getCacheKey(username, "removeWord");
        Integer last = lastSeenSeq.get(key);
        if (last != null && seq <= last) {
            System.out.println("Duplicate request: removeWord('" + word + "') from " + username + " with seq " + seq + " - **IGNORED**");
            return lastResponse.containsKey(key) && lastResponse.get(key) instanceof Boolean 
                   ? (Boolean) lastResponse.get(key) : false;
        }
        if (wordServer == null) {
            connectToWordRepository();
            if (wordServer == null) {
                throw new RemoteException("WordRepositoryServer is not available.");
            }
        }
        boolean result;
        try {
            result = wordServer.removeWord(word);
        } catch (RemoteException e) {
            if (e.getMessage() != null && e.getMessage().contains("Connection refused")) {
                System.out.println("Lost connection to WordRepositoryServer, attempting to reconnect...");
                reconnectWordRepository();
                if (wordServer == null) {
                    throw new RemoteException("WordRepositoryServer is unavailable after reconnection attempt.");
                }
                result = wordServer.removeWord(word);
            } else {
                throw e;
            }
        }
        lastSeenSeq.put(key, seq);
        lastResponse.put(key, result);
        System.out.println("Processed removeWord('" + word + "') for " + username + " [seq=" + seq + "], result=" + result);
        return result;
    }
    
    
    /**
     * Checks if a word exists in the repository on behalf of a user.
     * <p>
     * Deduplication is performed using sequence numbers. If the connection is lost,
     * reconnection is attempted.
     * </p>
     *
     * @param username The user's name.
     * @param word     The word to check.
     * @param seq      The sequence number for deduplication.
     * @return {@code true} if the word exists; {@code false} otherwise.
     * @throws RemoteException if a remote communication error occurs.
     */
    @Override
    public boolean checkWord(String username, String word, int seq) throws RemoteException {
        String key = getCacheKey(username, "checkWord");
        Integer last = lastSeenSeq.get(key);
        if (last != null && seq <= last) {
            System.out.println("Duplicate request: checkWord('" + word + "') from " + username + " with seq " + seq + " - **IGNORED**");
            return lastResponse.containsKey(key) && lastResponse.get(key) instanceof Boolean 
                   ? (Boolean) lastResponse.get(key) : false;
        }
        if (wordServer == null) {
            connectToWordRepository();
            if (wordServer == null) {
                throw new RemoteException("WordRepositoryServer is not available.");
            }
        }
        boolean result;
        try {
            result = wordServer.checkWord(word);
        } catch (RemoteException e) {
            if (e.getMessage() != null && e.getMessage().contains("Connection refused")) {
                System.out.println("Lost connection to WordRepositoryServer, attempting to reconnect...");
                reconnectWordRepository();
                if (wordServer == null) {
                    throw new RemoteException("WordRepositoryServer is unavailable after reconnection attempt.");
                }
                result = wordServer.checkWord(word);
            } else {
                throw e;
            }
        }
        lastSeenSeq.put(key, seq);
        lastResponse.put(key, result);
        System.out.println("Processed checkWord('" + word + "') for " + username + " [seq=" + seq + "], result=" + result);
        return result;
    }

    /**
     * Returns a random word from the file "words.txt" that meets a minimum length.
     *
     * @param minLength The minimum length of the word.
     * @return A random word meeting the criteria or an empty string if none found.
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
     * Retrieves a random word from "words.txt" that contains a specified character constraint.
     * <p>
     * The method ensures the word has exactly one occurrence of the constraint and that the word
     * can be placed in the puzzle grid given the vertical stem's constraints.
     * </p>
     *
     * @param constraint         The character constraint.
     * @param minLength          The minimum word length.
     * @param verticalStemLength The length of the vertical stem.
     * @param colForStem         The column in which the vertical stem is placed.
     * @return A random word meeting the criteria or an empty string if none found.
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
     * Counts the number of occurrences of a character in a string.
     *
     * @param str The string to search.
     * @param ch  The character to count.
     * @return The number of occurrences of {@code ch} in {@code str}.
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
     * Counts the number of letters in the puzzle grid (i.e. non-placeholder characters).
     *
     * @param puzzle The puzzle grid.
     * @return The count of letters in the grid.
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
     * Formats the puzzle so that unrevealed letters appear as underscores ('_').
     *
     * @param puzzle The puzzle grid.
     * @return A formatted string representation of the puzzle.
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


    // ====================================================
    // Helper methods for synchronizing access to game sessions.
    // ====================================================

    /**
     * Stores a game session for a player.
     *
     * @param player  The player's name.
     * @param session The game session to store.
     */
    private void putSession(String player, GameSession session) {
        synchronized (sessions) {
            sessions.put(player, session);
        }
    }

    /**
     * Retrieves the game session for a player.
     *
     * @param player The player's name.
     * @return The game session associated with the player, or null if none exists.
     */
    private GameSession getSession(String player) {
        synchronized (sessions) {
            return sessions.get(player);
        }
    }

    /**
     * Removes the game session for a player.
     *
     * @param player The player's name.
     */
    private void removeSession(String player) {
        synchronized (sessions) {
            sessions.remove(player);
        }
    }

    /**
     * Starts a new game session for the player.
     * <p>
     * This method registers the player with the failure detector, generates a vertical stem
     * and horizontal words, constructs the puzzle grid, and sets the number of allowed failed attempts.
     * </p>
     *
     * @param player             The player's name.
     * @param level              The difficulty level (also used as minimum word length).
     * @param failedAttemptFactor Factor to determine allowed failed attempts.
     * @param seq                The sequence number for deduplication.
     * @return A string response with the initial puzzle state and allowed attempts.
     * @throws RemoteException if a remote communication error occurs.
     */
    @Override
    public String startGame(String player, int level, int failedAttemptFactor, int seq) throws RemoteException {
        // Register and update the client activity with the failure detector.
        failureDetector.registerClient(player);
        failureDetector.updateClientActivity(player);

        // Deduplication check using sequence numbers.
        String key = getCacheKey(player, "startGame");
        Integer last = lastSeenSeq.get(key);
        if (last != null && seq <= last) {
            System.out.println("Duplicate request: startGame from " + player + " [seq=" + seq + "] - **IGNORED**");
            return lastResponse.containsKey(key) ? (String) lastResponse.get(key) : "Duplicate request ignored.";
        }

        int effectiveLevel = Math.max(1, Math.min(level, 10));
        GameSession session = new GameSession();
        String candidate;
        do {
            candidate = getRandomWordFromFile(effectiveLevel);
        } while (candidate.length() < effectiveLevel);
        session.verticalStem = candidate.toLowerCase();
        int verticalStemLength = candidate.length();
        int numCols = verticalStemLength;
        int colForStem = numCols / 2;
        session.horizontalWords = new String[effectiveLevel];
        Arrays.fill(session.horizontalWords, "");
        for (int i = 1; i < effectiveLevel && i < verticalStemLength; i++) {
            String hWord;
            do {
                hWord = getConstrainedRandomWord(session.verticalStem.charAt(i), effectiveLevel, verticalStemLength, colForStem);
            } while (hWord.isEmpty());
            session.horizontalWords[i] = hWord.toLowerCase();
        }
        session.puzzle = constructPuzzle(session.verticalStem, session.horizontalWords);
        int numLetters = countPuzzleLetters(session.puzzle);
        session.failAttempts = failedAttemptFactor * numLetters;
        session.formattedPuzzle = formatPuzzle(session.puzzle);
        session.revealedPuzzle = revealPuzzle(session.puzzle);
        putSession(player, session);
        System.out.println("Completed puzzle on server:");
        System.out.println(session.revealedPuzzle);
        String response = "Game started for " + player + "!\n" + session.formattedPuzzle +
                          "\nAttempts allowed: " + session.failAttempts;
        // Store the sequence and response for deduplication.
        lastSeenSeq.put(key, seq);
        lastResponse.put(key, response);
        System.out.println("Processed startGame for " + player + " [seq=" + seq + "]");
        return response;
    }
    
    

    /**
     * Processes a letter guess from the player.
     * <p>
     * If the guessed letter exists in the solution and has not been revealed yet, it is revealed.
     * Otherwise, the allowed attempt counter is decremented and the game may end if attempts run out.
     * </p>
     *
     * @param player The player's name.
     * @param letter The guessed letter.
     * @param seq    The sequence number for deduplication.
     * @return A string representing the current state of the puzzle or a game over message.
     * @throws RemoteException if a remote communication error occurs.
     */
    @Override
    public String guessLetter(String player, char letter, int seq) throws RemoteException {
        String key = getCacheKey(player, "guessLetter");
        Integer last = lastSeenSeq.get(key);
        if (last != null && seq <= last) {
            System.out.println("Duplicate request: guessLetter('" + letter + "') from " + player + " [seq=" + seq + "] - **IGNORED**");
            return lastResponse.containsKey(key) ? (String) lastResponse.get(key) : "Duplicate request ignored.";
        }
        GameSession session = getSession(player);
        if (session == null) {
            String result = "No active game session for " + player + ".";
            lastSeenSeq.put(key, seq);
            lastResponse.put(key, result);
            System.out.println("Processed guessLetter for " + player + " [seq=" + seq + "] - no active session.");
            return result;
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
                    accountServer.updateScore(player, -1, false);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                removeSession(player);
                String result = "Game over! No attempts remaining. The solution was:\n" + session.revealedPuzzle;
                lastSeenSeq.put(key, seq);
                lastResponse.put(key, result);
                System.out.println("Processed guessLetter for " + player + " [seq=" + seq + "] - game over.");
                return result;
            }
        }
        session.formattedPuzzle = new String(formattedChars);
        if (!session.formattedPuzzle.contains("_")) {
            removeSession(player);
            try {
                Registry registry = LocateRegistry.getRegistry("localhost", 1099);
                UserAccountServer accountServer = (UserAccountServer) registry.lookup("UserAccountServer");
                accountServer.updateScore(player, 1, false);
            } catch (Exception e) {
                e.printStackTrace();
            }
            String result = "Congratulations " + player + ", you completed the puzzle!\n" + session.formattedPuzzle;
            lastSeenSeq.put(key, seq);
            lastResponse.put(key, result);
            System.out.println("Processed guessLetter for " + player + " [seq=" + seq + "] - puzzle solved.");
            return result;
        }
        String result = "Current puzzle state:\n" + session.formattedPuzzle +
                        "\nAttempts remaining: " + session.failAttempts;
        lastSeenSeq.put(key, seq);
        lastResponse.put(key, result);
        System.out.println("Processed guessLetter for " + player + " [seq=" + seq + "] - letter " + (found ? "found" : "not found") + ".");
        return result;
    }
    

    /**
     * Processes a word guess from the player.
     * <p>
     * If the guessed word matches the vertical stem or one of the horizontal words,
     * the corresponding parts of the puzzle are revealed. If not, the allowed attempts
     * are decremented and the game may end.
     * </p>
     *
     * @param player The player's name.
     * @param word   The guessed word.
     * @param seq    The sequence number for deduplication.
     * @return A string with the updated puzzle state or a game over message.
     * @throws RemoteException if a remote communication error occurs.
     */
    @Override
    public String guessWord(String player, String word, int seq) throws RemoteException {
        String key = getCacheKey(player, "guessWord");
        Integer last = lastSeenSeq.get(key);
        if (last != null && seq <= last) {
            System.out.println("Duplicate request: guessWord(\"" + word + "\") from " + player + " [seq=" + seq + "] - **IGNORED**");
            return lastResponse.containsKey(key) ? (String) lastResponse.get(key) : "Duplicate request ignored.";
        }
        GameSession session = getSession(player);
        if (session == null) {
            String result = "No active game session for " + player + ".";
            lastSeenSeq.put(key, seq);
            lastResponse.put(key, result);
            System.out.println("Processed guessWord for " + player + " [seq=" + seq + "] - no active session.");
            return result;
        }
        String lowerWord = word.toLowerCase();
        boolean wordFound = false;
        int gridWidth = session.puzzle[0].length;
        int colForStem = gridWidth / 2;
        String[] rows = session.formattedPuzzle.split("\\+\\n");
        if (lowerWord.equals(session.verticalStem.toLowerCase())) {
            for (int i = 0; i < rows.length && i < session.puzzle.length; i++) {
                char[] rowChars = rows[i].toCharArray();
                if (colForStem < rowChars.length) {
                    rowChars[colForStem] = session.puzzle[i][colForStem];
                }
                rows[i] = new String(rowChars);
            }
            session.formattedPuzzle = String.join("+\n", rows) + "+";
            session.formattedPuzzle = session.formattedPuzzle.replace("++", "+");
            wordFound = true;
        } else {
            for (int i = 0; i < session.horizontalWords.length; i++) {
                String hWord = session.horizontalWords[i];
                if (hWord.isEmpty()) continue;
                if (hWord.equalsIgnoreCase(lowerWord)) {
                    char constraint = session.verticalStem.charAt(i);
                    int constraintIndex = hWord.toLowerCase().indexOf(Character.toLowerCase(constraint));
                    if (constraintIndex == -1) continue;
                    int startCol = colForStem - constraintIndex;
                    startCol = Math.max(0, Math.min(startCol, gridWidth - hWord.length()));
                    if (i >= rows.length) continue;
                    char[] rowChars = rows[i].toCharArray();
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
            if (!session.formattedPuzzle.contains("_")) {
                removeSession(player);
                try {
                    Registry registry = LocateRegistry.getRegistry("localhost", 1099);
                    UserAccountServer accountServer = (UserAccountServer) registry.lookup("UserAccountServer");
                    accountServer.updateScore(player, 1, false);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                String result = "Congratulations " + player + ", you completed the puzzle!\n" + session.formattedPuzzle;
                lastSeenSeq.put(key, seq);
                lastResponse.put(key, result);
                System.out.println("Processed guessWord for " + player + " [seq=" + seq + "] - puzzle solved.");
                return result;
            }
            String result = "Word correct!\nCurrent puzzle state:\n" + session.formattedPuzzle +
                            "\nAttempts remaining: " + session.failAttempts;
            lastSeenSeq.put(key, seq);
            lastResponse.put(key, result);
            System.out.println("Processed guessWord for " + player + " [seq=" + seq + "] - word found.");
            return result;
        } else {
            session.failAttempts--;
            if (session.failAttempts <= 0) {
                try {
                    Registry registry = LocateRegistry.getRegistry("localhost", 1099);
                    UserAccountServer accountServer = (UserAccountServer) registry.lookup("UserAccountServer");
                    accountServer.updateScore(player, -1, false);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                removeSession(player);
                String result = "Game over! No attempts remaining. The solution was:\n" + session.revealedPuzzle;
                lastSeenSeq.put(key, seq);
                lastResponse.put(key, result);
                System.out.println("Processed guessWord for " + player + " [seq=" + seq + "] - game over.");
                return result;
            }
            String result = "Sorry, the word \"" + word + "\" is not in the puzzle.\nAttempts remaining: " 
                            + session.failAttempts;
            lastSeenSeq.put(key, seq);
            lastResponse.put(key, result);
            System.out.println("Processed guessWord for " + player + " [seq=" + seq + "] - word not found.");
            return result;
        }
    }
    
    /**
     * Ends the current game session for the player.
     * <p>
     * The method unregisters the player from the failure detector and returns the solution.
     * </p>
     *
     * @param player The player's name.
     * @param seq    The sequence number for deduplication.
     * @return A string containing the final puzzle state or a message if no session exists.
     * @throws RemoteException if a remote communication error occurs.
     */
    @Override
    public String endGame(String player, int seq) throws RemoteException {
    	failureDetector.updateClientActivity(player);
        String key = getCacheKey(player, "endGame");
        Integer last = lastSeenSeq.get(key);
        if (last != null && seq <= last) {
            System.out.println("Duplicate request: endGame from " + player + " [seq=" + seq + "] - **IGNORED**");
            return lastResponse.containsKey(key) ? (String) lastResponse.get(key) : "Duplicate request ignored.";
        }
        GameSession session = sessions.remove(player);
        String result;
        if (session == null) {
            result = "No active game session for " + player + ".";
        } else {
            result = "Game ended for " + player + ".\nThe solution was:\n" + session.revealedPuzzle;
        }
        lastSeenSeq.put(key, seq);
        lastResponse.put(key, result);
        System.out.println("Processed endGame for " + player + " [seq=" + seq + "]");
        
        failureDetector.unregisterClient(player);
        return result;
    }

    /**
     * Restarts the game for the player.
     * <p>
     * This method removes any existing session and starts a new game by invoking startGame.
     * </p>
     *
     * @param player The player's name.
     * @param seq    The sequence number for deduplication.
     * @return A string response with the new puzzle state.
     * @throws RemoteException if a remote communication error occurs.
     */
    @Override
    public synchronized String restartGame(String player, int seq) throws RemoteException {
        String key = getCacheKey(player, "restartGame");
        Integer last = lastSeenSeq.get(key);
        if (last != null && seq <= last) {
            System.out.println("Duplicate request: restartGame from " + player + " [seq=" + seq + "] - **IGNORED**");
            return lastResponse.containsKey(key) ? (String) lastResponse.get(key) : "Duplicate request ignored.";
        }
        sessions.remove(player);
        String result = startGame(player, 5, 3, seq);
        System.out.println("Processed restartGame for " + player + " [seq=" + seq + "]");
        return result;
    }

    // ====================================================
    // Multiplayer-related methods (delegated to Multiplayer manager)
    // ====================================================
    
    
    @Override
    public String startMultiGame(String username, int numPlayers, int level)
            throws RemoteException, RejectedException {
        return multiplayerManager.startMultiGame(username, numPlayers, level);
    }

    @Override
    public String joinMultiGame(String player, int gameId, ClientCallback callback)
            throws RemoteException, RejectedException {
        // Delegate to the multiplayerManager instance.
        return multiplayerManager.joinMultiGame(player, gameId, callback);
    }

    @Override
    public String showActiveGameRooms() throws RemoteException {
        return multiplayerManager.showActiveGameRooms();
    }

    @Override
    public String startGameRoom(String hostName, int gameId) throws RemoteException {
        return multiplayerManager.startGameRoom(hostName, gameId);
    }

    @Override
    public String setActivePlayer(String player, int gameId) throws RemoteException {
        return multiplayerManager.setActivePlayer(player, gameId);
    }

    @Override
    public String leaveRoom(String player, int gameId) throws RemoteException {
        return multiplayerManager.leaveRoom(player, gameId);
    }

    @Override
    public boolean isActiveRoom(int gameId) throws RemoteException {
        return multiplayerManager.isActiveRoom(gameId);
    }

    @Override
    public synchronized boolean isGameRun(int gameId) throws RemoteException {
        return multiplayerManager.isGameRun(gameId);
    }

    
    /**
     * Releases the game state for a client by removing their session.
     *
     * @param clientName The client's name.
     */
    @Override
    public String runGame(String player, int roomId, WordRepositoryServer wordServer) throws RemoteException {
        return multiplayerManager.runGame(player, roomId, wordServer);
    }

    /**
     * Processes a heartbeat from a client.
     * <p>
     * This method updates the client's activity in the FailureDetector.
     * </p>
     *
     * @param client The client identifier.
     * @throws RemoteException if a remote communication error occurs.
     */
    @Override
    public void heartbeat(String client) throws RemoteException {
        failureDetector.updateClientActivity(client);
        System.out.println("Received heartbeat from " + client);
    }
    
    
    /**
     * Releases the game state for a client by removing their session.
     *
     * @param clientName The client's name.
     */
    public void releaseGameState(String clientName) {
        removeSession(clientName);
        System.out.println("Released game state for " + clientName);
    }

	
    /**
     * Checks if a room ID is valid within the multiplayer manager.
     *
     * @param roomID The room ID to check.
     * @return {@code true} if the room ID is valid; {@code false} otherwise.
     * @throws RemoteException if a remote communication error occurs.
     */
    public boolean isValidRoomID(int roomID) throws RemoteException {
        return multiplayerManager.isValidRoomID(roomID);
    }
}

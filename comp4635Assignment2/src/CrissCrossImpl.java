
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

    public CrissCrossImpl(String bankName) throws RemoteException {
        super();
        loadConfigAndInitializeFailureDetector();
        connectToWordRepository();
    }

    // Inner class to represent a game session per player.
    private class GameSession {
        String verticalStem;
        String[] horizontalWords;
        char[][] puzzle;
        String formattedPuzzle;
        String revealedPuzzle;
        int failAttempts;
        // Additional game state (e.g., score) could be added here.
    }
    
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

    // Helper methods to synchronize access to sessions.
    private void putSession(String player, GameSession session) {
        synchronized (sessions) {
            sessions.put(player, session);
        }
    }

    private GameSession getSession(String player) {
        synchronized (sessions) {
            return sessions.get(player);
        }
    }

    private void removeSession(String player) {
        synchronized (sessions) {
            sessions.remove(player);
        }
    }

    @Override
    public String startGame(String player, int level, int failedAttemptFactor) throws RemoteException {
    	failureDetector.registerClient(player);
        failureDetector.updateClientActivity(player);
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
                    accountServer.updateScore(player, -1, false);
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
                accountServer.updateScore(player, 1, false);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return "Congratulations " + player + ", you completed the puzzle!\n" + session.formattedPuzzle;
        }
        return "Current puzzle state:\n" + session.formattedPuzzle +
                "\nAttempts remaining: " + session.failAttempts;
    }

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
                    accountServer.updateScore(player, 1, false);
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
                    accountServer.updateScore(player, -1, false);
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

    @Override
    public String endGame(String player) throws RemoteException {
    	failureDetector.updateClientActivity(player);
        GameSession session = sessions.remove(player);
        if (session == null) {
            return "No active game session for " + player + ".";
        }
        failureDetector.unregisterClient(player);
        return "Game ended for " + player + ".\nThe solution was:\n" + session.revealedPuzzle;
    }

    @Override
    public synchronized String restartGame(String player) throws RemoteException {
        sessions.remove(player);
        // Restart with default parameters (adjust as needed)
        return startGame(player, 5, 3);
    }

    @Override
    public synchronized String startMultiGame(String username, int numPlayers, int level)
            throws RemoteException, RejectedException {
        return multiplayerManager.startMultiGame(username, numPlayers, level);
    }

    @Override
    public synchronized String joinMultiGame(String player, int gameId, ClientCallback callback)
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
    public boolean isGameRun(int gameId) throws RemoteException {
        return multiplayerManager.isGameRun(gameId);
    }

    @Override
    public String runGame(String player, int roomId, WordRepositoryServer wordServer) throws RemoteException {
        return multiplayerManager.runGame(player, roomId, wordServer);
    }

    @Override
    public void heartbeat(String client) throws RemoteException {
        failureDetector.updateClientActivity(client);
        System.out.println("Received heartbeat from " + client);
    }
    
    public void releaseGameState(String clientName) {
        removeSession(clientName);
        System.out.println("Released game state for " + clientName);
    }

	
    
}

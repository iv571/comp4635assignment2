

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
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

@SuppressWarnings("serial")
public class CrissCrossImpl extends UnicastRemoteObject implements CrissCrossPuzzleServer {
    private String bankName;
    private Map<String, Account> accounts = new HashMap<>();
 // Map to hold a game session for each player
    private Map<String, GameSession> sessions = new ConcurrentHashMap<>();

    public CrissCrossImpl(String bankName) throws RemoteException {
        super();
        this.bankName = bankName;
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
    
    private String getRandomWordFromFile(int minLength) {
        List<String> words = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader("words.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
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

    private String getConstrainedRandomWord(char constraint, int minLength) {
        List<String> words = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader("words.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && line.length() >= minLength &&
                    line.toLowerCase().indexOf(Character.toLowerCase(constraint)) >= 0) {
                    words.add(line);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading words.txt: " + e.getMessage());
        }
        if (words.isEmpty()) {
            return getRandomWordFromFile(minLength);
        }
        Random rand = new Random();
        return words.get(rand.nextInt(words.size()));
    }

    private char[][] constructPuzzle(String verticalStem, String[] horizontalWords) {
        int numRows = verticalStem.length();
        int numCols = 20;
        char[][] grid = new char[numRows][numCols];
        // Initialize grid with '.' characters.
        for (int i = 0; i < numRows; i++) {
            Arrays.fill(grid[i], '.');
        }
        // Place the vertical stem in a fixed column (here, column 10)
        int colForStem = 10;
        for (int row = 0; row < verticalStem.length(); row++) {
            grid[row][colForStem] = verticalStem.charAt(row);
        }
        // Place horizontal words ensuring they cross the vertical stem.
        for (int row = 0; row < horizontalWords.length && row < numRows; row++) {
            String hWord = horizontalWords[row];
            char constraint = verticalStem.charAt(row);
            int constraintIndex = hWord.toLowerCase().indexOf(Character.toLowerCase(constraint));
            if (constraintIndex < 0) continue; // skip if letter not found
            int startCol = colForStem - constraintIndex;
            if (startCol < 0) {
                startCol = 0;
            } else if (startCol + hWord.length() > numCols) {
                startCol = numCols - hWord.length();
            }
            for (int j = 0; j < hWord.length() && (startCol + j) < numCols; j++) {
                int currentCol = startCol + j;
                char existingChar = grid[row][currentCol];
                char newChar = hWord.charAt(j);
                if (existingChar == '.' || existingChar == newChar) {
                    grid[row][currentCol] = newChar;
                } else {
                    System.err.println("Conflict at row " + row + ", col " + currentCol);
                    // In case of conflict, we simply keep the existing character.
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


	@Override
	public String startGame(String player, int level, int failedAttemptFactor) throws RemoteException {
		// Clamp the level: if level <= 0 then use 1; if level > 10 then use 10.
	    int effectiveLevel = Math.max(1, Math.min(level, 10));

	    GameSession session = new GameSession();

	    // Force the vertical stem to be exactly 'effectiveLevel' characters.
	    String candidate = getRandomWordFromFile(effectiveLevel);
	    // In case the candidate is too short, keep trying.
	    while (candidate.length() < effectiveLevel) {
	        candidate = getRandomWordFromFile(effectiveLevel);
	    }
	    if (candidate.length() > effectiveLevel) {
	        candidate = candidate.substring(0, effectiveLevel);
	    }
	    session.verticalStem = candidate;

	    // Create an array for horizontal words of length equal to the vertical stem.
	    // Total words = 1 (vertical) + (effectiveLevel - 1) horizontal = effectiveLevel words.
	    session.horizontalWords = new String[effectiveLevel];
	    // Initialize all entries to empty strings.
	    for (int i = 0; i < effectiveLevel; i++) {
	        session.horizontalWords[i] = "";
	    }
	    // For visual appeal, fill rows 1 to effectiveLevel-1 with horizontal words.
	    // (You can adjust which row(s) get horizontal words if desired.)
	    for (int i = 1; i < effectiveLevel; i++) {
	        session.horizontalWords[i] = getConstrainedRandomWord(session.verticalStem.charAt(i), effectiveLevel);
	    }
	    
	    // Construct the puzzle using your existing method.
	    session.puzzle = constructPuzzle(session.verticalStem, session.horizontalWords);
	    
	    // Count the total number of letters in the puzzle.
	    int numLetters = countPuzzleLetters(session.puzzle);
	    // Allowed fail attempts is 3 times the total number of letters.
	    session.failAttempts = 3 * numLetters;
	    
	    session.formattedPuzzle = formatPuzzle(session.puzzle);
	    session.revealedPuzzle = revealPuzzle(session.puzzle);
	    sessions.put(player, session);
	    
	    // Print the completed (revealed) puzzle on the server side.
	    System.out.println("Completed puzzle on server:");
	    System.out.println(session.revealedPuzzle);
	    
	    return "Game started for " + player + "!\n" +
	           session.formattedPuzzle +
	           "\nAttempts allowed: " + session.failAttempts;
	}

	@Override
	public String guessLetter(String player, char letter) throws RemoteException {
		GameSession session = sessions.get(player);
        if (session == null) {
            return "No active game session for " + player + ".";
        }
        boolean found = false;
        char[] formattedChars = session.formattedPuzzle.toCharArray();
        char[] revealedChars = session.revealedPuzzle.toCharArray();
        for (int i = 0; i < revealedChars.length; i++) {
            if (revealedChars[i] == letter && formattedChars[i] == '_') {
                formattedChars[i] = letter;
                found = true;
            }
        }
        if (!found) {
            session.failAttempts--;
            if (session.failAttempts <= 0) {
                sessions.remove(player);
                return "Game over! No attempts remaining. The solution was:\n" + session.revealedPuzzle;
            }
        }
        session.formattedPuzzle = String.valueOf(formattedChars);
        if (!session.formattedPuzzle.contains("_")) {
            sessions.remove(player);
            return "Congratulations " + player + ", you completed the puzzle!\n" + session.formattedPuzzle;
        }
        return "Current puzzle state:\n" + session.formattedPuzzle +
               "\nAttempts remaining: " + session.failAttempts;
	}

	@Override
	public String guessWord(String player, String word) throws RemoteException {
		GameSession session = sessions.get(player);
        if (session == null) {
            return "No active game session for " + player + ".";
        }
        String lowerRevealed = session.revealedPuzzle.toLowerCase();
        String lowerWord = word.toLowerCase();
        int index = lowerRevealed.indexOf(lowerWord);
        if (index != -1) {
            char[] formattedChars = session.formattedPuzzle.toCharArray();
            char[] revealedChars = session.revealedPuzzle.toCharArray();
            for (int i = index; i < index + word.length(); i++) {
                formattedChars[i] = revealedChars[i];
            }
            session.formattedPuzzle = String.valueOf(formattedChars);
        } else {
            session.failAttempts--;
            if (session.failAttempts <= 0) {
                sessions.remove(player);
                return "Game over! No attempts remaining. The solution was:\n" + session.revealedPuzzle;
            }
            return "Sorry, the word \"" + word + "\" is not in the puzzle.\nAttempts remaining: " + session.failAttempts;
        }
        if (!session.formattedPuzzle.contains("_")) {
            sessions.remove(player);
            return "Congratulations " + player + ", you completed the puzzle!\n" + session.formattedPuzzle;
        }
        return "Word correct!\nCurrent puzzle state:\n" + session.formattedPuzzle +
               "\nAttempts remaining: " + session.failAttempts;
	}

	@Override
	public String endGame(String player) throws RemoteException {
		GameSession session = sessions.remove(player);
        if (session == null) {
            return "No active game session for " + player + ".";
        }
        return "Game ended for " + player + ".\nThe solution was:\n" + session.revealedPuzzle;
	}

	@Override
	public String restartGame(String player) throws RemoteException {
		sessions.remove(player);
        // Restart with default parameters (adjust as needed)
        return startGame(player, 5, 3);
	}

	@Override
	public String addWord(String word) throws RemoteException {
		 try {
	            // Locate the RMI registry (adjust host and port as needed)
	            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
	            // Look up the remote WordRepositoryServer object by its bound name
	            WordRepositoryServer wordRepo = (WordRepositoryServer) registry.lookup("WordRepositoryServer");
	            // Invoke the remote method to add (create) the word
	            boolean success = wordRepo.createWord(word);
	            return success ? "Word added successfully." : "Failed to add word.";
	        } catch (Exception e) {
	            e.printStackTrace();
	            return "Error while adding word: " + e.getMessage();
	        }
	}

	@Override
	public String removeWord(String word) throws RemoteException {
		try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            WordRepositoryServer wordRepo = (WordRepositoryServer) registry.lookup("WordRepositoryServer");
            boolean success = wordRepo.removeWord(word);
            return success ? "Word removed successfully." : "Failed to remove word.";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error while removing word: " + e.getMessage();
        }
	}

	@Override
	public String checkWord(String word) throws RemoteException {
		 try {
	            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
	            WordRepositoryServer wordRepo = (WordRepositoryServer) registry.lookup("WordRepositoryServer");
	            boolean exists = wordRepo.checkWord(word);
	            return exists ? "Word exists in the repository." : "Word does not exist in the repository.";
	        } catch (Exception e) {
	            e.printStackTrace();
	            return "Error while checking word: " + e.getMessage();
	        }
	}
	
	
}

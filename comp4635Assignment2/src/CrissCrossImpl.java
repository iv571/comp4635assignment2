

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
   
 // Map to hold a game session for each player
    private Map<String, GameSession> sessions = new ConcurrentHashMap<>();

    public CrissCrossImpl(String bankName) throws RemoteException {
        super();
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

    private String getConstrainedRandomWord(char constraint, int minLength) {
    	 List<String> words = new ArrayList<>();
         try (BufferedReader br = new BufferedReader(new FileReader("words.txt"))) {
             String line;
             while ((line = br.readLine()) != null) {
                 line = line.trim().toLowerCase();
                 if (!line.isEmpty() && line.length() >= minLength &&
                     line.indexOf(Character.toLowerCase(constraint)) >= 0) {
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

    private char[][] constructPuzzle(String verticalStem, String[] horizontalWords) {
        int numRows = verticalStem.length();
        int numCols = 20;
        char[][] grid = new char[numRows][numCols];
        for (int i = 0; i < numRows; i++) {
            Arrays.fill(grid[i], '.');
        }
        int colForStem = 10;
        for (int row = 0; row < verticalStem.length(); row++) {
            grid[row][colForStem] = verticalStem.charAt(row);
        }
        for (int row = 0; row < horizontalWords.length && row < numRows; row++) {
            String hWord = horizontalWords[row];
            if (hWord.isEmpty()) continue;
            char constraint = verticalStem.charAt(row);
            int constraintIndex = hWord.indexOf(Character.toLowerCase(constraint));
            if (constraintIndex < 0) continue;
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


	@Override
	public String startGame(String player, int level, int failedAttemptFactor) throws RemoteException {
		   int effectiveLevel = Math.max(1, Math.min(level, 10));
	        int verticalStemLength = (effectiveLevel < 7) ? 7 : (new Random().nextInt(effectiveLevel - 7 + 1) + 7);

	        GameSession session = new GameSession();

	        String candidate;
	        do {
	            candidate = getRandomWordFromFile(verticalStemLength);
	        } while (candidate.length() < verticalStemLength);
	        candidate = candidate.substring(0, verticalStemLength).toLowerCase();
	        session.verticalStem = candidate;

	        session.horizontalWords = new String[verticalStemLength];
	        Arrays.fill(session.horizontalWords, "");
	        for (int i = 1; i < effectiveLevel && i < verticalStemLength; i++) {
	            String hWord;
	            do {
	                hWord = getConstrainedRandomWord(session.verticalStem.charAt(i), effectiveLevel);
	            } while (hWord.isEmpty());
	            session.horizontalWords[i] = hWord.toLowerCase();
	        }

	        session.puzzle = constructPuzzle(session.verticalStem, session.horizontalWords);
	        int numLetters = countPuzzleLetters(session.puzzle);
	        session.failAttempts = failedAttemptFactor * numLetters;

	        session.formattedPuzzle = formatPuzzle(session.puzzle);
	        session.revealedPuzzle = revealPuzzle(session.puzzle);
	        sessions.put(player, session);

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
	                sessions.remove(player);
	                return "Game over! No attempts remaining. The solution was:\n" + session.revealedPuzzle;
	            }
	        }
	        session.formattedPuzzle = new String(formattedChars);
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
	        String lowerWord = word.toLowerCase();
	        boolean wordFound = false;

	        if (lowerWord.equals(session.verticalStem.toLowerCase())) {
	            int colForStem = 10;
	            String[] rows = session.formattedPuzzle.split("\\+\\n");
	            for (int i = 0; i < rows.length; i++) {
	                if (i >= session.puzzle.length) break;
	                char[] rowChars = rows[i].toCharArray();
	                if (colForStem < rowChars.length) {
	                    rowChars[colForStem] = session.puzzle[i][colForStem];
	                    rows[i] = new String(rowChars);
	                }
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
	                    int startCol = 10 - constraintIndex;
	                    startCol = Math.max(0, Math.min(startCol, 20 - hWord.length()));
	                    String[] rows = session.formattedPuzzle.split("\\+\\n");
	                    if (i >= rows.length) continue;
	                    char[] rowChars = rows[i].toCharArray();
	                    for (int j = 0; j < hWord.length() && (startCol + j) < 20; j++) {
	                        rowChars[startCol + j] = hWord.charAt(j);
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
	                sessions.remove(player);
	                return "Congratulations " + player + ", you completed the puzzle!\n" + session.formattedPuzzle;
	            }
	            return "Word correct!\nCurrent puzzle state:\n" + session.formattedPuzzle +
	                   "\nAttempts remaining: " + session.failAttempts;
	        } else {
	            session.failAttempts--;
	            if (session.failAttempts <= 0) {
	                sessions.remove(player);
	                return "Game over! No attempts remaining. The solution was:\n" + session.revealedPuzzle;
	            }
	            return "Sorry, the word \"" + word + "\" is not in the puzzle.\nAttempts remaining: " + session.failAttempts;
	        }
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
	        // Obtain the registry on localhost at port 1099.
	        Registry registry = LocateRegistry.getRegistry("localhost", 1099);
	        // Look up the remote object using its bound name.
	        WordRepositoryServer wordRepo = (WordRepositoryServer) registry.lookup("WordRepositoryServer");
	        // Call the remote method to create the word.
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of the UserAccountServer interface.
 * <p>
 * This class manages user accounts by storing account information in an in-memory map
 * and persisting it to a file. It provides functionality for account creation, login,
 * and score management (both individual and multiplayer scores). Passwords are hashed
 * using SHA-256 for security.
 * </p>
 */

public class UserAccountImpl extends UnicastRemoteObject implements UserAccountServer {

    private static final String ACCOUNTS_FILE = "accounts.txt";
    
    // In-memory maps for accounts and scores.
    // Accounts map now stores username -> hashedPassword
    private Map<String, String> accounts = new HashMap<>();
    private Map<String, Integer> scores = new ConcurrentHashMap<>();
 // New field: multiplayerScores stores scores for multiplayer sessions
    private Map<String, Integer> multiplayerScores = new ConcurrentHashMap<>();
    private static final String MULTIPLAYER_SCORES_FILE = "multiplayer_scores.txt";
    


    // Load accounts from file upon instantiation.
    protected UserAccountImpl() throws RemoteException {
        super();
        loadAccountsFromFile();
    }

    /**
     * Hashes the provided password using the SHA-256 algorithm.
     *
     * @param password the plain text password.
     * @return the SHA-256 hash of the password in hexadecimal format.
     * @throws RuntimeException if the SHA-256 algorithm is not available.
     */
    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            // Convert bytes to hex format
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Creates a new account for the specified username with the given password.
     * <p>
     * The password is stored as a hash. If the account already exists, this method returns false.
     * </p>
     *
     * @param username the username for the new account.
     * @param password the plain text password.
     * @return true if the account was created successfully; false if the account already exists.
     * @throws RemoteException if a remote error occurs.
     */
    @Override
    public synchronized boolean createAccount(String username, String password) throws RemoteException {
        if (accounts.containsKey(username)) {
            return false; // Account already exists.
        }
        // Store the hashed password
        String hashed = hashPassword(password);
        accounts.put(username, hashed);
        // Initialize score for a new account to zero.
        scores.put(username, 0);
        System.out.println("Created account for " + username);
        saveAccountsToFile();
        return true;
    }

    /**
     * Logs in the user by verifying the provided password against the stored hash.
     *
     * @param username the username.
     * @param password the plain text password.
     * @return true if login is successful; false otherwise.
     * @throws RemoteException if a remote error occurs.
     */
    @Override
    public synchronized boolean loginAccount(String username, String password) throws RemoteException {
        // Hash the provided password and compare it with the stored hash.
        String hashed = hashPassword(password);
        if (accounts.containsKey(username) && accounts.get(username).equals(hashed)) {
            System.out.println("User " + username + " logged in successfully.");
            return true;
        }
        return false;
    }
    
    /**
     * Updates the individual score for the specified user by the given delta.
     *
     * @param username the username.
     * @param delta    the change in score (can be positive or negative).
     * @throws RemoteException if a remote error occurs.
     */
    @Override
    public synchronized void updateScore(String username, int delta) throws RemoteException {
        int newScore = scores.getOrDefault(username, 0) + delta;
        scores.put(username, newScore);
        System.out.println("Updated score for " + username + " by " + delta 
                + ". New score: " + newScore);
        saveAccountsToFile();
    }
    
    /**
     * Retrieves the individual score for the specified user.
     *
     * @param username the username.
     * @return the user's individual score.
     * @throws RemoteException if a remote error occurs.
     */
    @Override
    public synchronized int getScore(String username) throws RemoteException {
        return scores.getOrDefault(username, 0);
    }
    
    /**
     * Retrieves the sorted scoreboard for individual scores.
     * <p>
     * The scoreboard is sorted in descending order of scores.
     * </p>
     *
     * @return an unmodifiable map of usernames to scores.
     * @throws RemoteException if a remote error occurs.
     */
    @Override
    public synchronized Map<String, Integer> getScoreboard() throws RemoteException {
        // Create a list from the entries in the scores map.
        List<Map.Entry<String, Integer>> list = new ArrayList<>(scores.entrySet());
        
        // Sort the list in descending order by score.
        Collections.sort(list, (e1, e2) -> e2.getValue().compareTo(e1.getValue()));
        
        // Create a LinkedHashMap to preserve the sorted order.
        Map<String, Integer> sortedMap = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : list) {
            sortedMap.put(entry.getKey(), entry.getValue());
        }
        
        return Collections.unmodifiableMap(sortedMap);
    }
    
    
    /**
     * Retrieves a combined scoreboard that includes both individual and multiplayer scores.
     * <p>
     * Multiplayer scores are first read from file and then combined with the individual scores.
     * The combined scoreboard is sorted in descending order by total score.
     * </p>
     *
     * @return an unmodifiable map where each key is a username and the value is a string detailing
     *         the individual and multiplayer scores.
     * @throws RemoteException if a remote error occurs.
     */
    @Override
    public synchronized Map<String, String> getCombinedScoreboard() throws RemoteException {
        // First, read multiplayer scores from the file.
        Map<String, Integer> fileMultiplayerScores = new HashMap<>();
        File file = new File(MULTIPLAYER_SCORES_FILE);
        if (file.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                // Each line is formatted as "username;multiplayerScore"
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split(";");
                    if (parts.length >= 2) {
                        String username = parts[0].trim();
                        int mScore = Integer.parseInt(parts[1].trim());
                        fileMultiplayerScores.put(username, mScore);
                    }
                }
            } catch (IOException e) {
                System.err.println("Error reading " + MULTIPLAYER_SCORES_FILE + ": " + e.getMessage());
            }
        }

        // Combine individual scores (from the in-memory scores map) with the multiplayer scores from file.
        Map<String, Integer> totalScores = new HashMap<>();
        for (String username : accounts.keySet()) {
            int individual = scores.getOrDefault(username, 0);
            int multiplayer = fileMultiplayerScores.getOrDefault(username, 0);
            totalScores.put(username, individual + multiplayer);
        }

        // Sort users by total score (descending) and build a sorted combined scoreboard.
        List<Map.Entry<String, Integer>> list = new ArrayList<>(totalScores.entrySet());
        Collections.sort(list, (e1, e2) -> e2.getValue().compareTo(e1.getValue()));

        Map<String, String> sortedCombined = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : list) {
            String username = entry.getKey();
            int individual = scores.getOrDefault(username, 0);
            int multiplayer = fileMultiplayerScores.getOrDefault(username, 0);
            sortedCombined.put(username, "Individual: " + individual + ", Multiplayer: " + multiplayer);
        }

        return Collections.unmodifiableMap(sortedCombined);
    }
    
   /**
     * Loads account information from the accounts file into memory.
     * <p>
     * Each line in the file should be formatted as "username;hashedPassword;score".
     * </p>
     */
    private void loadAccountsFromFile() {
        File file = new File(ACCOUNTS_FILE);
        if (!file.exists()) {
            return;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            // Each line format: username;hashedPassword;score
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(";");
                if (parts.length >= 2) {
                    String username = parts[0].trim();
                    String hashedPassword = parts[1].trim();
                    int score = 0;
                    if (parts.length == 3) {
                        try {
                            score = Integer.parseInt(parts[2].trim());
                        } catch (NumberFormatException e) {
                            // if there's an error, assume a default score of 0
                            score = 0;
                        }
                    }
                    accounts.put(username, hashedPassword);
                    scores.put(username, score);
                }
            }
            System.out.println("Loaded " + accounts.size() + " account(s) from file.");
        } catch (IOException e) {
            System.err.println("Error reading " + ACCOUNTS_FILE + ": " + e.getMessage());
        }
    }
    
    // Helper method: save the in-memory accounts and scores to file.
    private synchronized void saveAccountsToFile() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(ACCOUNTS_FILE))) {
            // Write each account on a separate line.
            for (String username : accounts.keySet()) {
                String hashedPassword = accounts.get(username);
                int score = scores.getOrDefault(username, 0);
                bw.write(username + ";" + hashedPassword + ";" + score);
                bw.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error writing to " + ACCOUNTS_FILE + ": " + e.getMessage());
        }
    }
    
    /**
     * Loads multiplayer scores from the multiplayer scores file into memory.
     * <p>
     * Each line in the file should be formatted as "username;multiplayerScore".
     * </p>
     */
    private void loadMultiplayerScoresFromFile() {
        File file = new File(MULTIPLAYER_SCORES_FILE);
        if (!file.exists()) {
            return;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            // Each line format: username;multiplayerScore
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(";");
                if (parts.length >= 2) {
                    String username = parts[0].trim();
                    int mscore = Integer.parseInt(parts[1].trim());
                    multiplayerScores.put(username, mscore);
                }
            }
            System.out.println("Loaded " + multiplayerScores.size() + " multiplayer score(s) from file.");
        } catch (IOException e) {
            System.err.println("Error reading " + MULTIPLAYER_SCORES_FILE + ": " + e.getMessage());
        }
    }
    
    /**
     * Saves the current in-memory multiplayer scores to the multiplayer scores file.
     */
    private synchronized void saveMultiplayerScoresToFile() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(MULTIPLAYER_SCORES_FILE))) {
            for (Map.Entry<String, Integer> entry : multiplayerScores.entrySet()) {
                bw.write(entry.getKey() + ";" + entry.getValue());
                bw.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error writing to " + MULTIPLAYER_SCORES_FILE + ": " + e.getMessage());
        }
    }
    
    /**
     * Integrates multiplayer scores from a game session into the overall scores.
     * <p>
     * For each entry in the provided gameScores map, the multiplayer score is added to the stored value.
     * Additionally, the individual persistent score is also updated.
     * </p>
     *
     * @param gameScores a map of usernames to the score achieved in a game session.
     * @throws RemoteException if a remote error occurs.
     */
    @Override
    public synchronized void integrateMultiplayerScores(Map<String, Integer> gameScores) throws RemoteException {
        for (Map.Entry<String, Integer> entry : gameScores.entrySet()) {
            String username = entry.getKey();
            int gameScore = entry.getValue();

            // Update the multiplayer-specific scores.
            int currentMultiplayerScore = multiplayerScores.getOrDefault(username, 0);
            multiplayerScores.put(username, currentMultiplayerScore + gameScore);

            // Optionally, also update the individual (persistent) score.
            int currentPersistentScore = scores.getOrDefault(username, 0);
            scores.put(username, currentPersistentScore + gameScore);
        }
        // Persist both individual and multiplayer scores.
        saveAccountsToFile();
        saveMultiplayerScoresToFile();
        System.out.println("Integrated multiplayer scores from game session.");
    }
    
    /**
     * Main method to start the UserAccountServer.
     * <p>
     * This method creates or retrieves the RMI registry on port 1099 and binds this instance
     * under the name "UserAccountServer".
     * </p>
     *
     * @param args command-line arguments (not used).
     */
    public static void main(String[] args) {
        try {
            UserAccountImpl accountServer = new UserAccountImpl();
            // Create or get the RMI registry on port 1099.
            try {
                java.rmi.registry.LocateRegistry.getRegistry(1099).list();
            } catch (Exception e) {
                java.rmi.registry.LocateRegistry.createRegistry(1099);
            }
            // Bind to the RMI registry under the name "UserAccountServer"
            java.rmi.Naming.rebind("UserAccountServer", accountServer);
            System.out.println("UserAccountServer is running...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Updates multiplayer scores from a GameRoom by integrating the provided game scores.
     *
     * @param gameScores a map of usernames to scores from a multiplayer game session.
     * @throws RemoteException if a remote error occurs.
     */
 
	@Override
	public synchronized void updateMultiplayerScoresFromGameRoom(Map<String, Integer> gameScores) throws RemoteException {
		 
	        integrateMultiplayerScores(gameScores);
	        System.out.println("Multiplayer scores updated from GameRoom.");
		
	}


}
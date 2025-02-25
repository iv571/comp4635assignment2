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

public class UserAccountImpl extends UnicastRemoteObject implements UserAccountServer {

    private static final String ACCOUNTS_FILE = "accounts.txt";
    
    // In-memory maps for accounts and scores.
    // Accounts map now stores username -> hashedPassword
    private Map<String, String> accounts = new HashMap<>();
    private Map<String, Integer> scores = new ConcurrentHashMap<>();

    // Load accounts from file upon instantiation.
    protected UserAccountImpl() throws RemoteException {
        super();
        loadAccountsFromFile();
    }

    /**
     * Hashes a password using SHA-256.
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
    
    @Override
    public synchronized void updateScore(String username, int delta) throws RemoteException {
        int newScore = scores.getOrDefault(username, 0) + delta;
        scores.put(username, newScore);
        System.out.println("Updated score for " + username + " by " + delta 
                + ". New score: " + newScore);
        saveAccountsToFile();
    }
    
//    @Override
//    public synchronized void updateMultiplayerScore(String username, int delta) throws RemoteException {
//       
//    }
//    
    // Get the score for a given user.
    @Override
    public synchronized int getScore(String username) throws RemoteException {
        return scores.getOrDefault(username, 0);
    }
    
    // New method: get the scoreboard for all users.
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
    
    // Helper method: load accounts from file into the in-memory maps.
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
    
    // Main method for starting the account server.
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
}
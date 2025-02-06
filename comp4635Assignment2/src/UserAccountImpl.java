import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UserAccountImpl extends UnicastRemoteObject implements UserAccountServer {

    private static final String ACCOUNTS_FILE = "accounts.txt";
    
    // In-memory maps for accounts and scores.
    private Map<String, String> accounts = new HashMap<>();
    private Map<String, Integer> scores = new ConcurrentHashMap<>();

    // Load accounts from file upon instantiation.
    protected UserAccountImpl() throws RemoteException {
        super();
        loadAccountsFromFile();
    }

    @Override
    public synchronized boolean createAccount(String username, String password) throws RemoteException {
        if (accounts.containsKey(username)) {
            return false; // Account already exists.
        }
        accounts.put(username, password);
        // Initialize score for a new account to zero.
        scores.put(username, 0);
        System.out.println("Created account for " + username);
        saveAccountsToFile();
        return true;
    }

    @Override
    public synchronized boolean loginAccount(String username, String password) throws RemoteException {
        if (accounts.containsKey(username) && accounts.get(username).equals(password)) {
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
    
    // Get the score for a given user.
    @Override
    public synchronized int getScore(String username) throws RemoteException {
        return scores.getOrDefault(username, 0);
    }
    
    // New method: get the scoreboard for all users.
    @Override
    public synchronized Map<String, Integer> getScoreboard() throws RemoteException {
        // Return an unmodifiable copy of the scoreboard.
        return Collections.unmodifiableMap(new HashMap<>(scores));
    }
    
    // Helper method: load accounts from file into the in-memory maps.
    private void loadAccountsFromFile() {
        File file = new File(ACCOUNTS_FILE);
        if (!file.exists()) {
            return;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            // Each line format: username;password;score
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(";");
                if (parts.length >= 2) {
                    String username = parts[0].trim();
                    String password = parts[1].trim();
                    int score = 0;
                    if (parts.length == 3) {
                        try {
                            score = Integer.parseInt(parts[2].trim());
                        } catch (NumberFormatException e) {
                            // if there's an error, assume a default score of 0
                            score = 0;
                        }
                    }
                    accounts.put(username, password);
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
                String password = accounts.get(username);
                int score = scores.getOrDefault(username, 0);
                bw.write(username + ";" + password + ";" + score);
                bw.newLine();
            }
            // Flush is handled by try-with-resources closing the writer.
        } catch (IOException e) {
            System.err.println("Error writing to " + ACCOUNTS_FILE + ": " + e.getMessage());
        }
    }
    
    // Main method for starting the account server.
    public static void main(String[] args) {
        try {
            UserAccountImpl accountServer = new UserAccountImpl();
            // Create or get the RMI registry on port 1099.
            java.rmi.registry.LocateRegistry.createRegistry(1099);
            // Bind to the RMI registry under the name "UserAccountServer"
            java.rmi.Naming.rebind("UserAccountServer", accountServer);
            System.out.println("UserAccountServer is running...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.registry.LocateRegistry;
import java.rmi.Naming;
import java.io.*;
import java.util.*;

public class WordRepositoryImpl extends UnicastRemoteObject implements WordRepositoryServer {

    // File where words are stored.
    private static final String WORDS_FILE = "words.txt";

    // Constructor must throw RemoteException.
    protected WordRepositoryImpl() throws RemoteException {
        super();
    }

    /**
     * Creates a word in the repository.
     * If the word already exists (ignoring case), returns false.
     * Otherwise, appends the word to the file and returns true.
     */
    @Override
    public synchronized boolean createWord(String word) throws RemoteException {
        // Check if the word already exists.
        if (checkWord(word)) {
            return false;
        }
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(WORDS_FILE, true))) {
            bw.write(word);
            bw.newLine();
        } catch (IOException e) {
            throw new RemoteException("Error creating word: " + word, e);
        }
        return true;
    }

    /**
     * Removes a word from the repository.
     * Reads all words, filters out the given word (ignoring case), and rewrites the file.
     * Returns true if the word was found and removed; false otherwise.
     */
    @Override
    public synchronized boolean removeWord(String word) throws RemoteException {
        List<String> words = new ArrayList<>();
        boolean found = false;
        try (BufferedReader br = new BufferedReader(new FileReader(WORDS_FILE))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().equalsIgnoreCase(word)) {
                    found = true;
                } else {
                    words.add(line);
                }
            }
        } catch (IOException e) {
            throw new RemoteException("Error reading words file.", e);
        }
        if (!found) {
            return false;
        }
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(WORDS_FILE, false))) {
            for (String w : words) {
                bw.write(w);
                bw.newLine();
            }
        } catch (IOException e) {
            throw new RemoteException("Error writing words file.", e);
        }
        return true;
    }

    /**
     * Checks if the given word exists in the repository (ignoring case).
     */
    @Override
    public synchronized boolean checkWord(String word) throws RemoteException {
        try (BufferedReader br = new BufferedReader(new FileReader(WORDS_FILE))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().equalsIgnoreCase(word)) {
                    return true;
                }
            }
        } catch (IOException e) {
            throw new RemoteException("Error reading words file.", e);
        }
        return false;
    }

    /**
     * Returns a random word from the repository that has at least the specified length.
     * If no such word exists, returns an empty string.
     */
    @Override
    public synchronized String getRandomWord(int length) throws RemoteException {
        List<String> words = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(WORDS_FILE))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.length() >= length) {
                    words.add(line);
                }
            }
        } catch (IOException e) {
            throw new RemoteException("Error reading words file.", e);
        }
        if (words.isEmpty()) {
            return "";
        }
        Random random = new Random();
        return words.get(random.nextInt(words.size()));
    }

    /**
     * Main method to start the WordRepositoryServer.
     */
    public static void main(String[] args) {
        try {
            // Create or get the registry on port 1099.
        	java.rmi.registry.LocateRegistry.createRegistry(1100);
            // Create an instance of the repository implementation.
            WordRepositoryImpl wordServer = new WordRepositoryImpl();
            // Bind the repository instance in the registry with the name "WordRepositoryServer".
            java.rmi.Naming.rebind("WordRepositoryServer", wordServer);
            System.out.println("WordRepositoryServer is running and bound to 'WordRepositoryServer'.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
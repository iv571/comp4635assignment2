import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.io.*;
import java.util.*;

public class WordRepositoryImpl extends UnicastRemoteObject implements WordRepositoryServer {

    // File where words are stored.
    private static final String WORDS_FILE = "words.txt";
    private static List<String> words;

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
     * Reads all words, filters out the given word (ignoring case), and rewrites the
     * file.
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
     * Returns a random word from the repository that has at least the specified
     * length.
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
            try {
                java.rmi.registry.LocateRegistry.getRegistry(1099).list();
            } catch (Exception e) {
                // If the registry doesn't exist, create it.
                java.rmi.registry.LocateRegistry.createRegistry(1099);
            }
            // Create an instance of the repository implementation.
            WordRepositoryImpl wordServer = new WordRepositoryImpl();
            // Bind the repository instance in the registry with the name
            // "WordRepositoryServer".
            java.rmi.Naming.rebind("WordRepositoryServer", wordServer);
            System.out.println("WordRepositoryServer is running and bound to 'WordRepositoryServer'.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 
     * Method: generate_map_list
     *
     * @param word_len
     * 
     * @ author Stanley
     *
     * Details:
     * 			read words as stores as string list 
     * 			randomly pick a vertical word 
     * 			then find horizontal word match with the vertical word
     * 			if the puzzle is null than re call this function again.
     * 			return hash map that record which letter of the vertical
     * 			must match with each horizontal words 
     */
    public HashMap<String, Integer> generate_map_list(int word_len) throws RemoteException {

        read_words_info();

        String verticle_stem = find_vertical_stem(word_len - 1);

        HashMap<String, Integer> puzzle = find_horizontal_stem(verticle_stem, word_len - 1);

        while (puzzle == null) // if cant find the matching horizontal words

            puzzle = generate_map_list(word_len);

        return puzzle;
    }

    /**
     * Method: find_matching_addtional_vertical_stem
     * find all the match word lenght and store them into a list string
     * then randomly choose one of the word from the list
     * 
     * @param char constraint_letter[]
     * @return the matching verical words
     * @ author Stanley
     * 
     * Details: base on the constraint to find the matching vertical word
     * 			can randomly pick on of those
     */
    public String find_matching_addtional_vertical_stem(char constraint_letter[]) {

        int index;

        List<String> candidate = new ArrayList<>();

        boolean is_first_index_letter_only = is_first_index_letter_only(constraint_letter);

        if (is_first_index_letter_only)

            return find_constrained_word_match_with_first_letter(constraint_letter[0]);

        for (String word : words) {

            boolean matches = true;

            for (index = 0; index < word.length() && index < constraint_letter.length; index++) {

                if (constraint_letter[index] != '*') {

                    char word_ch = word.charAt(index);

                    if (word_ch != constraint_letter[index]) {

                        matches = false;

                        break;
                    }

                }

            }

            if (!rest_is_matched_with_any_letter(index, constraint_letter))

                matches = false;

            if (matches)

                candidate.add(word);

        }

        if (candidate.isEmpty())

            return null;

        return candidate.get(new Random().nextInt(candidate.size()));

    }
    public void ping() throws RemoteException {return;}
    
    private void read_words_info() {

        BufferedReader br;

        words = new ArrayList<>();

        try {

            br = new BufferedReader(new FileReader(WORDS_FILE));

            for (String line = br.readLine(); line != null; line = br.readLine()) {
                words.add(line.toLowerCase());
            }

        } catch (IOException e) {

            System.err.println("\nCouldn't read from file...");
        }

        return;

    }

    /**
     * Method: find_vertical_stem
     * find all the match word lenght and store them into a list string
     * then randomly choose one of the word from the list
     * 
     * @param word_len
     * @return random word from filtered_words
     * @ author Stanley
     */
    private String find_vertical_stem(int word_len) {

        List<String> filtered_words = new ArrayList<>();

        for (String word : words)

            if (word.length() >= word_len)

                filtered_words.add(word);

        if (filtered_words.isEmpty()) // no matching words

            return null;

        int random_index = new Random().nextInt(filtered_words.size());

        return filtered_words.get(random_index);

    }

    /**
     * Method: find_horizontal_stem
     * 
     * check every char at vertical word and find a horizontal word that match
     * with the char
     * 
     * @param verticle_stem selected vertical word
     * @param word_len      number of horizontal word need to found
     * @return a string list of horizontal words
     * @ author Stanley
     */
    private HashMap<String, Integer> find_horizontal_stem(String vertical_stem, int word_len) {

        HashMap<String, Integer> puzzle = new HashMap<>();

        puzzle.put(vertical_stem, -1);

        int[] random_postion = generate_random_position(vertical_stem.length());

        for (int index = 0, word_char_position = 0; index < word_len; index++) {

            word_char_position = random_postion[index];

            char target_letter = vertical_stem.charAt(word_char_position);

            String horizontal_word = find_constrained_word(target_letter);

            if (horizontal_word == null)

                return null;

            else

                puzzle.put(horizontal_word, word_char_position);

        }

        return puzzle;
    }

    /**
     * 
     * Method: generate_random_position
     *
     * @param word_len
     * @return
     *
     * @ author Stanley
     *
     * Details:
     * 			return a int [] that have random position.
     * 			eg if int [0] = 2 ; that find a horizontal word	
     * 			that match with the 2 letter of the vertical word. 
     */
    private int[] generate_random_position(int word_len) {

        int[] random_postion = new int[word_len];

        List<Integer> list = new ArrayList<>();

        for (int i = 1; i <= word_len; i++)

            list.add(i);

        Collections.shuffle(list);

        for (int i = 0; i < word_len; i++)

            random_postion[i] = list.get(i) - 1;

        return random_postion;

    }

    /**
     * Method: find_constrained_word
     * find a horizontal word that contains the target letter from vertical word
     * and randomly select one of the word as horizontal word
     * 
     * @param target_letter
     * @param word_len
     * @return a single horizontal word that contain the target_letter
     * @ author Stanley
     */
    private String find_constrained_word(char target_letter) {

        List<String> candidate = new ArrayList<>();

        for (String word : words)

            if (word.indexOf(target_letter) != -1) {

                candidate.add(word);

            }

        if (candidate.isEmpty())

            return null;

        return candidate.get(new Random().nextInt(candidate.size()));

    }
    /**
     * 
     * Method: rest_is_matched_with_any_letter
     *
     * @param index
     * @param constraint_letter
     * @return
     *
     * @ author Stanley
     *
     * Details:
     * 			check if the element after the index only contain '*'
     * 			if true, that only find for a word that match the element before the index
     * 			the rest does care including the length as well
     */
    private boolean rest_is_matched_with_any_letter(int index, char[] constraint_letter) {

        if (constraint_letter == null || index >= constraint_letter.length)

            return false;

        for (; index < constraint_letter.length; index++)

            if (constraint_letter[index] != '*')

                return false;

        return true;

    }

    private String find_constrained_word_match_with_first_letter(char target_letter) {

        List<String> candidate = new ArrayList<>();

        for (String word : words)

            if (word.charAt(0) == target_letter) {

                candidate.add(word);

            }

        if (candidate.isEmpty())

            return null;

        return candidate.get(new Random().nextInt(candidate.size()));

    }

    private boolean is_first_index_letter_only(char constraint_letter[]) {

        int letter_count = 0;

        for (char ch : constraint_letter) {

            if (Character.isLetter(ch))

                letter_count++;
        }

        return letter_count == 1;

    }
}
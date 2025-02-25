import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
/**
 * The Word class responds to any operation related to words, such as adding, 
 * checking, removing, and generating the game's crossword map.
 */
public class Word {
	
	private static List<String> words; //list of words that would output for game map
	/**
	 * 
	 * start by reading words in given file name
	 * 	
	 * @param file_name path that read in words and store in the List<String> words
	 */
	public Word (String file_name){
		
		read_words_info(file_name);

	}

	/**
	 * Method: read_words_info
	 * read and stores given file name in to List <String> words
	 * 
	 * @param file_name the list of words usually are stored in word.txt file
	 * @ author Stanley
	 */
	private static void read_words_info(String file_name){

		BufferedReader br;
		
		words = new ArrayList<>();

		try {
			
			br = new BufferedReader(new FileReader(file_name));
			
			for (String line = br.readLine(); line != null; line = br.readLine()) {
                words.add(line.toLowerCase());
            }
		
		} catch (IOException e) {
			
			System.err.println("\nCouldn't read from file...");
		}
		
		return;

	}

	/**
	 * Method: add_word 
	 * 
	 * add a new word to the list.
	 * 
	 * if the word already exist, the new word would not add again.
	 * else add to the list
	 *  
	 * this method find the alphabet position and insert the 
	 * target word
	 * 
	 * the code uses of Lexicographical order to located the 
	 * correct alphabetical order
	 * 
	 * @param target_word the word that need to be added
	 * @return if the word already exist return false 
	 * 			else return true
	 * @ author Stanley
	 */
	public static boolean add_word (String target_word) {
		
		int index = 0;
		
		int compare_result = 0;
		
		boolean added = false;

		while (index < words.size()) {
			
			compare_result = words.get(index).compareTo(target_word);
			
			// lexicographically smaller or equal to the current word, 
			// the target word is inserted at the current index.
			if (compare_result >= 0) 
				
				break;
			
			index++;
			
		}
		
		if (compare_result != 0) { // check if the word exist in the list
			
		 words.add(index, target_word);
		
		 added = true;
		 
		}
		
		return added;
	}
	/**
	 * Method: remove_word
	 * 
	 * remove a word form the list
	 *  
	 * @param target_word the word that need to be removed from the list 
	 * 
	 * @return if the word exist then removed the word return true
	 * 		   else return false
	 * @ author Stanley
	 */
	public static boolean remove_word (String target_word) {
		
		boolean removed = false;
		
		int index = words.indexOf(target_word);
		
		if (index != -1) {
			
			words.remove(index);
		
			removed = true;
		}
		
		return removed;
		
	}
	/**
	 * Method: check_word
	 * 
	 * check if the given word contain in the list
	 * 
	 * @param target_word the word to check
	 * @return true if exist
	 * 			else false
	 * @ author Stanley
	 */
	public static boolean check_word (String target_word) {
		
		 if (words.contains(target_word)) 
			 
			 return true;
			
		return false;
	}
	/**
	 * Method: generate_map
	 * 
	 * first it find the vertical word by calling the method find_vertical_stem
	 * then base on this vertical word to generate word len - 1 horizontal word
	 * 
	 * if find_horizontal_stem can't find the matching horizontal word that correspond 
	 * to vertical word, this method genertae_map will be called again until it find all the words.
	 * we assume that the server read in words from words.txt every time, so it less likely
	 * to result in inf loop.
	 * 
	 * @param word_len number of word length need to find for words
	 * @return a list of string. index 0 is stored vertical word the rest contain horizontal words
	 * @ author Stanley
	 */
	public static HashMap<String, Integer> generate_map_list(int word_len) {

	
		String verticle_stem = find_vertical_stem (word_len - 1);
		
        HashMap<String, Integer> puzzle = find_horizontal_stem (verticle_stem, word_len - 1);
		
		while (puzzle == null) // if cant find the matching horizontal words
			
			puzzle = generate_map_list(word_len);
			
		
		return puzzle;
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
	private static String find_vertical_stem (int word_len) {
		
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
	 * @param word_len number of horizontal word need to found
	 * @return a string list of horizontal words
	 * @ author Stanley
	 */
	private static HashMap<String, Integer> find_horizontal_stem(String vertical_stem, int word_len) {
		
        HashMap<String, Integer> puzzle = new HashMap<>();

        puzzle.put(vertical_stem, -1);
        
		int [] random_postion = generate_random_position(vertical_stem.length());
		
		for (int index = 0, word_char_position = 0;  index < word_len;  index++) {
			
			word_char_position = random_postion [index];
			
            char target_letter = vertical_stem.charAt(word_char_position);
            
            String horizontal_word = find_constrained_word(target_letter);
                        
            if (horizontal_word == null)
            	
            	return null;
            
            else
            	
                puzzle.put(horizontal_word, word_char_position);

        }
		
		return puzzle;
	}
	
	private static int[] generate_random_position(int word_len) {

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
	private static String find_constrained_word(char target_letter) {

		List<String> candidate = new ArrayList<>();
		
		for (String word : words) 
							
                if (word.indexOf(target_letter) != -1) {
                 
                	candidate.add(word);
			
                }
		
		if (candidate.isEmpty())
			
			return null;
		
	
		return candidate.get(new Random().nextInt(candidate.size()));

	}
	
	public static String find_matching_addtional_vertical_stem(char constraint_letter[]) {
	
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
	
	

	private static boolean rest_is_matched_with_any_letter(int index, char[] constraint_letter) {

		if (constraint_letter == null || index >= constraint_letter.length) 
        
			return false; 
        
        for (; index < constraint_letter.length; index++) 
           
        	if (constraint_letter[index] != '*') 
             
        		return false; 
            
        
        
        return true; 
    
	}
	

	private static String find_constrained_word_match_with_first_letter (char target_letter) {
	
		List<String> candidate = new ArrayList<>();
		
		for (String word : words) 
							
                if (word.charAt(0) == target_letter) {
                 
                	candidate.add(word);
			
                }
		
		if (candidate.isEmpty())
			
			return null;
		
		return candidate.get(new Random().nextInt(candidate.size()));
	
	
	}
	private static boolean is_first_index_letter_only(char constraint_letter[]) {
		
		int letter_count = 0;

        for (char ch : constraint_letter) {
            
        	if (Character.isLetter(ch)) 
            
        		letter_count++;
	}
        
        return letter_count == 1;
		
}		

}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	

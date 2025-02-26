import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


public class Mutiplayer_Puzzle {
	
	// use for initiate 2d array
	private final int INIT_SIZE = 50;
	// not sure how big would the map be so initiate as 50 x 50 at first
	// then resize the puzzle; this puzzle contains all the words
	private char[][] puzzle_solution = new char [INIT_SIZE][INIT_SIZE];
	// puzzle for player. when letter or words are guessed right 
	// words or letters would reveal on the puzzle
	private char[][] player_view_puzzle;
	// some int veritable to help mapping the words
	private int word_col_range = 0, max_col_size = 0, max_row_size = 0, min_col_size = 999;
	// same as above
	private int target_col, target_row;
	// contain horizontal and vertical words in the puzzle
	List<String> horizontal_stem = new ArrayList<>(), vertical_stem = new ArrayList<>();
	// horizontal or vertical words as a key output a int[] with 2 elements
	// int[0] = row ; int[1] = col;
	// (row, col) = the first letter of each word to render on the puzzle
	HashMap<String, int[]> horizontal_stem_position, vertical_stem_position;
	// a RMI of WordRepositoryServer class to remotely use its method
	WordRepositoryServer wordServer;

	/**
	 * 
	 * @param player_num: total players in the mutiplayer games
	 * @param level:      total words appear in the puzzle (but not including additional word related to player num)
	 * @param wordServer  a RMI that connect to word Server
	 * 
	 * @author Stanley
	 * 
	 * Details: the constructor for Mutiplayer_Puzzle. since there are too many constraints looking for a 
	 * multiple words puzzle, if the construct_puzzle is run longer then 1 sec, it is likely that the current
	 * horizontal and vertical words set cant form a puzzle. in this case the constructor would stop the previous
	 * call on construct_puzzle and make and other new function call on construct_puzzle again. 
	 * Because words are randomly selected, it ensures the puzzle for mutable player will be generated.
	 */
	public Mutiplayer_Puzzle (int player_num, int level, WordRepositoryServer wordServer){
	
	this.wordServer = wordServer; 

	// test on word Server
	try {
		
		wordServer.ping();
		
	} catch (RemoteException e) {
		
	    System.err.println("Failed to connect to the Word Repository Server.");
	}
	
	// construct puzzle if run more than 1 sec rerun the function again.
	 while (true) {
		 
		ExecutorService executor = Executors.newSingleThreadExecutor();
		// make another thread to run the function
		Future<?> future = executor.submit(() -> {
			 
		try {
		
			construct_puzzle(player_num, level);
			 
		} catch (Exception e) {
			
			throw new RuntimeException(e);
			
		} 
		
		});
		 
		try {
			// time for 1 sec 
			future.get(1, TimeUnit.SECONDS);
			// puzzle generated in one sec break the loop
			break; 
			 
		} catch (TimeoutException e) {
			// cancel the task
			// rerun the function again
			future.cancel(true);
			 
		} catch (Exception e) {
			 
			e.printStackTrace();
			 
			break;
			 
		} finally {
			 
			executor.shutdownNow();
		}
		 
	}
}

	
/**
 * 	
 * Method: is_guessed_word_correct
 * 
 * @param guessed_word: A player String input to check is the guessed word correct.
 * @return: boolean type: true = correct; false = incorrect
 * @author Stanley
 * 
 * Details: check if the guessed word is a letter or a word by checking
 * 			its length. depend on the lens to call the corresponding function
 */
public boolean is_guessed_word_correct(String guessed_word) {
	
	if (guessed_word.length() == 1)
		
		return check_if_letter_exist(guessed_word);
	
	else if (vertical_stem.contains(guessed_word)) 
			
		return reveal_vertical_word(guessed_word);
	
	else if (horizontal_stem.contains(guessed_word)) 
		
		return reveal_horizontal_stem_word(guessed_word);
	
	else 
		return false;	
}

/**
 * 
 * Method: render_player_view_puzzle
 * 
 * @return: return a string that contains the most updated player view puzzle
 * 
 * @author Stanley
 *
 */
public String render_player_view_puzzle () {
	
	StringBuilder puzzle = new StringBuilder();
	
	puzzle.append("\n");
	
	for (char[] row : player_view_puzzle) {
		
		for (char ch : row) {
		
			puzzle.append(ch + " ");
		}
		
		puzzle.append("\n");
	
	}

	return puzzle.toString();
	
}

/**
 * 
 * Method: render_puzzle_solution
 * 
 * @return: return a string that contains puzzle solution
 * 
 * @author Stanley
 *
 * Details:
 */
public String render_puzzle_solution () {
	
	StringBuilder puzzle = new StringBuilder();
	
	puzzle.append("\n");
	
	for (char[] row : puzzle_solution) {
		
		for (char ch : row) {
		
			puzzle.append(ch + " ");
		}
		
		puzzle.append("\n"); 
	}
	
	return puzzle.toString();
	
	
}

/**
 * 
 * Method: is_All_words_are_guessed
 *
 * @return true if all letters or words have been revealed.
 * 		   else false;
 *
 * @ author Stanley
 *
 * Details: check if there is any '_' on the puzzle 
 * 			if there is then false
 * 			else true
 */
public boolean is_All_words_are_guessed() {
	
	boolean guessed_All = true;
	
	for (int row = 0; row < max_row_size; row++)
		
		for (int col = 0; col < max_col_size; col++) {
			
			char current_letter = player_view_puzzle [row][col];
			
			if (current_letter == '_') {
			
				guessed_All = false;
			
				break;
			}
		}

	return guessed_All;
	
}
/**
 * 
 * Method: construct_puzzle
 *
 * @param player_num		total player in the multi-player game 
 * 							this number also means number of additional vertical words
 * @param level				number of words contains with 1 vertical word and (level - 1) horizontal word
 * *
 * @ author Stanley
 *
 * Details:
 * 			function name has show the basic steps on constructing the puzzle
 * 			first request a words from word Server
 * 			then plot the plot the words in the corresponding grids
 * 			resize the puzzle because it was initiated with 50 x 50
 * 			resize to puzzle that fit the words
 * 			omits all the letter with '_' for the game use 
 * 			then print the puzzle with all '_'
 */
private void construct_puzzle(int player_num, int level) throws RemoteException {

    reset_All();
    
	HashMap<String, Integer> word_position = wordServer.generate_map_list(level);
 	
	polt_player_puzzle(word_position, player_num);
	
	resize_puzzle();

	construct_player_view_puzzle();
		
	print_player_view_puzzle();
	
}
/**
 * 
 * Method: reveal_vertical_word
 *
 * @param guessed_word
 * @return if there is a matching word reveal the word in the puzzle
 * 		   and return true
 * 		   else do nothing
 * 		   return false
 *
 * @ author Stanley
 *
 */
private boolean reveal_vertical_word(String guessed_word) {
	
	int[] point = vertical_stem_position.get(guessed_word);
	
	int start_row = point [0];
	
	int fix_col = point [1];
	
	for (int row = start_row, index = 0; index < guessed_word.length(); row++) {
			
		player_view_puzzle[row][fix_col] = guessed_word.charAt(index);
	
		index++;
	}
	
	return true;
}
/**
 * 
 * Method: reveal_horizontal_stem_word
 *
 * @param guessed_word
 * @return if there is a matching word reveal the word in the puzzle
 * 		   and return true
 * 		   else do nothing
 * 		   return false
 *s
 *
 * @ author Stanley
 *
 */
private boolean reveal_horizontal_stem_word(String guessed_word) {
	
	int[] point = horizontal_stem_position.get(guessed_word);
	
	int fix_row = point [0];
	
	int start_col = point [1];
	
	for (int col = start_col, index = 0; index < guessed_word.length(); col++) {
			
		player_view_puzzle[fix_row][col] = guessed_word.charAt(index);
	
		index++;
	}
	return true;
}
/**
 * 
 * Method: check_if_letter_exist
 *
 * @param guessed_word
 * @return if there is a matching letter, reveal the letter in the puzzle
 * 		   and return true
 * 		   else do nothing
 * 		   return false
 *
 * @ author Stanley
 *
 * Details:
 */
private boolean check_if_letter_exist(String guessed_word) {

    boolean letter_found = false;
	
	char guessed_letter = guessed_word.charAt(0);
    
	for (int row = 0; row < max_row_size; row++)
		
		for (int col = 0; col < max_col_size; col++) {
			
			char current_letter = puzzle_solution [row][col];
			
			if (guessed_letter == current_letter) {
			
				letter_found = true;
				
				player_view_puzzle[row][col] = guessed_letter;
			
			
			}
		}

	return letter_found;
}
/**
 * 
 * Method: reset_All
 *
 * @ author Stanley
 *
 * Details:
 * 			because some words set cant produce the puzzle because of
 * 			certain reason. therefore whenever starting the construct_puzzle
 * 			every thing has to be reset
 */
private void reset_All(){
	
	horizontal_stem = new ArrayList<>();
	 
    vertical_stem = new ArrayList<>();
     
    puzzle_solution = new char [INIT_SIZE][INIT_SIZE];
 	
    horizontal_stem_position = new HashMap<>();

 	vertical_stem_position = new HashMap<>();
     
    word_col_range = 0;
     
    max_col_size = 0;
     
    max_row_size = 0;
     
    min_col_size = 999;
	
}

/**
 * 
 * Method: construct_player_view_puzzle
 *
 *
 * @ author Stanley
 *
 * Details:
 *           omits all the letter with '_' for the game use 
 *
 */

private void construct_player_view_puzzle() {

	player_view_puzzle = new char [max_row_size][max_col_size];
	
	for (int row = 0; row < max_row_size; row++)
		
		for(int col = 0; col < max_col_size; col++) {
			
			char target_block = puzzle_solution[row][col];
			
			if (Character.isLetter(target_block))
			
				player_view_puzzle[row][col] = '_';
			
			else if (col == max_col_size - 1)
				
				player_view_puzzle[row][col] = '+';		
			
			else
				
				player_view_puzzle[row][col] = '.';		

		}
	
}

/**
 * 
 * Method: resize_puzzle
 *
 *
 * @ author Stanley
 *
 * Details:
 * 			copy entire puzzle with perfect fit size with max lenght of words
 */			
private void resize_puzzle() {
		
	char[][] resized_puzzle_solution = new char [max_row_size][max_col_size];

	
	for (int index = 0; index < max_row_size; index++) 
	    System.arraycopy(puzzle_solution[index], 0, resized_puzzle_solution[index], 0, max_col_size);
	
	for (int row = 0; row < max_row_size; row++)
		resized_puzzle_solution[row][max_col_size - 1] = '+';
	
	puzzle_solution = resized_puzzle_solution;
	
}
/**
 * 
 * Method: polt_player_puzzle
 *
 * @param word_position     a hash Map with string and int. index 0 is the vertical word. it has to be pull out.
 * 							the rest are horizontal word as a key with a position value as a int. the int indicates
 * 							which position of the vertical word must the horizontal word match with. 
 * @param player_num		total player num
 *
 * @ author Stanley
 *
 * Details:
 * 			few general steps of plotting the puzzle. 
 * 			pull out the vertical word and horizontal word to List<String> horizontal_stem = new ArrayList<>(), vertical_stem = new ArrayList<>();
			find the best row to plot the vertical word then plot the rest with matching letter.
			then plot addtional_verticle words 
			last fill the non char spot with '.'
 */
private void polt_player_puzzle (HashMap<String, Integer> word_position, int player_num) throws RemoteException {
	
	pull_vertical_word(word_position);
	
	pull_horizontal_words(word_position);
	
	int ver_col_position = plot_map (word_position);
	
	add_addtional_vertical_stem (ver_col_position, player_num - 1);
	
	fill_in_dot();
	
	
}

/**
 * 
 * Method: fill_in_dot
 *
 *
 * @ author Stanley
 *
 * Details:
 * 			fill '.' with non char spot in the puzzle
 */
private void fill_in_dot() {
	
	for (int row = 0; row < max_row_size; row++) {
		
		for (int col = 0; col <= max_col_size; col++) {
			
			if (puzzle_solution[row][col] == '.')
				break;
			
			if (!Character.isLetter(puzzle_solution[row][col]) && col != max_col_size)
				
				puzzle_solution[row][col] = '.';
			
			else if (col == max_col_size)
				
				puzzle_solution[row][col] = '+';
		
		
		}
			
	}	
}

/**
 * 
 * Method: pull_vertical_word
 *
 * @param words_position
 * @return
 *
 * @ author Stanley
 *
 * Details:
 * 			pull the vertical word and store that in string list
 * 			the value of the vertical word is -1 
 * 			the rest are any positive number
 */
private String pull_vertical_word(HashMap<String, Integer> words_position) {

	for (Map.Entry<String, Integer> entry : words_position.entrySet()) 
		
        if (entry.getValue() == -1) {
        
        	vertical_stem.add(entry.getKey());
        	
        	return entry.getKey();
        
        }
	return null;
}
/**
 * 
 * Method: pull_horizontal_words
 *
 * @param words_position
 *
 * @ author Stanley
 *
 * Details: 
 * 			store horizontal words to string list
 */
private void pull_horizontal_words(HashMap<String, Integer> words_position) {

    horizontal_stem = new ArrayList<>(words_position.keySet());

	if (horizontal_stem.contains(vertical_stem.get(0))) 
		
		horizontal_stem.remove(vertical_stem.get(0));
	
	
	return;
	
}

/**
 * 
 * Method: add_addtional_vertical_stem
 *
 * @param ver_col_position  vericle words render position in the grid
 * @param player_num		total player num
 * 
 *
 * @ author Stanley
 *
 * Details:
 * 			total player number equal to the additional vertical words
 * 			loop until certain additional vertical words have been found
 */
private void add_addtional_vertical_stem(int ver_col_position, int player_num) throws RemoteException {

	for (int index = 0; index <= player_num; index++) {
	
		String addtional_verticle_stem = find_addtional_verticle_stem (ver_col_position);
    
		polt_addtional_vertical_stem(addtional_verticle_stem);
    
		vertical_stem.add(addtional_verticle_stem);
		
	}

}

/**
 * 
 * Method: polt_addtional_verticle_stem
 *
 * @param vertical_word: the additional vertical word
 *
 * @ author Stanley
 *
 * Details:
 *			when the a additional word was found, it render row and col also were store at target_row and target_col
 *			therefore plot each letter until the length of the word reach
 *
 */

private void polt_addtional_vertical_stem(String vertical_word) {

	int row, index;
	
	boolean letter_found = false;
	
	for (index = 0, row = target_row; index < vertical_word.length() && row < INIT_SIZE; row++) {
		
		char target_letter = vertical_word.charAt(index);
		
		char puzzle_char = puzzle_solution[row][target_col];
		
		if (puzzle_char != '.' &&  target_letter == puzzle_solution[row][target_col] && !letter_found) {
			
			vertical_stem_position.put(vertical_word, new int[] {row, target_col});
			
			letter_found = true;

			puzzle_solution[row][target_col] = vertical_word.charAt(index);

			index++;
			
			continue;

		}
		
		if (letter_found) {
			
			puzzle_solution[row][target_col] = vertical_word.charAt(index);
			
			index++;
			
			continue;
		}

		
	}
	
	if (row > max_row_size)
		
		max_row_size = row;
	
	return;
	
}
/**
 * 
 * Method: find_addtional_verticle_stem
 *
 * @param ver_col_position:  vertical word position on the puzzle
 * @return  the addtional vertical word
 * 
 * @ author Stanley
 *
 * Details:
 * 			since the puzzle has been plot with some words.
 * 			when looking for additional vertical word there would be some constraint 
 * 			in certain col. therefore, look for the constraint and find the matching
 * 			additional vertical words then plot with the matching horizontal words
 */
private String find_addtional_verticle_stem(int ver_col_position) throws RemoteException {

    char constraint_letter[] = find_constraint_letter (ver_col_position);
    
    String addtional_verticle_stem = wordServer.find_matching_addtional_vertical_stem(constraint_letter);
	
    if (addtional_verticle_stem == null) 
    	
    	return find_addtional_verticle_stem (ver_col_position);
    
	return addtional_verticle_stem;
}


/**
 * 
 * Method: find_constraint_letter
 *
 * @param ver_col_position verical words position in col
 * @return char array that contain letter or '*'
 * 		   '*' can be any char 
 * 			eg. if array = ['*','*', 'a', '*', '*', 'l', '*', '*', '*']
 * 				"a (char) (char) l (char) (char) (char) (char)......."
 *				so in this case apple , amplify, analyze are matched
 *
 * @ author Stanley
 *
 * Details:
 * 			it randomly look for a point on the puzzle then scan and letter
 * 			from the random point to the end row of the puzzle. 
 * 			recored what letter in the col and produce the constraint char array 
 * 			
 * 			some random point is bad such as no any contact word, or upper, left or 
 * 			right spot contain letter. these condition would reset the point until 
 * 			it finds a constraint letters
 */
private char[] find_constraint_letter (int ver_col_position) {
		
	int differece = word_col_range - min_col_size;
	
	char constraint_letter[] = new char[max_row_size + 1];
		
	int index = 0, target_col = 0, target_row = 0;
	
	boolean letter_found = false, reset_target_col = true , target_char_is_letter = false;
	
    Set<String> pairs = new HashSet<>();
    
	while (reset_target_col) {
		
	    Arrays.fill(constraint_letter, '*'); 

		target_col = new Random().nextInt(differece) + min_col_size;
		
		target_row = new Random().nextInt(max_row_size);
		
		if (target_col == ver_col_position) 
				
			continue;
		
		if (pairs.contains(target_row + "," + target_col)) 
			
			continue;
		
		reset_target_col = false;
				
		letter_found = false;
		
		this.target_row = target_row;
		
		for (index = 0; target_row <= max_row_size; target_row++) {
			
			target_char_is_letter = Character.isLetter(puzzle_solution[target_row][target_col]);
			
			if (target_char_is_letter) {
				
				constraint_letter[index] = puzzle_solution[target_row][target_col];

				letter_found = true;
			
				index++;
			
			} else if (letter_found)
				
				index++;
			
			reset_target_col = check_if_need_to_reset(target_row, target_col, letter_found);
		
			if (reset_target_col)
				
				break;
		
		}
		
        pairs.add(target_row + "," + target_col);

		reset_target_col = check_constraint_letter_validity(constraint_letter, reset_target_col);
	}
	
		this.target_col = target_col; 
			
		return constraint_letter;
	
	
}

/**
 * 
 * Method: check_if_need_to_reset
 *
 * @param target_row
 * @param target_col
 * @param letter_found
 * @return
 *
 * @ author Stanley
 *
 * Details:
 * 			check if there is a letter beside the random spot. if yes return true
 * 			else false. in order to make the puzzle looks good, these condition 
 * 			must avoid. because of these conditions, some set of words wont have 
 * 			a solution. Therefore, re run construct_puzzle are needed sometime.
 */
private boolean check_if_need_to_reset(int target_row, int target_col, boolean letter_found) {

	boolean target_char_is_letter = Character.isLetter(puzzle_solution[target_row][target_col]);
	
	boolean left_target_char_is_letter = Character.isLetter(puzzle_solution[target_row][target_col - 1]);
	
	boolean right_target_char_is_letter = Character.isLetter(puzzle_solution[target_row][target_col + 1]);
	
	boolean upper_target_char_is_letter = false;
	
	if (target_row - 1 >= 0)
		
		upper_target_char_is_letter = Character.isLetter(puzzle_solution[target_row - 1][target_col]);

	if (upper_target_char_is_letter && !target_char_is_letter && !letter_found) 
		
		return true;
	
	if (upper_target_char_is_letter && target_char_is_letter && letter_found) 
		
		return true;
	
	if (left_target_char_is_letter && !target_char_is_letter && letter_found) 
	
		return true;
		
	if (right_target_char_is_letter && !target_char_is_letter && letter_found) 
	
		return true;
	
	return false;
}

/**
 * 
 * Method: check_constraint_letter_validity
 *
 * @param constraint_letter
 * @param reset_target_col
 * @return
 *
 * @ author Stanley
 *
 * Details:
 * 			some time it produce a random point that does not contact any words
 * 			this is the function to avoid those points.
 */			
private boolean check_constraint_letter_validity(char[] constraint_letter, boolean reset_target_col) {

	for (char ch : constraint_letter) {
		
		if (ch != '*')
			
			return reset_target_col;
	}
	
	return true;
}

private void print_solution_puzzle() {
	
	for (char[] row : puzzle_solution) {
		
        for (char ch : row) {
        	
            System.out.print(ch + " "); // Print character with space
        }
        
        System.out.println(); // Move to the next line after each row
    }
}	
private void print_player_view_puzzle() {
	
	for (char[] row : player_view_puzzle) {
		
        for (char ch : row) {
        	
            System.out.print(ch + " "); // Print character with space
        }
        
        System.out.println(); // Move to the next line after each row
    }
}	
/**
 * 
 * Method: plot_map
 *
 * @param words_position
 * @return vertical word col position
 *
 * @ author Stanley
 *
 * Details:
 * 			when every it plot a word, it records it starting row and col and store that in to 
 * 			HashMap<String, int[]> horizontal_stem_position, vertical_stem_position;
 *			so it can be used in any revealing words.
 *			then plot words base on the hash map value
 */
private int plot_map(HashMap<String, Integer> words_position) {

	int ver_col_position = find_vertical_stem_position(words_position);
		
	int max_col_len = horizontal_stem.stream().mapToInt(String::length).max().orElse(0) + ver_col_position;
	
	HashMap<String, Integer> print_position = find_each_horizontal_stem_position(ver_col_position, words_position);
	
    String vertical_word = vertical_stem.get(0);
	
    vertical_stem_position.put(vertical_word, new int[] {0 ,ver_col_position});

	place_string (words_position, print_position);
    
	for (int row = 0; row < vertical_word.length(); row++) 
				
		for (int col = 0; col <= max_col_len; col++) 

			if (col == ver_col_position)
			
				puzzle_solution[row][col] = vertical_word.charAt(row);
						
			else if (!Character.isLetter(puzzle_solution[row][col]) && col != max_col_len)
				
				puzzle_solution[row][col] = '.';
			
			else if (col == max_col_len)
				
				puzzle_solution[row][col] = '+';
		
	max_col_size = max_col_len + 1;
	
	return ver_col_position;
	
	
}
/**
 * 
 * Method: place_string
 *
 * @param word_position
 * @param print_position
 *
 * @ author Stanley
 *
 * Details:
 * 
 * 		plot all the horizontal words in target point.
 */
private void place_string(HashMap<String, Integer> word_position, HashMap<String, Integer> print_position) {

	int starting_col, row;
	
	for (String horizontal_word : horizontal_stem) {
		
		row = word_position.get(horizontal_word);
		
		starting_col = print_position.get(horizontal_word);
		
		horizontal_stem_position.put(horizontal_word, new int[] {row, starting_col});
		
		for (int index = 0; index < horizontal_word.length(); index++) {
			
	        puzzle_solution[row][starting_col] = horizontal_word.charAt(index);
	    	
	    	starting_col++;

		}
	
		if (starting_col > word_col_range)
			
			word_col_range = starting_col - 1;
		
	}
	

	return;
	
}
/**
 * 
 * Method: find_each_horizontal_stem_position
 *
 * @param ver_col_position
 * @param words_position
 * @return
 *
 * @ author Stanley
 *					
 * Details:
 *			before doing any polting, all words render position must be located.
 *			then stores the render starting point to hashMap
 */
private HashMap<String, Integer> find_each_horizontal_stem_position(int ver_col_position, HashMap<String, Integer> words_position) {

    HashMap<String, Integer> render_position = new HashMap<>();

    String verical_word = vertical_stem.get(0);
    
	for (String word : horizontal_stem) {
				
		char target_letter = verical_word.charAt(words_position.get(word));
		
		int target_letter_position = word.indexOf(target_letter);

		int render_index = ver_col_position - target_letter_position; 
		
		render_position.put(word, render_index);
		
		if (min_col_size > render_index)
			
			min_col_size = render_index;
	}

	return render_position;
	
}

/**
 * 
 * Method: find_vertical_stem_position
 *
 * @param words_position
 * @return
 *
 * @ author Stanley
 *
 * Details:
 * 			find the best location to render the vertical words that fits all
 * 			the horizontal words.
 */			
private int find_vertical_stem_position(HashMap<String, Integer> words_position) {
	
	int max_value = 0;
	
	String vertical_word = vertical_stem.get(0);

	for (String word : horizontal_stem) {
				
		char target_letter = vertical_word.charAt(words_position.get(word));
		
		int target_letter_position = word.indexOf(target_letter);
		
		if (target_letter_position > max_value)
					
			max_value = target_letter_position;
			
	}
	
	max_row_size = vertical_word.length();
	
	return max_value + 2;
	


}

}
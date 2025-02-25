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
	
	private final int INIT_SIZE = 50;
	
	private char[][] puzzle_solution = new char [INIT_SIZE][INIT_SIZE];
	
	private char[][] player_view_puzzle;
	
	private int word_col_range = 0, max_col_size = 0, max_row_size = 0, min_col_size = 999;
	
	private int target_col, target_row;
	
	List<String> horizontal_stem = new ArrayList<>(), vertical_stem = new ArrayList<>();
	
	HashMap<String, int[]> horizontal_stem_position, vertical_stem_position;
		
	WordRepositoryServer wordServer;

public Mutiplayer_Puzzle (int player_num, int level, WordRepositoryServer wordServer){
	
	this.wordServer = wordServer; 

	 while (true) {
		 
		ExecutorService executor = Executors.newSingleThreadExecutor();
		 
		Future<?> future = executor.submit(() -> {
			 
		try {
		
			construct_puzzle(player_num, level);
			 
		} catch (Exception e) {
			 
			throw new RuntimeException(e);
			}
		});
		 
		try {
			 
			future.get(1, TimeUnit.SECONDS);
			 
			break; 
			 
		} catch (TimeoutException e) {
			 
			future.cancel(true); // Cancel the task
			 
		} catch (Exception e) {
			 
			e.printStackTrace();
			 
			break;
			 
		} finally {
			 
			executor.shutdownNow();
		}
		 
	}
}

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
private void construct_puzzle(int player_num, int level) throws RemoteException {

    reset_All();
    
	HashMap<String, Integer> word_position = wordServer.generate_map_list(level);
 	
	polt_player_puzzle(word_position, player_num);
	
	resize_puzzle();

	construct_player_view_puzzle();
	
	print2DArray1();
	
	System.out.println();
	
	print2DArray2();
	
}
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


private void resize_puzzle() {
		
	char[][] resized_puzzle_solution = new char [max_row_size][max_col_size];

	
	for (int index = 0; index < max_row_size; index++) 
	    System.arraycopy(puzzle_solution[index], 0, resized_puzzle_solution[index], 0, max_col_size);
	
	for (int row = 0; row < max_row_size; row++)
		resized_puzzle_solution[row][max_col_size - 1] = '+';
	
	puzzle_solution = resized_puzzle_solution;
	
}

private void polt_player_puzzle (HashMap<String, Integer> word_position, int player_num) throws RemoteException {
	
	pull_vertical_word(word_position);
	
	pull_horizontal_words(word_position);
	
	int ver_col_position = plot_map (word_position);
	
	add_addtional_vertical_stem (ver_col_position, player_num - 1);
	
	fill_in_dot();
	
	
}

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

private String pull_vertical_word(HashMap<String, Integer> single_list) {

	for (Map.Entry<String, Integer> entry : single_list.entrySet()) 
		
        if (entry.getValue() == -1) {
        
        	vertical_stem.add(entry.getKey());
        	
        	return entry.getKey();
        
        }
	return null;
}

private void pull_horizontal_words(HashMap<String, Integer> single_list) {

    horizontal_stem = new ArrayList<>(single_list.keySet());

	if (horizontal_stem.contains(vertical_stem.get(0))) 
		
		horizontal_stem.remove(vertical_stem.get(0));
	
	
	return;
	
}

private void add_addtional_vertical_stem(int ver_col_position, int player_num) throws RemoteException {

	for (int index = 0; index <= player_num; index++) {
	
		String addtional_verticle_stem = find_addtional_verticle_stem (ver_col_position);
    
		polt_addtional_verticle_stem(addtional_verticle_stem);
    
		vertical_stem.add(addtional_verticle_stem);
		
	}

}


private void polt_addtional_verticle_stem(String vertical_word) {

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

private String find_addtional_verticle_stem(int ver_col_position) throws RemoteException {

    char constraint_letter[] = find_constraint_letter (ver_col_position);
    
    String addtional_verticle_stem = wordServer.find_matching_addtional_vertical_stem(constraint_letter);
	
    if (addtional_verticle_stem == null) 
    	
    	return find_addtional_verticle_stem (ver_col_position);
    
	return addtional_verticle_stem;
}



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

private boolean check_constraint_letter_validity(char[] constraint_letter, boolean reset_target_col) {

	for (char ch : constraint_letter) {
		
		if (ch != '*')
			
			return reset_target_col;
	}
	
	return true;
}

private void print2DArray1() {
	
	for (char[] row : puzzle_solution) {
		
        for (char ch : row) {
        	
            System.out.print(ch + " "); // Print character with space
        }
        
        System.out.println(); // Move to the next line after each row
    }
}	
private void print2DArray2() {
	
	for (char[] row : player_view_puzzle) {
		
        for (char ch : row) {
        	
            System.out.print(ch + " "); // Print character with space
        }
        
        System.out.println(); // Move to the next line after each row
    }
}	

private int plot_map(HashMap<String, Integer> single_list) {

	int ver_col_position = find_vertical_stem_position(single_list);
		
	int max_col_len = horizontal_stem.stream().mapToInt(String::length).max().orElse(0) + ver_col_position;
	
	HashMap<String, Integer> print_position = find_each_horizontal_stem_position(ver_col_position, single_list);
	
    String vertical_word = vertical_stem.get(0);
	
    vertical_stem_position.put(vertical_word, new int[] {0 ,ver_col_position});

	place_string (single_list, print_position);
    
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

private void place_string(HashMap<String, Integer> single_list, HashMap<String, Integer> print_position) {

	int starting_col, row;
	
	for (String horizontal_word : horizontal_stem) {
		
		row = single_list.get(horizontal_word);
		
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

private HashMap<String, Integer> find_each_horizontal_stem_position(int ver_col_position, HashMap<String, Integer> single_list) {

    HashMap<String, Integer> render_position = new HashMap<>();

    String verical_word = vertical_stem.get(0);
    
	for (String word : horizontal_stem) {
				
		char target_letter = verical_word.charAt(single_list.get(word));
		
		int target_letter_position = word.indexOf(target_letter);

		int render_index = ver_col_position - target_letter_position; 
		
		render_position.put(word, render_index);
		
		if (min_col_size > render_index)
			
			min_col_size = render_index;
	}

	return render_position;
	
}

private int find_vertical_stem_position(HashMap<String, Integer> single_list) {
	
	int max_value = 0;
	
	String vertical_word = vertical_stem.get(0);

	for (String word : horizontal_stem) {
				
		char target_letter = vertical_word.charAt(single_list.get(word));
		
		int target_letter_position = word.indexOf(target_letter);
		
		if (target_letter_position > max_value)
					
			max_value = target_letter_position;
			
	}
	
	max_row_size = vertical_word.length();
	
	return max_value + 2;
	


}

}
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.Scanner;

public class Main {


	public static void main(String[] args) throws MalformedURLException, RemoteException, NotBoundException {
		
		WordRepositoryServer wordServer = (WordRepositoryServer) Naming.lookup("rmi://localhost:1099/WordRepositoryServer");

		Scanner input = new Scanner (System.in);
		
		boolean GAME_OVER = false;
		
		new Word ("words.txt");

		Mutiplayer_Puzzle puzzle = new Mutiplayer_Puzzle(5,5, wordServer);
		
		while (!GAME_OVER) {
			
			System.out.print("\nGuess a word: ");
			
	        String word = input.nextLine();
			
	       if (puzzle.is_guessed_word_correct(word)) 
	    	   
				System.out.print("YOU GUESS IT RIGHT");

	       else 
	    	   
				System.out.print("YOU GUESS IT WRONG");

	       System.out.print(puzzle.render_player_view_puzzle ());
	       
	       GAME_OVER = puzzle.is_All_words_are_guessed();
		
		}
		input.close();
		
		System.out.print("GAME--ENDED");
		
	}

}

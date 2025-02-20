
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface CrissCrossPuzzleServer extends Remote {

	public String startGame(
			String player,
			int number_of_words,
			int failed_attempt_factor) throws RemoteException;

	public String guessLetter(String player, char letter) throws RemoteException;

	public String guessWord(String player, String word) throws RemoteException;

	public String endGame(String player) throws RemoteException;

	public String restartGame(String player) throws RemoteException;

	public String addWord(String word) throws RemoteException;

	public String removeWord(String word) throws RemoteException;

	public String checkWord(String word) throws RemoteException;

	String startMultiGame(String username, int numPlayers, int level) throws RemoteException, RejectedException;

	// String joinMultiGame(String player, String gameId) throws RemoteException, RejectedException;

	// List<String> getActiveGames() throws RemoteException;
}

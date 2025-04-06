
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

public interface CrissCrossPuzzleServer extends Remote {

	public String startGame(
			String player,
			int number_of_words,
			int failed_attempt_factor,
			int seq) throws RemoteException;

	public String guessLetter(String player, char letter, int seq) throws RemoteException;

	public String guessWord(String player, String word, int seq) throws RemoteException;

	public String endGame(String player, int seq) throws RemoteException;

	public String restartGame(String player, int seq) throws RemoteException;

	public boolean addWord(String username, String word, int seq) throws RemoteException;

	public boolean removeWord(String username, String word, int seq) throws RemoteException;

	public boolean checkWord(String username, String word, int seq) throws RemoteException;

	public String startMultiGame(String username, int numPlayers, int level) throws RemoteException, RejectedException;

	public String joinMultiGame(String player, int gameId, ClientCallback callback)
			throws RemoteException, RejectedException;

	public String startGameRoom(String hostName, int gameId) throws RemoteException;

	public String setActivePlayer(String player, int gameId) throws RemoteException;

	public String leaveRoom(String player, int gameId) throws RemoteException;

	public boolean isActiveRoom(int gameId) throws RemoteException;

	public boolean isGameRun(int gameId) throws RemoteException;

	public String runGame(String player, int roomId, WordRepositoryServer wordServer) throws RemoteException;

	public String showActiveGameRooms() throws RemoteException;
	
	// Heartbeat method for failure detection
    public void heartbeat(String client) throws RemoteException;

	

	public boolean isValidRoomID(int roomID) throws RemoteException;

	public void updateRevealedPuzzle(String updatedView) throws RemoteException;
	
	// In CrissCrossPuzzleServer interface
	public String getCurrentRevealedPuzzle() throws RemoteException;
}

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface CrissCrossPuzzleServer extends Remote {

    // Single-player methods with deduplication parameters (username and sequence number)
    public String startGame(String player, int number_of_words, int failed_attempt_factor, int seq) throws RemoteException;
    public String guessLetter(String player, char letter, int seq) throws RemoteException;
    public String guessWord(String player, String word, int seq) throws RemoteException;
    public String endGame(String player, int seq) throws RemoteException;
    public String restartGame(String player, int seq) throws RemoteException;

    // Word repository operations (if applicable, they now take username and sequence number)
    public boolean addWord(String username, String word, int seq) throws RemoteException;
    public boolean removeWord(String username, String word, int seq) throws RemoteException;
    public boolean checkWord(String username, String word, int seq) throws RemoteException;

    // Multiplayer methods with deduplication parameters
    public String startMultiGame(String username, int numPlayers, int level, int seq) throws RemoteException, RejectedException;
    public String joinMultiGame(String player, int gameId, ClientCallback callback, int seq) throws RemoteException, RejectedException;
    public String startGameRoom(String hostName, int gameId, int seq) throws RemoteException;
    public String setActivePlayer(String player, int gameId, int seq) throws RemoteException;
    public String leaveRoom(String player, int gameId, int seq) throws RemoteException;
    public boolean isActiveRoom(String username, int gameId, int seq) throws RemoteException;
    public boolean isGameRun(String username, int gameId, int seq) throws RemoteException;
    public String runGame(String player, int roomId, WordRepositoryServer wordServer, int seq) throws RemoteException;
    public String showActiveGameRooms(String username, int seq) throws RemoteException;
    public boolean isValidRoomID(String username, int roomID, int seq) throws RemoteException;
}
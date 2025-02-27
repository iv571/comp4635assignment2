import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Map;

public interface UserAccountServer extends Remote {
    // Returns true if account creation succeeded; false if the username already
    // exists.
    public boolean createAccount(String username, String password) throws RemoteException;

    // Returns true if login credentials are correct; false otherwise.
    public boolean loginAccount(String username, String password) throws RemoteException;

    public void updateScore(String username, int score, boolean multiplayerMode) throws RemoteException;

    int getScore(String username) throws RemoteException;

    public Map<String, Integer> getScoreboard(boolean multimode) throws RemoteException;

}

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface UserAccountServer extends Remote {
    // Returns true if account creation succeeded; false if the username already exists.
    public boolean createAccount(String username, String password) throws RemoteException;
    
    // Returns true if login credentials are correct; false otherwise.
    public boolean loginAccount(String username, String password) throws RemoteException;
}

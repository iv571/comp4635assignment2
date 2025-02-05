import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Map;

public class UserAccountImpl extends UnicastRemoteObject implements UserAccountServer {

    // Simple in-memory map from username to password.
    private Map<String, String> accounts = new HashMap<>();

    protected UserAccountImpl() throws RemoteException {
        super();
    }

    @Override
    public synchronized boolean createAccount(String username, String password) throws RemoteException {
        if (accounts.containsKey(username)) {
            return false; // Account already exists.
        }
        accounts.put(username, password);
        System.out.println("Created account for " + username);
        return true;
    }

    @Override
    public synchronized boolean loginAccount(String username, String password) throws RemoteException {
        if (accounts.containsKey(username) && accounts.get(username).equals(password)) {
            System.out.println("User " + username + " logged in successfully.");
            return true;
        }
        return false;
    }
    
    // Main method for starting the account server.
    public static void main(String[] args) {
        try {
            UserAccountImpl accountServer = new UserAccountImpl();
            // Bind to the RMI registry under the name "UserAccountServer"
            java.rmi.registry.LocateRegistry.createRegistry(1099);
            java.rmi.Naming.rebind("UserAccountServer", accountServer);
            System.out.println("UserAccountServer is running...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
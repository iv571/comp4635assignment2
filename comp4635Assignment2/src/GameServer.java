

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;

public class GameServer {
    private static final String USAGE = "java Server <bank_rmi_url>";
    private static final String BANK = "RBC";
    private static final String HOST = "localhost";

    public GameServer(String bankName) {
        try {
            // Register the newly created object at rmiregistry.
            try {
                LocateRegistry.getRegistry(1099).list();
            } catch (RemoteException e) {
                LocateRegistry.createRegistry(1099);
            }
            
            
         // --- Automatically start and bind the UserAccountServer ---
            try {
                // Create an instance of your UserAccountServer implementation.
                UserAccountImpl accountServer = new UserAccountImpl();
                // Bind the account server to the registry under the name "UserAccountServer"
                Naming.rebind("rmi://localhost:1099/UserAccountServer", accountServer);
                System.out.println("UserAccountServer is running and bound as 'UserAccountServer'");
            } catch (Exception e) {
                System.err.println("Failed to start UserAccountServer: " + e.getMessage());
                e.printStackTrace();
            }
            
            CrissCrossPuzzleServer bankobj = new CrissCrossImpl(bankName);
            
            //Create the string URL holding the object's name
    		String rmiObjectName = "rmi://" + HOST + "/" + bankName;
            Naming.rebind(bankName, bankobj);
            System.out.println(bankobj + " is ready.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        if (args.length > 1 || (args.length > 0 && args[0].equalsIgnoreCase("-h"))) {
            System.out.println(USAGE);
            System.exit(1);
        }

        String bankName;
        if (args.length > 0) {
            bankName = args[0];
        } else {
            bankName = BANK;
        }

        new GameServer(bankName);
    }
}

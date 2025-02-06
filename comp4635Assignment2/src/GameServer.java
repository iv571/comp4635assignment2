

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class GameServer {
    private static final String USAGE = "java Server <bank_rmi_url>";
    private static final String BANK = "RBC";
    private static final String HOST = "localhost";
    private static final int REGISTRY_PORT = 1099;

    public GameServer(String bankName) {
        try {
        	// Create or get the RMI registry
            Registry registry;
            try {
                registry = LocateRegistry.getRegistry(REGISTRY_PORT);
                registry.list(); // Forces a check if the registry exists
            } catch (RemoteException e) {
                registry = LocateRegistry.createRegistry(REGISTRY_PORT);
            }
            
            
         // --- Automatically start and bind the UserAccountServer ---
            try {
            	// Bind UserAccountServer
                UserAccountImpl accountServer = new UserAccountImpl();
                registry.rebind("UserAccountServer", accountServer);
                System.out.println("UserAccountServer bound.");
                System.out.println("UserAccountServer is running and bound as 'UserAccountServer'");
            } catch (Exception e) {
                System.err.println("Failed to start UserAccountServer: " + e.getMessage());
                e.printStackTrace();
            }
            
            // --- Automatically start and bind the WordRepositoryServer ---
            try {
            	WordRepositoryImpl wordServer = new WordRepositoryImpl();
                registry.rebind("WordRepositoryServer", wordServer);
                System.out.println("WordRepositoryServer bound.");
            } catch (Exception e) {
                System.err.println("Failed to start WordRepositoryServer: " + e.getMessage());
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

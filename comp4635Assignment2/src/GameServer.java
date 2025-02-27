

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * The GameServer class initializes and binds the CrissCrossPuzzleServer implementation to the RMI registry.
 * <p>
 * It sets up the RMI registry on the specified port, creates an instance of the game server,
 * and binds it to a specific RMI URL so that clients can look it up and invoke remote methods.
 * </p>
 */

public class GameServer {
    private static final String USAGE = "java GameServer <rmi_url>";
    private static final String HOST = "localhost";
    private static final int REGISTRY_PORT = 1099;

    /**
     * Constructs a new GameServer instance and binds the CrissCrossPuzzleServer implementation to the RMI registry.
     *
     * @param name a string used to initialize the game server (e.g., to differentiate instances).
     */
    public GameServer(String name) {
        try {
        	// Create or get the RMI registry
            Registry registry;
            try {
                registry = LocateRegistry.getRegistry(REGISTRY_PORT);
                registry.list(); // Forces a check if the registry exists
            } catch (RemoteException e) {
                registry = LocateRegistry.createRegistry(REGISTRY_PORT);
            }
            
            
//         // --- Automatically start and bind the UserAccountServer ---
//            try {
//            	// Bind UserAccountServer
//                UserAccountImpl accountServer = new UserAccountImpl();
//                registry.rebind("UserAccountServer", accountServer);
//                System.out.println("UserAccountServer bound.");
//                System.out.println("UserAccountServer is running and bound as 'UserAccountServer'");
//            } catch (Exception e) {
//                System.err.println("Failed to start UserAccountServer: " + e.getMessage());
//                e.printStackTrace();
//            }
//            
//            // --- Automatically start and bind the WordRepositoryServer ---
//            try {
//            	WordRepositoryImpl wordServer = new WordRepositoryImpl();
//                registry.rebind("WordRepositoryServer", wordServer);
//                System.out.println("WordRepositoryServer bound.");
//            } catch (Exception e) {
//                System.err.println("Failed to start WordRepositoryServer: " + e.getMessage());
//                e.printStackTrace();
//            }
            
            CrissCrossPuzzleServer gameServer = new CrissCrossImpl(name);
            
          //	Create the string URL holding the object's name
            String rmiObjectName = "rmi://localhost:1099/GameServer";
            Naming.rebind(rmiObjectName, gameServer);
            System.out.println(gameServer + " is ready.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * The entry point of the GameServer.
     * 
     * The main method parses command-line arguments to optionally set the server name,
     * and then instantiates the GameServer.
     * 
     *
     * @param args command-line arguments. If an argument is provided, it is used as the server name.
     */
    public static void main(String[] args) {
        if (args.length > 1 || (args.length > 0 && args[0].equalsIgnoreCase("-h"))) {
            System.out.println(USAGE);
            System.exit(1);
        }

        String name;
        if (args.length > 0) {
            name = args[0];
        } else {
            name = "A";
        }

        new GameServer(name);
    }
}

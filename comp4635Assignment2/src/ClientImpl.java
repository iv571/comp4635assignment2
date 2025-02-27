import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Implementation of the ClientCallback interface.
 * <p>
 * This class extends provides mechanisms for receiving messages from the server, requesting player input,
 * and notifying the client when it is their turn during a multi-player game.
 * </p>
 */

public class ClientImpl extends UnicastRemoteObject implements ClientCallback {
	
	 // Declare a shared static BufferedReader for System.in
    private static final BufferedReader consoleIn = new BufferedReader(new InputStreamReader(System.in));
 // Shared blocking queue for input lines.
    private static final BlockingQueue<String> inputQueue = new LinkedBlockingQueue<>();

    private final Client client;  // Reference to the outer Client instance\
    
    /**
     * Constructs a new ClientImpl instance.
     *
     * @param client the Client instance that owns this callback implementation.
     * @throws RemoteException if an RMI error occurs during export.
     */
    public ClientImpl(Client client) throws RemoteException {
    	super();
    	this.client = client;
    }

    /**
     * Receives a message from the server and displays it to the user.
     * If the client is in multi-player mode with an active game, it also displays the prompt.
     *
     * @param message the message sent by the server.
     * @throws RemoteException if a remote communication error occurs.
     */
    @Override
    public void receiveMessage(String message) throws RemoteException {
        System.out.println("\n[SERVER MESSAGE]: " + message);
        if (client.multiplayerMode && client.activeGameID != -1) {
            System.out.print(client.clientname + "@server>");
        }
    }

    /**
     * Requests player input by polling the shared input queue.
     * This method waits for input for a limited time before returning.
     *
     * @param playerName the name of the player requesting input.
     * @return the input string from the player, or an empty string if the timeout expires.
     * @throws RemoteException if a remote communication error occurs.
     */
    @Override
    public String requestPlayerInput(String playerName) throws RemoteException {
        try {
            // Poll input from the queue with a timeout (here 1 second, adjust if needed)
            String input = inputQueue.poll(1, TimeUnit.SECONDS);
            return input;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }
    
    /**
     * Adds input from the player into the shared input queue.
     * 
     * This method can be called by the main client loop to supply input asynchronously
     * to the ClientImpl for processing.
     * 
     *
     * @param input the input string to be added to the queue.
     */
    public static void addInput(String input) {
        inputQueue.offer(input);
    }

    /**
     * Notifies the client that it is their turn in the multi-player game.
     
     * This method sets the client's turn flag to true.
     
     *
     * @throws RemoteException if a remote communication error occurs.
     */
	@Override
	public void notifyTurn() throws RemoteException {
		// TODO Auto-generated method stub
		client.isMyTurn = true;
	}
}
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ClientImpl extends UnicastRemoteObject implements ClientCallback {
	
	 // Declare a shared static BufferedReader for System.in
    private static final BufferedReader consoleIn = new BufferedReader(new InputStreamReader(System.in));
 // Shared blocking queue for input lines.
    private static final BlockingQueue<String> inputQueue = new LinkedBlockingQueue<>();

    private final Client client;  // Reference to the outer Client instance\
    
    public ClientImpl(Client client) throws RemoteException {
    	super();
    	this.client = client;
    }

    @Override
    public void receiveMessage(String message) throws RemoteException {
        System.out.println("\n[SERVER MESSAGE]: " + message);
        if (client.multiplayerMode && client.activeGameID != -1) {
            System.out.print(client.clientname + "@server>");
        }
    }

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
    // This method will be called by the main loop to feed input into the queue.
    public static void addInput(String input) {
        inputQueue.offer(input);
    }

	@Override
	public void notifyTurn() throws RemoteException {
		// TODO Auto-generated method stub
		client.isMyTurn = true;
	}
}
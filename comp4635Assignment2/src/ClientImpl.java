import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

public class ClientImpl extends UnicastRemoteObject implements ClientCallback {
    private Client client;
    private BufferedReader consoleIn;

    public ClientImpl(Client client) throws RemoteException {
        this.client = client;
        this.consoleIn = client.getReader();
    }

    @Override
    public void receiveMessage(String message) throws RemoteException {
        System.out.println("[SERVER MESSAGE]: " + message);
    }

    @Override
    public String requestPlayerInput(String playerName) throws RemoteException {
        try {
            if (consoleIn == null) {
                throw new RemoteException("BufferedReader is not initialized. Call setBufferedReader() first.");
            }
            System.out.print(playerName + ", please enter a word: ");

            String input = consoleIn.readLine();

            if (input == null) {
                System.out.println("No input received. Returning default response.");
                return "NO_INPUT"; // Return a recognizable response to prevent infinite looping
            }

            return input.trim(); // Trim spaces to avoid accidental empty inputs
        } catch (IOException e) {
            e.printStackTrace();
            return "ERROR"; // Return an error message instead of an empty string
        }
    }

    @Override
    public void updateInGameStatus(boolean inGameStatus) throws RemoteException {
        client.setInGame(inGameStatus);
    }
}

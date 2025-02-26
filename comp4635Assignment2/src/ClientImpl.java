import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ClientImpl extends UnicastRemoteObject implements ClientCallback {
    public ClientImpl() throws RemoteException {
    }

    @Override
    public void receiveMessage(String message) throws RemoteException {
        System.out.println("\n[SERVER MESSAGE]: " + message);
    }

    @Override
    public String requestPlayerInput(String playerName) throws RemoteException {
        BufferedReader consoleIn = new BufferedReader(new InputStreamReader(System.in));
        String input;

        try {
            while (true) {
                System.out.print(playerName + ", please enter a word: ");
                input = consoleIn.readLine();

                if (input != null) {
                    input = input.trim(); // Ensure no leading/trailing whitespace

                    if (!input.isEmpty()) {
                        System.out.println(playerName + " entered: " + input);
                        return input;
                    }
                }

                System.out.println("Invalid input. Please enter a non-empty word.");
            }
        } catch (IOException e) {
            System.out.println("Error reading input. Returning default response.");
            return "ERROR";
        }
    }
}
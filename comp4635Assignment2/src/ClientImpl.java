import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.locks.ReentrantLock;

public class ClientImpl extends UnicastRemoteObject implements ClientCallback {

    private final BufferedReader consoleIn = new BufferedReader(new InputStreamReader(System.in));
    private final ReentrantLock inputLock = new ReentrantLock();

    public ClientImpl() throws RemoteException {
    }

    @Override
    public void receiveMessage(String message) throws RemoteException {
        System.out.println("\n[SERVER MESSAGE]: " + message);
    }

    @Override
    public String requestPlayerInput(String playerName) throws RemoteException {
        String input;

        try {
            while (true) {
                System.out.print(playerName + ", please enter a word: ");
                input = consoleIn.readLine();

                if (input != null) {
                    input = input.trim(); // Remove extra spaces

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

    @Override
    public boolean isInputBufferEmpty() throws RemoteException {
        try {
            return !consoleIn.ready(); // Check if there's pending input
        } catch (IOException e) {
            return true; // Assume empty if error occurs
        }
    }

    @Override
    public void flushInputBuffer() throws RemoteException {
        inputLock.lock();
        try {
            while (consoleIn.ready()) {
                consoleIn.readLine(); // Consume and discard any pending input
            }
        } catch (IOException ignored) {
        } finally {
            inputLock.unlock();
        }
    }
}
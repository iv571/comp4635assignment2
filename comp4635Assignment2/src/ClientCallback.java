import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ClientCallback extends Remote {

    void receiveMessage(String message) throws RemoteException;

    public String requestPlayerInput(String playerName) throws RemoteException;

    void updateInGameStatus(boolean inGameStatus) throws RemoteException;
}
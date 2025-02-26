import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class ClientImpl extends UnicastRemoteObject implements ClientCallback {
    public ClientImpl() throws RemoteException {}

    @Override
    public void receiveMessage(String message) throws RemoteException {
        System.out.println("[SERVER MESSAGE]: " + message);
    }
}
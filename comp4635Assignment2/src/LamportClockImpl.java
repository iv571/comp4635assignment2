import java.rmi.Remote;
import java.rmi.RemoteException;

public interface LamportClockImpl extends Remote {
    int tick() throws RemoteException;
    int update(int receivedTimestamp) throws RemoteException;
    int getTime() throws RemoteException;
    // Optionally, include other methods that should be remotely accessible.
    // For example, if you need to broadcast a message:
    void send(String content, GameRoom gameRoom) throws RemoteException;
}
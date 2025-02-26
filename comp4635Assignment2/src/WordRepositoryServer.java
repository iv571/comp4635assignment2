import java.rmi.*;
import java.util.HashMap;

public interface WordRepositoryServer extends Remote {

    public boolean createWord(String word) throws RemoteException;

    public boolean removeWord(String word) throws RemoteException;

    public boolean checkWord(String word) throws RemoteException;

    public String getRandomWord(int length) throws RemoteException;

    public HashMap<String, Integer> generate_map_list(int word_len) throws RemoteException;

    public String find_matching_addtional_vertical_stem(char constraint_letter[]) throws RemoteException;

}
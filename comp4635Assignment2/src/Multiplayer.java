import java.rmi.RemoteException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Multiplayer {
    private Map<Integer, GameRoom> gameRooms; // Stores active game rooms
    private AtomicInteger gameIdCounter; // Thread-safe counter
    private Map<String, Integer> hostGameMap;

    public Multiplayer() {
        this.gameRooms = new ConcurrentHashMap<>();
        this.gameIdCounter = new AtomicInteger(1); // Start IDs from 1
        this.hostGameMap = new ConcurrentHashMap<>(); // Track host-created games
    }

    // Creates a new game room when the first player joins
    private synchronized int createGame(String host, int numPlayers, int gameLevel) throws RemoteException {
        // Check if the host has already created a game
        if (hostGameMap.containsKey(host)) {
            throw new RemoteException("Host " + host + " has already created a game and cannot create another.");
        }

        int gameId = generateGameId();
        GameRoom gameRoom = new GameRoom(gameId, numPlayers, gameLevel, host);
        gameRooms.put(gameId, gameRoom);
        hostGameMap.put(host, gameId); // Track the host's game
        System.out.println("Game room created: Game ID = " + gameId + " by " + host);
        return gameId;
    }

    public synchronized String startMultiGame(String host, int numPlayers, int gameLevel) {
        try {
            int newGameId = createGame(host, numPlayers, gameLevel);
            return "Multi-player game created! Game ID = " + newGameId
                    + "\nWaiting for " + (numPlayers - 1) + " more players to join...";
        } catch (RemoteException e) {
            return "Failed to create game: " + e.getMessage();
        }
    }

    private int generateGameId() {
        return gameIdCounter.getAndIncrement();
    }
}
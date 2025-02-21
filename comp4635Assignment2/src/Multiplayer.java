import java.rmi.RemoteException;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Multiplayer {
    private Map<Integer, GameRoom> gameRooms; // Stores active game rooms
    private AtomicInteger gameIdCounter; // Thread-safe counter
    private Map<String, Integer> hostGameMap;
    private ScheduledExecutorService scheduler; // For scheduling auto-start

    public Multiplayer() {
        this.gameRooms = new ConcurrentHashMap<>();
        this.gameIdCounter = new AtomicInteger(1); // Start IDs from 1
        this.hostGameMap = new ConcurrentHashMap<>(); // Track host-created games
        this.scheduler = Executors.newScheduledThreadPool(5);
    }

    private synchronized int createGame(String host, int numPlayers, int gameLevel) throws RemoteException {
        if (hostGameMap.containsKey(host)) {
            throw new RemoteException("Host " + host + " has already created a game and cannot create another.");
        }

        int gameId = generateGameId();
        GameRoom gameRoom = new GameRoom(gameId, numPlayers, gameLevel, host);
        gameRooms.put(gameId, gameRoom);
        hostGameMap.put(host, gameId);
        gameRoom.addPlayer(host);
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

    public synchronized String joinMultiGame(String player, int gameId) throws RemoteException {
        GameRoom game = gameRooms.get(gameId);
        if (game == null) {
            return "No game room found with ID " + gameId;
        }
        if (game.isStarted()) {
            return "Game " + gameId + " has already started. You cannot join now.";
        }

        boolean added = game.addPlayer(player);
        if (!added) {
            return "Game room is full. Cannot join.";
        }

        // Start countdown only when the second player joins
        if (game.getPlayerCount() > 1) {
            System.out.println("Starting auto-start timer for game " + gameId);
            scheduleAutoStart(gameId);
        }

        if (game.getRemainingSpot() > 0) {
            return "Still waiting for " + game.getRemainingSpot() + " more players to join...";
        } else {
            game.startGame();
            return "***** All players joined! The game is now started *****\n";
        }
    }

    private void scheduleAutoStart(int gameId) {
        scheduler.schedule(() -> {
            GameRoom game = gameRooms.get(gameId);
            if (game != null && !game.isStarted()) {
                System.out.println("***** Time is up! Auto-starting game " + gameId + " now! *****");
                game.startGame();
            }
        }, 1, TimeUnit.MINUTES);
    }

    private int generateGameId() {
        int gameId = 1000 + gameIdCounter.getAndIncrement();
        return gameId;
    }
}
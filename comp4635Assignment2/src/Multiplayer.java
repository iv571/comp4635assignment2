import java.rmi.RemoteException;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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

    // joinMultiGame(player_name, ...., .....)
    public synchronized String joinMultiGame(String player, int gameId, ClientCallback callback)
            throws RemoteException {
        GameRoom game = gameRooms.get(gameId);
        if (game == null) {
            return "No game room found with ID " + gameId;
        }
        if (game.isStarted()) {
            return "Game " + gameId + " has already started. You cannot join now.";
        }

        boolean added = game.addPlayer(player, callback);
        if (!added) {
            return "Game room is full. Cannot join.";
        }

        StringBuilder response = new StringBuilder();
        response.append("You have successfully joined Game ID ").append(gameId).append(".\n");

        // Broadcast the message to all players that a new player has joined
        game.broadcastMessage(player + " has joined the game!");

        // Start countdown only when the second player joins
        if (game.getPlayerCount() > 1 && game.getRemainingSpot() > 0) {
            System.out.println("Starting auto-start timer for game " + gameId);
            scheduleAutoStart(gameId);
            game.broadcastMessage("The game will auto-start in 1 minute if not all players join.\n");
            response.append("The game will auto-start in 1 minute if not all players join.\n");
        }

        if (game.getRemainingSpot() > 0) {
            response.append("Still waiting for ").append(game.getRemainingSpot()).append(" more players to join...\n");
            game.broadcastMessage("Still waiting for " + game.getRemainingSpot() + " more players to join...\n");
        } else {
            game.startGame();
            response.append("***** All players joined! The game is now started *****\n");
            game.broadcastMessage("***** All players joined! The game is now started *****\n");
        }

        return response.toString();
    }

    private void scheduleAutoStart(int gameId) {
        scheduler.schedule(() -> {
            GameRoom game = gameRooms.get(gameId);
            if (game != null && !game.isStarted()) {
                game.startGame(); // Ensure the game state is updated
                game.broadcastMessage("***** Time is up! Auto-starting game " + gameId + " now! *****");
                System.out.println("***** Time is up! Auto-starting game " + gameId + " now! *****");
            }
        }, 1, TimeUnit.MINUTES);
    }    

    private int generateGameId() {
        int gameId = 1000 + gameIdCounter.getAndIncrement();
        return gameId;
    }

    public synchronized String showActiveGameRooms() {
        StringBuilder info = new StringBuilder();
        info.append("=====      Active Game Rooms     =====\n");

        boolean hasActiveGames = false;

        for (GameRoom game : gameRooms.values()) {
            if (!game.isStarted()) {
                hasActiveGames = true;
                info.append("Game ID: ").append(game.getGameId()).append("\n")
                        .append("Host: ").append(game.getHost()).append("\n")
                        .append("Players Joined: ").append(game.getPlayerCount()).append("/")
                        .append(game.getTotalPlayers()).append("\n")
                        .append("Waiting for ").append(game.getRemainingSpot()).append(" more players to join...\n")
                        .append("------------------------------------------\n");
            }
        }

        if (!hasActiveGames) {
            info.append("No active game rooms available.\n");
        }

        return info.toString();
    }
}

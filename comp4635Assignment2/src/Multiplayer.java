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
        if (numPlayers < 2) {
            throw new RemoteException("At least 2 players required to start multiplayer mode.\n");
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
                    + "\nWaiting for " + (numPlayers) + " more players to join...";
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

        // Broadcast the message to all players that a new player has joined
        game.broadcastMessage(player + " has joined the game!\n" + "Waiting for the host to start the game...\n");

        return "\n";
    }

    public synchronized String startGameRoom(String hostName, int gameId) throws RemoteException {
        // Check if the host has a valid game ID
        if (!hostGameMap.containsKey(hostName)) {
            return "Host does not have a valid game room to start.";
        }

        GameRoom game = gameRooms.get(gameId);

        if (game == null) {
            return "Game with ID " + gameId + " not found.";
        }

        if (!game.playerExists(hostName)) {
            return "Host has to join the game room before start the game room";
        }

        // Start the game by calling startGame method of GameRoom
        String result = game.startGame(hostName);

        return result;
    }

    public synchronized String setActivePlayer(String player, int roomId) throws RemoteException {
        GameRoom game = gameRooms.get(roomId);

        if (game == null) {
            return "Game with ID " + roomId + " not found.";
        }

        String result = game.setActivePlayer(player);
        return result;
    }

    public synchronized String runGame(String player, int roomId, WordRepositoryServer wordServer)
            throws RemoteException {
        GameRoom game = gameRooms.get(roomId);

        if (game == null) {
            return "Game with ID " + roomId + " not found.";
        }
        String result = game.runGame(player, wordServer);
        return result;
    }

    public synchronized String leaveRoom(String player, int roomId) {
        GameRoom game = gameRooms.get(roomId);

        if (game == null) {
            return "Game with ID " + roomId + " not found.";
        }
        String result = game.leaveRoom(player);
        return result;
    }

    public boolean isActiveRoom(int gameId) throws RemoteException {
        try {
            GameRoom game = gameRooms.get(gameId);

            if (game == null) {
                throw new RemoteException("Game with ID " + gameId + " not found.");
            }

            return game.isStarted();
        } catch (Exception e) {
            throw new RemoteException("An error occurred while checking if the game room is active: " + e.getMessage(),
                    e);
        }
    }

    public synchronized boolean isGameRun(int gameId) throws RemoteException {
        try {
            GameRoom game = gameRooms.get(gameId);

            if (game == null) {
                throw new RemoteException("Game with ID " + gameId + " not found.");
            }

            return game.isGameRun();
        } catch (Exception e) {
            throw new RemoteException("An error occurred while checking if the game room is active: " + e.getMessage(),
                    e);
        }
    }

    private int generateGameId() {
        int gameId = 1000 + gameIdCounter.getAndIncrement();
        return gameId;
    }

    public String showActiveGameRooms() {
        StringBuilder info = new StringBuilder();
        info.append("=====      Active Game Rooms     =====\n");

        boolean hasActiveGames = false;

        for (GameRoom game : gameRooms.values()) {
            if (!game.isStarted() && !game.isGameFinished()) {
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

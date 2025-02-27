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

    private synchronized int createGame(String host, int numPlayers, int gameLevel, ClientCallback callback) throws RemoteException {
        if (hostGameMap.containsKey(host)) {
            throw new RemoteException("Host " + host + " has already created a game and cannot create another.");
        }
        if (numPlayers < 2) {
            throw new RemoteException("At least 2 players required to start multiplayer mode.\n");
        }
        int gameId = generateGameId();
        GameRoom gameRoom = new GameRoom(gameId, numPlayers, gameLevel, host, callback);
        gameRooms.put(gameId, gameRoom);
        hostGameMap.put(host, gameId);
      
        System.out.println("Game room created: Game ID = " + gameId + " by " + host);
        return gameId;
    }

    public synchronized String startMultiGame(String host, int numPlayers, int gameLevel, ClientCallback hostCallback) {
        try {
            int newGameId = createGame(host, numPlayers, gameLevel, hostCallback);
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
        if (game.isGameRun()) {
            game.currentTurnIndex = 0; // Ensure the first player (host) starts
            notifyNextTurn(roomId);    // Notify the first player
        }
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

    public synchronized boolean isActiveRoom(int gameId) throws RemoteException {
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
    
    public void processGuess(int gameId, String player, String guess) throws RemoteException {
        GameRoom room = gameRooms.get(gameId);
        if (room == null || !room.isGameRun()) {
            throw new RemoteException("Game room not found or not running.");
        }

        String currentPlayer = room.players.get(room.currentTurnIndex).getName();
        if (!currentPlayer.equals(player)) {
            room.playerCallbacks.get(player).receiveMessage("Not your turn yet.");
            return;
        }

        // Determine if the guess is a letter or a word
        boolean correct;
        if (guess.length() == 1) {
            char letter = guess.charAt(0);
            correct = room.puzzleServer.guessLetter(letter);
        } else {
            correct = room.puzzleServer.guessWord(guess);
        }

        if (correct) {
            // Update player's score
            Player currentPlayerObj = room.players.get(room.currentTurnIndex);
            currentPlayerObj.increaseScore();

            // Check if puzzle is solved
            if (room.puzzleServer.is_All_words_are_guessed()) {
                room.isRun = false;
                updateAllClients(gameId, "Game over! The puzzle has been solved.\nFinal Puzzle:\n" + 
                    room.puzzleServer.render_player_view_puzzle());
                try {
                	room.accountServer.updateMultiplayerScoresFromGameRoom(room.getMultiplayerScores());
                } catch (RemoteException e) {
                    System.err.println("Error updating scores: " + e.getMessage());
                }
                return;
            }
        }

        // Advance to the next player
        room.currentTurnIndex = (room.currentTurnIndex + 1) % room.players.size();
        notifyNextTurn(gameId);

        // Update all clients with current puzzle state
        updateAllClients(gameId, "Current Puzzle:\n" + room.puzzleServer.render_player_view_puzzle());
    }
    
    private void notifyNextTurn(int gameId) throws RemoteException {
        GameRoom room = gameRooms.get(gameId);
        if (room != null && room.isGameRun()) {
            Player nextPlayer = room.players.get(room.currentTurnIndex);
            String nextPlayerName = nextPlayer.getName();
            ClientCallback callback = room.playerCallbacks.get(nextPlayerName);
            if (callback != null) {
                callback.notifyTurn();
                updateAllClients(gameId, "It's " + nextPlayerName + "'s turn.");
            } else {
                // Player disconnected, remove them
                room.removePlayer(nextPlayerName);
                if (room.players.isEmpty()) {
                    room.isRun = false;
                    updateAllClients(gameId, "All players have disconnected. Game ended.");
                } else {
                    room.currentTurnIndex = room.currentTurnIndex % room.players.size();
                    notifyNextTurn(gameId); // Recursively notify the next player
                }
            }
        }
    }
    
    
    private void updateAllClients(int gameId, String message) throws RemoteException {
        GameRoom room = gameRooms.get(gameId);
        if (room != null) {
            for (ClientCallback callback : room.playerCallbacks.values()) {
                if (callback != null) {
                    callback.receiveMessage(message);
                }
            }
        }
    }

   
}

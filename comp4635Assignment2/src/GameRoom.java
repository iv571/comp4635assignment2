import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Iterator;

public class GameRoom {
    private int gameId;
    private int numPlayers;
    private int gameLevel;
    private boolean isStarted;
    private boolean isRun;
    private boolean isFinished;
    private String host;
    private List<Player> players;
    private Map<String, ClientCallback> playerCallbacks;
    private Map<String, Boolean> activePlayers; // player name
    private int currentTurnIndex = 0;
    private Mutiplayer_Puzzle puzzleServer;

    public GameRoom(int gameId, int numPlayers, int gameLevel, String host) {
        this.gameId = gameId;
        this.numPlayers = numPlayers;
        this.gameLevel = gameLevel;
        this.isStarted = false;
        this.isRun = false;
        this.isFinished = false;
        this.host = host;
        this.players = new ArrayList<>();
        this.playerCallbacks = new HashMap<>();
        this.activePlayers = new HashMap<>();
    }

    public boolean addPlayer(String playerName, ClientCallback callback) {
        if (players.size() < numPlayers) {
            Player player = new Player(playerName);
            players.add(player);
            playerCallbacks.put(playerName, callback);
            return true;
        }
        return false;
    }

    public synchronized String startGame(String hostName) {
        StringBuilder response = new StringBuilder();

        if (hostName == null || host == null) {
            response.append("Invalid host name.\n");
            return response.toString();
        }

        if (isStarted && hostName.equals(host)) {
            response.append("You already started the game!\n");
        } else if (!hostName.equals(host)) {
            response.append("Only host can start the game!\n");
        } else if (!isStarted && hostName.equals(host)) {
            isStarted = true;
            activePlayers.put(host, true);
            response.append("You have successfully started the game room.\n");
            broadcastMessage("Host <" + hostName
                    + "> has started the game: \n"
                    + "1.Type 'ready <game id>' if you are ready\n"
                    + "2.Type 'leave <game id>' if you want to quit the room\n"
                    + "NOTE:\n- Once host runs the game, you will be removed from the game room if you are not ready\n"
                    + "- Once you are ready, you cannot the quit the game\n");
        }

        return response.toString();
    }

    public synchronized String runGame(String player, WordRepositoryServer wordServer) {
        StringBuilder response = new StringBuilder();
        if (!player.equals(host)) {
            response.append("Only the host can run the game!\n");
            return response.toString();
        }

        // set up the game
        isRun = true;
        warningRunGame();
        getCurrentActivePlayers();
        shufflePlayers();

        puzzleServer = new Mutiplayer_Puzzle(players.size(), gameLevel + players.size(), wordServer);
        puzzleServer.print_solution_puzzle();

        String result = startTurns();
        response.append(result);

        endGame();

        return response.toString();
    }

    private void warningRunGame() {
        broadcastMessage("Host has run the game - Initializing the game...\n"
                + "Inactive player(s) will be removed from the game room\n");
    }

    private void endGame() {
        isStarted = false;
        isRun = false;
        isFinished = true;
        broadcastMessage("Game is terminated\n");
    }

    private String startTurns() {
        boolean singlePlayerCase = false;
        Player winner = null;
        StringBuilder response = new StringBuilder();
        List<String> addedWord = new ArrayList<>();

        while (!puzzleServer.is_All_words_are_guessed()) {
            // Count active players
            int activePlayers = 0;
            for (Player p : players) {
                if (p.getCurrentFailAttempt() > 0) {
                    activePlayers++;
                    winner = p; // Last-standing player if only one remains
                }
            }

            // If only one player is left, declare them the winner
            if (activePlayers == 1) {
                singlePlayerCase = true;
                broadcastMessage("Game over! " + winner.getName() + " is the winner!");
                break;
            }

            // Find the next available player
            int attempts = players.size(); // Prevent infinite loops if all players are out
            while (players.get(currentTurnIndex).getCurrentFailAttempt() == 0 && attempts > 0) {
                broadcastMessage(
                        players.get(currentTurnIndex).getName() + " has no remaining fail attempts and is skipped.");
                currentTurnIndex = (currentTurnIndex + 1) % players.size();
                attempts--;
            }

            // Safety check: If no valid players exist, end the game
            if (attempts == 0) {
                broadcastMessage("No active players left. Ending game.");
                break;
            }

            Player currentPlayer = players.get(currentTurnIndex);
            String currentPlayerName = currentPlayer.getName();

            broadcastMessage(puzzleServer.render_player_view_puzzle());
            broadcastMessage(currentPlayerName + ", it's your turn! Please type your word.");

            try {
                ClientCallback callback = playerCallbacks.get(currentPlayerName);
                if (callback != null) {
                    if (!callback.isInputBufferEmpty()) {
                        callback.flushInputBuffer();
                    }
                    String playerInput = callback.requestPlayerInput(currentPlayerName);

                    if ("ERROR".equals(playerInput) || "NO_INPUT".equals(playerInput)) {
                        broadcastMessage(currentPlayerName + " did not enter a valid word.");
                    } else {
                        broadcastMessage(currentPlayerName + " typed: " + playerInput);

                        if (!addedWord.contains(playerInput)) {
                            if (puzzleServer.is_guessed_word_correct(playerInput)) {
                                addedWord.add(playerInput);
                                currentPlayer.increaseScore();
                                broadcastMessage("Player " + currentPlayerName + "'s guess is correct! Add 1 score");
                                broadcastMessage(puzzleServer.render_player_view_puzzle());
                            } else {
                                currentPlayer.decrementFailAttempt();
                                broadcastMessage(
                                        "Player " + currentPlayerName + "'s guess is incorrect! Deduct 1 Fail Attempt");
                            }
                        } else {
                            currentPlayer.decrementFailAttempt();
                            broadcastMessage(
                                    "Player " + currentPlayerName + "'s guess is duplicated! Deduct 1 Fail Attempt");
                        }
                        broadcastMessage(
                                "Player " + currentPlayerName + " - Earned Scores: " + currentPlayer.getScore());
                        broadcastMessage("Player " + currentPlayerName + " - Remaining Fail Attempts: "
                                + currentPlayer.getCurrentFailAttempt() + "\n");
                    }
                } else {
                    broadcastMessage("Player " + currentPlayerName + " is unavailable and has been removed.");
                    removePlayer(currentPlayerName);
                }
            } catch (RemoteException e) {
                broadcastMessage("Error communicating with " + currentPlayerName + ". Removing player...");
                removePlayer(currentPlayerName);
            }

            currentTurnIndex = (currentTurnIndex + 1) % players.size();
        }

        // Determine winner if not decided by single-player elimination
        if (!singlePlayerCase) {
            winner = findWinner();
        } else {
            if (winner.getScore() == 0) {
                winner = null;
            }
        }

        // Ensure winner is valid before broadcasting
        if (winner != null) {
            response.append("WINNER: ").append(winner.getName()).append(" - Total Scores: ").append(winner.getScore())
                    .append("\n");
            broadcastMessage(response.toString());
        } else {
            response.append("No winner. Game ended with no active players.\n");
            broadcastMessage("No winner. Game ended with no active players.");
        }

        return response.toString();
    }

    private Player findWinner() {
        if (players.isEmpty()) {
            return null; // No players available
        }

        Player winner = players.get(0);
        for (Player p : players) {
            if (p.getScore() > winner.getScore()) {
                winner = p;
            } else if (p.getScore() == winner.getScore()) {
                // If scores are the same, compare fail attempts
                if (p.getCurrentFailAttempt() > winner.getCurrentFailAttempt()) {
                    winner = p;
                }
            }
        }
        return winner;
    }

    public synchronized String getCurrentPlayerTurn() {
        if (players.isEmpty()) {
            return null; // No players in the game
        }
        return players.get(currentTurnIndex).getName();
    }

    private void getCurrentActivePlayers() {
        Iterator<Player> iterator = players.iterator();

        while (iterator.hasNext()) {
            Player player = iterator.next();
            String playerName = player.getName();

            if (!activePlayers.containsKey(playerName) || !activePlayers.get(playerName)) {
                iterator.remove();
                playerCallbacks.remove(playerName);
                System.out.println("Removed inactive player: " + playerName);
            }
        }
    }

    public void broadcastMessage(String message) {
        for (Map.Entry<String, ClientCallback> entry : playerCallbacks.entrySet()) {
            String playerName = entry.getKey();
            ClientCallback callback = entry.getValue();
            if (callback == null) {
                System.err.println("Callback for player " + playerName + " is null.");
                continue;
            }
            try {
                callback.receiveMessage(message);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private void shufflePlayers() {
        System.out.println("Before Shuffle: \n");
        for (int i = 0; i < players.size(); i++) {
            System.out.println((i + 1) + ". " + players.get(i).getName());
        }

        Collections.shuffle(players);
        broadcastMessage("The players have been shuffled.");

        System.out.println("After Shuffle: \n");
        for (int i = 0; i < players.size(); i++) {
            System.out.println((i + 1) + ". " + players.get(i).getName());
            broadcastMessage((i + 1) + ". " + players.get(i).getName());
        }
    }

    public synchronized String setActivePlayer(String player) {
        StringBuilder response = new StringBuilder();
        boolean playerExists = playerExists(player);
        if (!playerExists) {
            response.append("You have not joined this game room\n");
            return response.toString();
        }
        if (!activePlayers.containsKey(player)) {
            isRun = true;
            activePlayers.put(player, true);
            broadcastMessage("Player " + player + " is ready\nWaiting for the host to run the game...\n");
            response.append("You have been marked as ready.\n");
        } else {
            response.append("You are already marked as ready.\n");
        }
        return response.toString();
    }

    public synchronized String leaveRoom(String player) {
        StringBuilder response = new StringBuilder();

        boolean playerExists = playerExists(player);
        if (!playerExists) {
            response.append("You have not joined this game room\n");
            return response.toString();
        }

        if (!activePlayers.containsKey(player)) {
            removePlayer(player);
            activePlayers.remove(player);
            playerCallbacks.remove(player);
            broadcastMessage("Player " + player + " has left the room\n");
        }
        response.append("You have left the room.\n");

        return response.toString();
    }

    public boolean playerExists(String playerName) {
        for (Player p : players) {
            if (p.getName().equals(playerName)) {
                return true;
            }
        }
        return false;
    }

    private synchronized boolean removePlayer(String playerName) {
        return players.removeIf(player -> player.getName().equals(playerName));
    }

    public boolean isStarted() {
        return this.isStarted;
    }

    public boolean isGameFinished() {
        return this.isFinished;
    }

    public boolean isGameRun() {
        return this.isRun;
    }

    public int getRemainingSpot() {
        return this.numPlayers - this.players.size();
    }

    public int getPlayerCount() {
        return players.size();
    }

    public int getGameId() {
        return this.gameId;
    }

    public String getHost() {
        return this.host;
    }

    public int getTotalPlayers() {
        return this.numPlayers;
    }

    private static class Player {
        private String name;
        private int currentFailedAttempts;
        private static final int TOTAL_FAILED_ATTEMPTS = 5;
        private int score;

        public Player(String name) {
            this.name = name;
            this.currentFailedAttempts = TOTAL_FAILED_ATTEMPTS;
            this.score = 0;
        }

        public String getName() {
            return this.name;
        }

        public void increaseScore() {
            score++;
        }

        public void decrementFailAttempt() {
            currentFailedAttempts -= 1;
        }

        public int getCurrentFailAttempt() {
            return currentFailedAttempts;
        }

        public int getScore() {
            return score;
        }
    }
}
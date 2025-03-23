import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Client {
    private static final String USAGE = "java Client rmi://localhost:1099/GameServer YourClientName";

    CrissCrossPuzzleServer puzzleServer;
    UserAccountServer accountServer;
    WordRepositoryServer wordServer;
    private String serverUrl;
    private ClientCallback clientCallback;
    String clientname;
    String username = " ";
    int activeGameID = -1;
    // [At-most-once] Sequence number for deduplication of requests
    private int sequenceNumber = 0;

    enum CommandName {
        start,      // start <numberOfWords> <failedAttemptFactor>
        letter,     // letter <character>
        word,       // word <word>
        end,        // end
        restart,    // restart
        add,        // add <word>
        remove,     // remove <word>
        check,      // check <word>
        score,      // check score
        scoreboard, // view scoreboard
        help,       // help
        startmultiplayer,  // start multiplayer game
        joinmultiplayer,   // join the multiplayer game
        startgameroom,     // start the game room (Only host can start)
        showactivegames,   // show existing game rooms
        multiscoreboard,   // view multiplayer scoreboard
        ready,      // ready for the game room (set active player)
        leave,      // leave the game room
        rungame,    // run the game
        quit        // quit
    }

    public Client(String serverUrl, String clientName) {
        this.serverUrl = serverUrl;
        this.clientname = clientName;

        try {
            // Look up the remote puzzle server object using the provided URL.
            puzzleServer = (CrissCrossPuzzleServer) Naming.lookup(serverUrl);
            clientCallback = new ClientImpl();
            // Look up the remote user account server and word repository server.
            accountServer = (UserAccountServer) Naming.lookup("rmi://localhost:1099/UserAccountServer");
            wordServer = (WordRepositoryServer) Naming.lookup("rmi://localhost:1099/WordRepositoryServer");
        } catch (Exception e) {
            System.out.println("The runtime failed: " + e.getMessage());
            System.exit(0);
        }
        System.out.println("Connected to puzzle server: " + serverUrl);
    }

    /**
     * Displays the authentication menu.
     */
    private void displayAuthenticationMenu() {
        System.out.println("--------- Welcome to the Game Server ---------");
        System.out.println("Please create an account or login to continue:");
        System.out.println("Commands:");
        System.out.println("  create <username> <password>");
        System.out.println("  login <username> <password>");
        System.out.println();
    }

    public void run() {
        BufferedReader consoleIn = new BufferedReader(new InputStreamReader(System.in));
        boolean authenticated = false;
        // Authentication loop
        while (!authenticated) {
            displayAuthenticationMenu();
            System.out.print("Auth> ");
            try {
                String authInput = consoleIn.readLine();
                if (authInput == null || authInput.trim().isEmpty()) {
                    continue;
                }
                StringTokenizer st = new StringTokenizer(authInput);
                String command = st.nextToken().toUpperCase();
                if (!st.hasMoreTokens()) {
                    System.out.println("Username required.");
                    continue;
                }
                username = st.nextToken();
                if (!st.hasMoreTokens()) {
                    System.out.println("Password required.");
                    continue;
                }
                String password = st.nextToken();

                if (command.equals("CREATE")) {
                    try {
                        boolean created = accountServer.createAccount(username, password);
                        if (created) {
                            System.out.println("Account created successfully. Please log in.");
                        } else {
                            System.out.println("Account already exists. Try logging in.");
                        }
                    } catch (RemoteException re) {
                        if (re.getMessage().contains("Connection refused")) {
                            System.out.println("Connection to the user account server was lost. Attempting to reconnect...");
                            reconnectUserAccountServer();
                            try {
                                boolean created = accountServer.createAccount(username, password);
                                if (created) {
                                    System.out.println("Account created successfully. Please log in.");
                                } else {
                                    System.out.println("Account already exists. Try logging in.");
                                }
                            } catch (RemoteException re2) {
                                if (re2.getMessage().contains("Connection refused")) {
                                    System.out.println("The user account server is currently unavailable. Please try again later.");
                                } else {
                                    System.out.println("Remote error after reconnection attempt: " + re2.getMessage());
                                }
                            }
                        } else {
                            System.out.println("Remote error: " + re.getMessage());
                        }
                    }
                } else if (command.equals("LOGIN")) {
                    try {
                        boolean loggedIn = accountServer.loginAccount(username, password);
                        if (loggedIn) {
                            System.out.println("Login successful. Welcome, " + username + "!");
                            clientname = username; // set the client name after successful login
                            // [At-most-once] Initialize sequence number as specified by client
                            System.out.print("Set initial sequence number for requests: ");
                            String seqInput = consoleIn.readLine();
                            try {
                                sequenceNumber = Integer.parseInt(seqInput.trim());
                            } catch (NumberFormatException nfe) {
                                sequenceNumber = 1;  // default to 1 if invalid input
                            }
                            System.out.println("Initial sequence number set to " + sequenceNumber);
                            authenticated = true;
                        } else {
                            System.out.println("Login failed. Please check your credentials and try again.");
                        }
                    } catch (RemoteException re) {
                        if (re.getMessage().contains("Connection refused")) {
                            System.out.println("Connection to the user account server was lost. Attempting to reconnect...");
                            reconnectUserAccountServer();
                            try {
                                boolean loggedIn = accountServer.loginAccount(username, password);
                                if (loggedIn) {
                                    System.out.println("Login successful. Welcome, " + username + "!");
                                    clientname = username;
                                    // [At-most-once] Initialize sequence number on login after reconnection
                                    System.out.print("Set initial sequence number for requests: ");
                                    String seqInput = consoleIn.readLine();
                                    try {
                                        sequenceNumber = Integer.parseInt(seqInput.trim());
                                    } catch (NumberFormatException nfe) {
                                        sequenceNumber = 1;
                                    }
                                    System.out.println("Initial sequence number set to " + sequenceNumber);
                                    authenticated = true;
                                } else {
                                    System.out.println("Login failed. Please check your credentials and try again.");
                                }
                            } catch (RemoteException re2) {
                                if (re2.getMessage().contains("Connection refused")) {
                                    System.out.println("The user account server is currently unavailable. Please try again later.");
                                } else {
                                    System.out.println("Remote error after reconnection attempt: " + re2.getMessage());
                                }
                            }
                        } else {
                            System.out.println("Remote error: " + re.getMessage());
                        }
                    }
                } else {
                    System.out.println("Unknown command. Please use CREATE or LOGIN.");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Display the help menu right after successful login.
        printHelp();

        // Main command loop
        while (true) {
            System.out.print(clientname + "@" + serverUrl + "> ");
            try {
                // Before accepting a new command, check multiplayer room status
                if (!puzzleServer.isValidRoomID(username, activeGameID, sequenceNumber)) {
                    // [At-most-once] Simulate possible duplicate for isValidRoomID
                    if (Math.random() < 0.5) {
                        try {
                            boolean dupValid = puzzleServer.isValidRoomID(username, activeGameID, sequenceNumber);
                        } catch (RemoteException dre) {
                            System.out.println("[Client] Duplicate isValidRoomID call error: " + dre.getMessage());
                        }
                    }
                    sequenceNumber++;
                    activeGameID = -1;
                } else {
                    // If valid room ID call succeeded (even if returns true/false, it's a valid call)
                    if (Math.random() < 0.5) {
                        try {
                            puzzleServer.isValidRoomID(username, activeGameID, sequenceNumber);
                            // We don't particularly need the duplicate result here beyond debugging
                        } catch (RemoteException dre) {
                            System.out.println("[Client] Duplicate isValidRoomID call error: " + dre.getMessage());
                        }
                    }
                    sequenceNumber++;
                }
                if (activeGameID != -1) {
                    boolean gameRunning = puzzleServer.isGameRun(username, activeGameID, sequenceNumber);
                    // [At-most-once] Simulate duplicate for isGameRun
                    if (Math.random() < 0.5) {
                        try {
                            boolean dupRun = puzzleServer.isGameRun(username, activeGameID, sequenceNumber);
                        } catch (RemoteException dre) {
                            System.out.println("[Client] Duplicate isGameRun call error: " + dre.getMessage());
                        }
                    }
                    sequenceNumber++;
                    boolean roomActive = false;
                    if (gameRunning) {
                        roomActive = puzzleServer.isActiveRoom(username, activeGameID, sequenceNumber);
                        // [At-most-once] Simulate duplicate for isActiveRoom
                        if (Math.random() < 0.5) {
                            try {
                                boolean dupActive = puzzleServer.isActiveRoom(username, activeGameID, sequenceNumber);
                            } catch (RemoteException dre) {
                                System.out.println("[Client] Duplicate isActiveRoom call error: " + dre.getMessage());
                            }
                        }
                        sequenceNumber++;
                    }
                    if (gameRunning && roomActive) {
                        System.out.println("WAITING FOR THE HOST TO START THE GAME - HANG ON.....\n");
                        // If waiting, skip reading a new command this iteration
                        continue;
                    }
                }
                // Read user input for a new command
                String userInput = consoleIn.readLine();
                execute(parse(userInput), clientname);
            } catch (RejectedException re) {
                System.out.println(re.getMessage());
            } catch (RemoteException re) {
                // Catch any RemoteExceptions from the status checks
                System.out.println("Remote error: " + re.getMessage());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private Command parse(String userInput) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return null;
        }
        String trimmed = userInput.trim();
        // Shortcut commands for convenience
        if (trimmed.equals("$")) {
            return new Command(CommandName.score, "", 0);
        } else if (trimmed.equals("!")) {
            return new Command(CommandName.restart, "", 0);
        } else if (trimmed.equals("#")) {
            return new Command(CommandName.end, "", 0);
        }
        // Parse normal commands
        String[] tokens = trimmed.split("\\s+");
        CommandName commandName;
        try {
            commandName = CommandName.valueOf(tokens[0].toLowerCase());
        } catch (IllegalArgumentException e) {
            // Unknown command
            return new Command(null, "", 0);
        }
        Command command = new Command(commandName, "", 0);
        // Assign parameters if present
        if (tokens.length >= 2) {
            command.param1 = tokens[1];
        }
        if (tokens.length >= 3) {
            command.param2 = tokens[2];
        }
        return command;
    }

    void execute(Command command, String clientName) throws RemoteException, RejectedException {
        if (command == null || command.commandName == null) {
            System.out.println("Unknown command");
            return;
        }
        try {
            switch (command.commandName) {
                case start: {
                    // start <numberOfWords> <failedAttemptFactor>
                    int numberOfWords = Integer.parseInt(command.param1);
                    int failedAttemptFactor = Integer.parseInt(command.param2);
                    // Invoke remote startGame with sequence number
                    String startResponse = puzzleServer.startGame(username, numberOfWords, failedAttemptFactor, sequenceNumber);
                    System.out.println(startResponse);
                    // [At-most-once] Simulate duplicate invocation
                    if (Math.random() < 0.5) {
                        
                        try {
                            String dupResponse = puzzleServer.startGame(username, numberOfWords, failedAttemptFactor, sequenceNumber);
                        } catch (RemoteException reDup) {
                            
                        }
                    }
                    sequenceNumber++;  // increment only after successful call
                    break;
                }
                case letter: {
                    // letter <character>
                    char letter = command.param1.charAt(0);
                    String letterResponse = puzzleServer.guessLetter(username, letter, sequenceNumber);
                    System.out.println(letterResponse);
                    if (Math.random() < 0.5) {
                       
                        try {
                            String dupResponse = puzzleServer.guessLetter(username, letter, sequenceNumber);
                            
                        } catch (RemoteException reDup) {
                            System.out.println("[Client] Duplicate request threw exception: " + reDup.getMessage());
                        }
                    }
                    sequenceNumber++;
                    break;
                }
                case word: {
                    // word <word>
                    String guess = command.param1;
                    String wordResponse = puzzleServer.guessWord(username, guess, sequenceNumber);
                    System.out.println(wordResponse);
                    if (Math.random() < 0.5) {
                       
                        try {
                            String dupResponse = puzzleServer.guessWord(username, guess, sequenceNumber);
                           
                        } catch (RemoteException reDup) {
                            System.out.println("[Client] Duplicate request threw exception: " + reDup.getMessage());
                        }
                    }
                    sequenceNumber++;
                    break;
                }
                case end: {
                    String endResponse = puzzleServer.endGame(username, sequenceNumber);
                    System.out.println(endResponse);
                    if (Math.random() < 0.5) {
                        System.out.println("[Client] Repeating endGame (seq " + sequenceNumber + ")");
                        try {
                            String dupResponse = puzzleServer.endGame(username, sequenceNumber);
                            System.out.println("[Client] Duplicate response: " + dupResponse);
                        } catch (RemoteException reDup) {
                            System.out.println("[Client] Duplicate request threw exception: " + reDup.getMessage());
                        }
                    }
                    sequenceNumber++;
                    break;
                }
                case restart: {
                    String restartResponse = puzzleServer.restartGame(username, sequenceNumber);
                    System.out.println(restartResponse);
                    if (Math.random() < 0.5) {
                        System.out.println("[Client] Repeating restartGame (seq " + sequenceNumber + ")");
                        try {
                            String dupResponse = puzzleServer.restartGame(username, sequenceNumber);
                            System.out.println("[Client] Duplicate response: " + dupResponse);
                        } catch (RemoteException reDup) {
                            System.out.println("[Client] Duplicate request threw exception: " + reDup.getMessage());
                        }
                    }
                    sequenceNumber++;
                    break;
                }
                case add: {
                    // add <word>
                    try {
                        boolean addSuccess = puzzleServer.addWord(username, command.param1, sequenceNumber);
                        System.out.println(addSuccess ? "Word added successfully." : "Failed to add word.");
                        // Simulate duplicate call on success (the call returned, even if false means it executed)
                        if (Math.random() < 0.5) {
                            System.out.println("[Client] Repeating addWord('" + command.param1 + "') (seq " + sequenceNumber + ")");
                            try {
                                boolean dupResult = puzzleServer.addWord(username, command.param1, sequenceNumber);
                                System.out.println("[Client] Duplicate addWord result: " + (dupResult ? "success" : "failure"));
                            } catch (RemoteException reDup) {
                                System.out.println("[Client] Duplicate addWord request error: " + reDup.getMessage());
                            }
                        }
                        sequenceNumber++;
                    } catch (RemoteException re) {
                        if (re.getMessage() != null && re.getMessage().contains("Connection refused")) {
                            System.out.println("Connection to PuzzleServer refused. Attempting to reconnect...");
                            reconnectPuzzleServer();
                            if (puzzleServer != null) {
                                try {
                                    boolean addSuccess = puzzleServer.addWord(username, command.param1, sequenceNumber);
                                    System.out.println(addSuccess ? "Word added successfully." : "Failed to add word.");
                                    if (Math.random() < 0.5) {
                                        System.out.println("[Client] Repeating addWord('" + command.param1 + "') (seq " + sequenceNumber + ")");
                                        try {
                                            boolean dupResult = puzzleServer.addWord(username, command.param1, sequenceNumber);
                                            System.out.println("[Client] Duplicate addWord result: " + (dupResult ? "success" : "failure"));
                                        } catch (RemoteException reDup) {
                                            System.out.println("[Client] Duplicate addWord request error: " + reDup.getMessage());
                                        }
                                    }
                                    sequenceNumber++;
                                } catch (RemoteException re2) {
                                    System.out.println("Remote error after reconnection attempt: " + re2.getMessage());
                                }
                            }
                        } else {
                            System.out.println("Remote error: " + re.getMessage());
                        }
                    }
                    break;
                }
                case remove: {
                    // remove <word>
                    try {
                        boolean removeSuccess = puzzleServer.removeWord(username, command.param1, sequenceNumber);
                        System.out.println(removeSuccess ? "Word removed successfully." : "Failed to remove word.");
                        if (Math.random() < 0.5) {
                            System.out.println("[Client] Repeating removeWord('" + command.param1 + "') (seq " + sequenceNumber + ")");
                            try {
                                boolean dupResult = puzzleServer.removeWord(username, command.param1, sequenceNumber);
                                System.out.println("[Client] Duplicate removeWord result: " + (dupResult ? "success" : "failure"));
                            } catch (RemoteException reDup) {
                                System.out.println("[Client] Duplicate removeWord request error: " + reDup.getMessage());
                            }
                        }
                        sequenceNumber++;
                    } catch (RemoteException re) {
                        if (re.getMessage() != null && re.getMessage().contains("Connection refused")) {
                            System.out.println("Connection to PuzzleServer refused. Attempting to reconnect...");
                            reconnectPuzzleServer();
                            if (puzzleServer != null) {
                                try {
                                    boolean removeSuccess = puzzleServer.removeWord(username, command.param1, sequenceNumber);
                                    System.out.println(removeSuccess ? "Word removed successfully." : "Failed to remove word.");
                                    if (Math.random() < 0.5) {
                                        System.out.println("[Client] Repeating removeWord('" + command.param1 + "') (seq " + sequenceNumber + ")");
                                        try {
                                            boolean dupResult = puzzleServer.removeWord(username, command.param1, sequenceNumber);
                                            System.out.println("[Client] Duplicate removeWord result: " + (dupResult ? "success" : "failure"));
                                        } catch (RemoteException reDup) {
                                            System.out.println("[Client] Duplicate removeWord request error: " + reDup.getMessage());
                                        }
                                    }
                                    sequenceNumber++;
                                } catch (RemoteException re2) {
                                    System.out.println("Remote error after reconnection attempt: " + re2.getMessage());
                                }
                            }
                        } else {
                            System.out.println("Remote error: " + re.getMessage());
                        }
                    }
                    break;
                }
                case check: {
                    // check <word>
                    try {
                        boolean exists = puzzleServer.checkWord(username, command.param1, sequenceNumber);
                        System.out.println(exists ? "Word exists in the repository." : "Word does not exist in the repository.");
                        if (Math.random() < 0.5) {
                            System.out.println("[Client] Repeating checkWord('" + command.param1 + "') (seq " + sequenceNumber + ")");
                            try {
                                boolean dupExists = puzzleServer.checkWord(username, command.param1, sequenceNumber);
                                System.out.println("[Client] Duplicate checkWord result: " + (dupExists ? "exists" : "does not exist"));
                            } catch (RemoteException reDup) {
                                System.out.println("[Client] Duplicate checkWord request error: " + reDup.getMessage());
                            }
                        }
                        sequenceNumber++;
                    } catch (RemoteException re) {
                        if (re.getMessage() != null && re.getMessage().contains("Connection refused")) {
                            System.out.println("Connection to PuzzleServer refused. Attempting to reconnect...");
                            reconnectPuzzleServer();
                            if (puzzleServer != null) {
                                try {
                                    boolean exists = puzzleServer.checkWord(username, command.param1, sequenceNumber);
                                    System.out.println(exists ? "Word exists in the repository." : "Word does not exist in the repository.");
                                    if (Math.random() < 0.5) {
                                        System.out.println("[Client] Repeating checkWord('" + command.param1 + "') (seq " + sequenceNumber + ")");
                                        try {
                                            boolean dupExists = puzzleServer.checkWord(username, command.param1, sequenceNumber);
                                            System.out.println("[Client] Duplicate checkWord result: " + (dupExists ? "exists" : "does not exist"));
                                        } catch (RemoteException reDup) {
                                            System.out.println("[Client] Duplicate checkWord request error: " + reDup.getMessage());
                                        }
                                    }
                                    sequenceNumber++;
                                } catch (RemoteException re2) {
                                    System.out.println("Remote error after reconnection attempt: " + re2.getMessage());
                                }
                            }
                        } else {
                            System.out.println("Remote error: " + re.getMessage());
                        }
                    }
                    break;
                }
                case startmultiplayer: {
                    // startmultiplayer <numPlayers> <level>
                    if (command.param1 == null || command.param2 == null) {
                        System.out.println("Usage: startmultiplayer <numberOfPlayers> <level>");
                        break;
                    }
                    int numPlayers = Integer.parseInt(command.param1);
                    int level = Integer.parseInt(command.param2);
                    String startMPResponse = puzzleServer.startMultiGame(username, numPlayers, level, sequenceNumber);
                    System.out.println(startMPResponse);
                    if (Math.random() < 0.5) {
                       
                        try {
                            String dupResponse = puzzleServer.startMultiGame(username, numPlayers, level, sequenceNumber);
                            
                        } catch (RemoteException reDup) {
                            System.out.println("[Client] Duplicate startMultiGame error: " + reDup.getMessage());
                        }
                    }
                    sequenceNumber++;
                    break;
                }
                case joinmultiplayer: {
                    // joinmultiplayer <gameId>
                    if (command.param1 == null) {
                        System.out.println("Usage: joinmultiplayer <gameId>");
                        break;
                    }
                    int gameId = Integer.parseInt(command.param1);
                    String joinMPResponse = puzzleServer.joinMultiGame(username, gameId, clientCallback, sequenceNumber);
                    System.out.println(joinMPResponse);
                    if (Math.random() < 0.5) {
                        
                        try {
                            String dupResponse = puzzleServer.joinMultiGame(username, gameId, clientCallback, sequenceNumber);
                            System.out.println("[Client] Duplicate response: " + dupResponse);
                        } catch (RemoteException reDup) {
                            System.out.println("[Client] Duplicate joinMultiGame error: " + reDup.getMessage());
                        }
                    }
                    sequenceNumber++;
                    break;
                }
                case startgameroom: {
                    // startgameroom <gameId>
                    if (command.param1 == null) {
                        System.out.println("Usage: startgameroom <gameId>");
                        break;
                    }
                    int roomId = Integer.parseInt(command.param1);
                    String gameMPResponse = puzzleServer.startGameRoom(username, roomId, sequenceNumber);
                    System.out.println(gameMPResponse);
                    if (gameMPResponse.equals("You have successfully started the game room.\n")) {
                        activeGameID = roomId;
                    }
                    if (Math.random() < 0.5) {
                        System.out.println("[Client] Repeating startGameRoom (roomId=" + roomId + ", seq " + sequenceNumber + ")");
                        try {
                            String dupResponse = puzzleServer.startGameRoom(username, roomId, sequenceNumber);
                            System.out.println("[Client] Duplicate response: " + dupResponse);
                        } catch (RemoteException reDup) {
                            System.out.println("[Client] Duplicate startGameRoom error: " + reDup.getMessage());
                        }
                    }
                    sequenceNumber++;
                    break;
                }
                case rungame: {
                    // rungame <gameId>
                    if (command.param1 == null) {
                        System.out.println("Usage: rungame <gameId>");
                        break;
                    }
                    int roomId = Integer.parseInt(command.param1);
                    if (activeGameID != -1 && roomId == activeGameID && puzzleServer.isActiveRoom(username, activeGameID, sequenceNumber)) {
                        // Note: Checking isActiveRoom here is a secondary check; activeGameID logic above already ensures game is ready.
                        sequenceNumber++; // increment for the isActiveRoom check call above
                        String runMPResponse = puzzleServer.runGame(username, activeGameID, wordServer, sequenceNumber);
                        System.out.println(runMPResponse);
                        if (Math.random() < 0.5) {
                            System.out.println("[Client] Repeating runGame (roomId=" + roomId + ", seq " + sequenceNumber + ")");
                            try {
                                String dupResponse = puzzleServer.runGame(username, activeGameID, wordServer, sequenceNumber);
                                System.out.println("[Client] Duplicate response: " + dupResponse);
                            } catch (RemoteException reDup) {
                                System.out.println("[Client] Duplicate runGame error: " + reDup.getMessage());
                            }
                        }
                        sequenceNumber++;
                        // If a winner is declared, update score
                        String trimmedResp = runMPResponse.trim();
                        Pattern pattern = Pattern.compile("WINNER: (.+?) - Total Scores: (\\d+)");
                        Matcher matcher = pattern.matcher(trimmedResp);
                        if (matcher.find()) {
                            String winnerName = matcher.group(1);
                            int score = Integer.parseInt(matcher.group(2));
                            accountServer.updateScore(winnerName, score, true);
                        }
                    } else {
                        System.out.println("This is not an active game id or you are not the host.");
                    }
                    break;
                }
                case ready: {
                    // ready <gameId> (mark player as ready in room)
                    if (command.param1 == null) {
                        System.out.println("Usage: ready <gameId>");
                        break;
                    }
                    int roomId = Integer.parseInt(command.param1);
                    String readyMPResponse = puzzleServer.setActivePlayer(username, roomId, sequenceNumber);
                    System.out.println(readyMPResponse);
                    if (readyMPResponse.equals("You have been marked as ready.\n")) {
                        activeGameID = roomId;
                    }
                    if (Math.random() < 0.5) {
                        System.out.println("[Client] Repeating setActivePlayer (ready) (roomId=" + roomId + ", seq " + sequenceNumber + ")");
                        try {
                            String dupResponse = puzzleServer.setActivePlayer(username, roomId, sequenceNumber);
                            System.out.println("[Client] Duplicate response: " + dupResponse);
                        } catch (RemoteException reDup) {
                            System.out.println("[Client] Duplicate ready request error: " + reDup.getMessage());
                        }
                    }
                    sequenceNumber++;
                    break;
                }
                case leave: {
                    // leave <gameId>
                    if (command.param1 == null) {
                        System.out.println("Usage: leave <gameId>");
                        break;
                    }
                    int roomId = Integer.parseInt(command.param1);
                    String leaveMPResponse = puzzleServer.leaveRoom(username, roomId, sequenceNumber);
                    System.out.println(leaveMPResponse);
                    if (Math.random() < 0.5) {
                        System.out.println("[Client] Repeating leaveRoom (roomId=" + roomId + ", seq " + sequenceNumber + ")");
                        try {
                            String dupResponse = puzzleServer.leaveRoom(username, roomId, sequenceNumber);
                            System.out.println("[Client] Duplicate response: " + dupResponse);
                        } catch (RemoteException reDup) {
                            System.out.println("[Client] Duplicate leave request error: " + reDup.getMessage());
                        }
                    }
                    sequenceNumber++;
                    break;
                }
                case showactivegames: {
                    String activeRooms = puzzleServer.showActiveGameRooms(username, sequenceNumber);
                    System.out.println(activeRooms);
                    if (Math.random() < 0.5) {
                        System.out.println("[Client] Repeating showActiveGameRooms (seq " + sequenceNumber + ")");
                        try {
                            String dupRooms = puzzleServer.showActiveGameRooms(username, sequenceNumber);
                            System.out.println("[Client] Duplicate response:\n" + dupRooms);
                        } catch (RemoteException reDup) {
                            System.out.println("[Client] Duplicate showActiveGameRooms error: " + reDup.getMessage());
                        }
                    }
                    sequenceNumber++;
                    break;
                }
                case help: {
                    printHelp();
                    break;
                }
                case score: {
                    // Get the current user's score (single-player)
                    int score = accountServer.getScore(username);
                    System.out.println("Your current score is: " + score);
                    break;
                }
                case scoreboard: {
                    try {
                        java.util.Map<String, Integer> scoreboard = accountServer.getScoreboard(false);
                        System.out.println("---- Scoreboard ----");
                        if (scoreboard.isEmpty()) {
                            System.out.println("No scores available.");
                        } else {
                            for (java.util.Map.Entry<String, Integer> entry : scoreboard.entrySet()) {
                                System.out.println(entry.getKey() + " : " + entry.getValue());
                            }
                        }
                        System.out.println("--------------------");
                    } catch (RemoteException e) {
                        System.out.println("Error retrieving scoreboard: " + e.getMessage());
                    }
                    break;
                }
                case multiscoreboard: {
                    try {
                        java.util.Map<String, Integer> multiScoreboard = accountServer.getScoreboard(true);
                        System.out.println("---- Multiplayer Scoreboard ----");
                        if (multiScoreboard.isEmpty()) {
                            System.out.println("No scores available.");
                        } else {
                            for (java.util.Map.Entry<String, Integer> entry : multiScoreboard.entrySet()) {
                                System.out.println(entry.getKey() + " : " + entry.getValue());
                            }
                        }
                        System.out.println("-------------------------------");
                    } catch (RemoteException e) {
                        System.out.println("Error retrieving multiplayer scoreboard: " + e.getMessage());
                    }
                    break;
                }
                case quit: {
                    System.out.println("Quitting...");
                    System.exit(0);
                    break;
                }
                default: {
                    System.out.println("Unknown command");
                    break;
                }
            }
        } catch (RemoteException re) {
            System.out.println("Remote error: " + re.getMessage());
        } catch (NumberFormatException nfe) {
            System.out.println("Invalid number format: " + nfe.getMessage());
        }
    }

    private class Command {
        private CommandName commandName;
        String param1;
        String param2;
        Command(CommandName name, String p1, float dummy) {
            this.commandName = name;
            this.param1 = p1;
            this.param2 = null;
        }
        private CommandName getCommandName() {
            return commandName;
        }
    }

    private void printHelp() {
        String border = "+--------------------------------------------------------------------------+";
        String title = "|                            CRISS CROSS PUZZLE                            |";
        String emptyLine = "|                                                                       |";
        
        System.out.println(border);
        System.out.println(title);
        System.out.println(border);
        System.out.println("|                            AVAILABLE COMMANDS                            |");
        System.out.println(border);
        System.out.println("|  start <numWords> <failFactor>      : Start a new single-player game     |");
        System.out.println("|  letter <char>                      : Guess a letter in the puzzle       |");
        System.out.println("|  word <word>                        : Guess the whole or horizontal word |");
        System.out.println("|  end                                : End the current game               |");
        System.out.println("|  restart                            : Restart the game (new puzzle)      |");
        System.out.println("|  add <word>                         : Add a word (admin feature)         |");
        System.out.println("|  remove <word>                      : Remove a word (admin feature)      |");
        System.out.println("|  check <word>                       : Check if a word exists             |");
        System.out.println("|  startmultiplayer <players> <level> : Create a new multiplayer game room |");
        System.out.println("|  joinmultiplayer <gameId>           : Join an existing multiplayer room  |");
        System.out.println("|  startgameroom <gameId>             : Host starts the game room          |");
        System.out.println("|  ready <gameId>                     : Mark yourself as ready             |");
        System.out.println("|  leave <gameId>                     : Leave the game room                |");
        System.out.println("|  rungame <gameId>                   : Run the game (host only)           |");
        System.out.println("|  showactivegames                    : List all active game rooms         |");
        System.out.println("|  score                              : Show your single-player score      |");
        System.out.println("|  scoreboard                         : View single-player scoreboard      |");
        System.out.println("|  multiscoreboard                    : View multiplayer scoreboard        |");
        System.out.println("|  help                               : Display this help menu             |");
        System.out.println("|  quit                               : Exit the game                      |");
        System.out.println(border);
    }

    // Helper method to re-lookup the WordRepositoryServer.
    private void reconnectWordServer() {
        try {
            wordServer = (WordRepositoryServer) Naming.lookup("rmi://localhost:1099/WordRepositoryServer");
            System.out.println("Reconnected to WordRepositoryServer.");
        } catch (Exception e) {
            System.out.println("Reconnection attempt failed: " + e.getMessage());
        }
    }

    // Helper method to reconnect to the UserAccountServer.
    private void reconnectUserAccountServer() {
        try {
            accountServer = (UserAccountServer) Naming.lookup("rmi://localhost:1099/UserAccountServer");
            System.out.println("Reconnected to UserAccountServer.");
        } catch (Exception e) {
            System.out.println("Reconnection to UserAccountServer failed: " + e.getMessage());
        }
    }

    private void reconnectPuzzleServer() {
        try {
            puzzleServer = (CrissCrossPuzzleServer) Naming.lookup(serverUrl);
            System.out.println("Reconnected to PuzzleServer at " + serverUrl);
        } catch (Exception e) {
            System.out.println("Reconnection to PuzzleServer failed: " + e.getMessage());
            puzzleServer = null; // ensure we don't leave a bad reference
        }
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println(USAGE);
            System.exit(1);
        }
        String serverUrl = args[0];
        String clientname = args[1];
        Client client = new Client(serverUrl, clientname);
        client.run();
    }
}

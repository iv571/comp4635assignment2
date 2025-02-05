

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.util.StringTokenizer;

public class Client {
    private static final String USAGE = "java Client <server_url> <client_name>";
    
    Account account;
    CrissCrossPuzzleServer puzzleServer;
    UserAccountServer accountServer;
    private String serverUrl;
    String clientname;
    String username = " ";
    
    // Define the commands the client supports.
    enum CommandName {
        start,   // start <numberOfWords> <failedAttemptFactor>
        letter,  // letter <character>
        word,    // word <word>
        end,     // end
        restart, // restart
        add,     // add <word>
        remove,  // remove <word>
        check,   // check <word>
        help,    // help
        quit     // quit
    }

    public Client(String serverUrl, String clientName) {
        this.serverUrl = serverUrl;
        this.clientname = clientName;
        
        try {
            // Look up the remote puzzle server object using the provided URL.
            puzzleServer = (CrissCrossPuzzleServer) Naming.lookup(serverUrl);
         // Look up the remote user account server (assumed to be at a fixed URL).
            accountServer = (UserAccountServer) Naming.lookup("rmi://localhost:1099/UserAccountServer");
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
        System.out.println("---------Welcome to the Game Server---------");
        System.out.println("Please create an account or login to continue:");
        System.out.println("Commands:");
        System.out.println("  CREATE <username> <password>");
        System.out.println("  LOGIN <username> <password>");
        System.out.println();
    }

    
    public void run() {
        BufferedReader consoleIn = new BufferedReader(new InputStreamReader(System.in));
        
        boolean authenticated = false;
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
                    boolean created = accountServer.createAccount(username, password);
                    if (created) {
                        System.out.println("Account created successfully. Please log in.");
                    } else {
                        System.out.println("Account already exists. Try logging in.");
                    }
                } else if (command.equals("LOGIN")) {
                    boolean loggedIn = accountServer.loginAccount(username, password);
                    if (loggedIn) {
                        System.out.println("Login successful. Welcome, " + username + "!");
                        clientname = username; // set the clientname after successful login
                        authenticated = true;
                    } else {
                        System.out.println("Login failed. Please check your credentials and try again.");
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

        while (true) {
            System.out.print(clientname + "@" + serverUrl + ">");
            try {
                String userInput = consoleIn.readLine();
                execute(parse(userInput), clientname);
            } catch (RejectedException re) {
                System.out.println(re);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private Command parse(String userInput) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return null;
        }
        
        // Split the input on one or more whitespace characters.
        String[] tokens = userInput.trim().split("\\s+");
        
        // The first token is the command name.
        CommandName commandName;
        try {
            // Convert to uppercase if your enum values are in uppercase;
            // otherwise adjust accordingly.
            commandName = CommandName.valueOf(tokens[0].toLowerCase());
        } catch (IllegalArgumentException e) {
            System.out.println("Illegal command");
            return null;
        }
        
        // Create a Command object. (Assuming your Command constructor takes a CommandName, a string, and a float.)
        Command command = new Command(commandName, "", 0);
        
        // Handle the "start" command specially (it requires two parameters).
        if (commandName == CommandName.start) {
            if (tokens.length < 3) {
                System.out.println("Usage: start <numberOfWords> <failedAttemptFactor>");
                return null;
            }
            command.param1 = tokens[1]; // numberOfWords as a string
            command.param2 = tokens[2]; // failedAttemptFactor as a string
        } else {
            // For other commands, assign the second token if available.
            if (tokens.length >= 2) {
                command.param1 = tokens[1];
            }
            // And assign the third token if available.
            if (tokens.length >= 3) {
                command.param2 = tokens[2];
            }
        }
        return command;
    }

    void execute(Command command, String clientName) throws RemoteException, RejectedException {
        if (command == null) {
            return;
        }
        
        try {
            switch (command.commandName) {
                case start:
                    int numberOfWords = Integer.parseInt(command.param1);
                    int failedAttemptFactor = Integer.parseInt(command.param2);
                    String startResponse = puzzleServer.startGame(username, numberOfWords, failedAttemptFactor);
                    System.out.println(startResponse);
                    break;
                case letter:
                    // For the letter command, use the first character of the parameter.
                    char letter = command.param1.charAt(0);
                    String letterResponse = puzzleServer.guessLetter(clientName, letter);
                    System.out.println(letterResponse);
                    break;
                case word:
                    String wordResponse = puzzleServer.guessWord(clientName, command.param1);
                    System.out.println(wordResponse);
                    break;
                case end:
                    String endResponse = puzzleServer.endGame(clientName);
                    System.out.println(endResponse);
                    break;
                case restart:
                    String restartResponse = puzzleServer.restartGame(clientName);
                    System.out.println(restartResponse);
                    break;
                case add:
                    String addResponse = puzzleServer.addWord(command.param1);
                    System.out.println(addResponse);
                    break;
                case remove:
                    String removeResponse = puzzleServer.removeWord(command.param1);
                    System.out.println(removeResponse);
                    break;
                case check:
                    String checkResponse = puzzleServer.checkWord(command.param1);
                    System.out.println(checkResponse);
                    break;
                case help:
                    printHelp();
                    break;
                case quit:
                    System.out.println("Quitting...");
                    System.exit(0);
                    break;
                default:
                    System.out.println("Unknown command");
                    break;
            }
        } catch (RemoteException re) {
            System.out.println("Remote error: " + re.getMessage());
        } catch (NumberFormatException nfe) {
            System.out.println("Invalid number format: " + nfe.getMessage());
        }

        switch (command.getCommandName()) {
            case quit:
                System.exit(0);
            case help:
                for (CommandName commandName : CommandName.values()) {
                    System.out.println(commandName);
                }
                return;
                        
        }

        // all further commands require a name to be specified
        String userName = command.getUserName();
        if (userName == null) {
            userName = clientname;
        }

        if (userName == null) {
            System.out.println("name is not specified");
            return;
        }

       

    
    }

    private class Command {
        private String userName;
        private float amount;
        private CommandName commandName;
        String param1;
        String param2;

        private String getUserName() {
            return userName;
        }

        private float getAmount() {
            return amount;
        }

        private CommandName getCommandName() {
            return commandName;
        }

        private Command(Client.CommandName commandName, String userName, float amount) {
            this.commandName = commandName;
            this.userName = userName;
            this.amount = amount;
        }
    }
    
    /**
     * Prints a list of available commands.
     */
    private void printHelp() {
    	String border = "+--------------------------------------------------------------------+";
        System.out.println(border);
        System.out.println("|                   CRISS CROSS PUZZLE                               |");
        System.out.println(border);
        System.out.println("| Commands:                                                          |");
        System.out.println("|                                                                    |");
        System.out.println("|   start <numberOfWords> <failedAttemptFactor>  - Start a new game  |");
        System.out.println("|   letter <character>                         - Guess a letter      |");
        System.out.println("|   word <word>                                - Guess a word        |");
        System.out.println("|   end                                        - End the current game|");
        System.out.println("|   restart                                    - Restart the game    |");
        System.out.println("|   add <word>                                 - Add a new word      |");
        System.out.println("|   remove <word>                              - Remove a word       |");
        System.out.println("|   check <word>                               - Check word existence|");
        System.out.println("|   help                                       - Display this help   |");
        System.out.println("|   quit                                       - Exit the client     |");
        System.out.println(border);
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

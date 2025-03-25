
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.util.StringTokenizer;
import java.util.regex.*;

/**
 * Client class for the CrissCross Puzzle game.
 * <p>
 * This client connects to various remote servers using Java RMI: a puzzle server,
 * a user account server, and a word repository server. It supports both single-player
 * and multi-player modes. Commands are provided via a console interface.
 * </p>
 *
 * Usage: java Client rmi://localhost:1099/GameServer YourClientName
 */
public class Client {
	private static final String USAGE = "java Client rmi://localhost:1099/GameServer YourClientName";

	// Heartbeat interval in milliseconds (set to 5 seconds)
	private static final long HEARTBEAT_INTERVAL = 5000;
	// Example toleranceMillis (should match server's configuration)
	private static final long TOLERANCE_MILLIS = 10000; // For reference, adjust as needed

	// [At-most-once] Sequence number for deduplication of requests
	private int sequenceNumber = 0;

	CrissCrossPuzzleServer puzzleServer;
	UserAccountServer accountServer;
	WordRepositoryServer wordServer;
	private String serverUrl;
	private ClientCallback clientCallback;
	String clientname;
	String username = " ";
	int activeGameID = -1;

	// Thread to handle heartbeat messages
	private Thread heartbeatThread = null;

	 /**
     * Enum defining the set of commands supported by the client.
     */
	enum CommandName {
		start, // start <numberOfWords> <failedAttemptFactor>
		letter, // letter <character>
		word, // word <word>
		end, // end
		restart, // restart
		add, // add <word>
		remove, // remove <word>
		check, // check <word>
		score, // check score
		scoreboard, // view scoreboard
		help, // help
		startmultiplayer, // start multiplayer game
		joinmultiplayer, // join the multiplayer game
		startgameroom, // start the game room (Only host can start)
		showactivegames, // show existing game room
		multiscoreboard, // view multiplayer scoreboard
		ready, // ready for the game room
		leave, // leave the game room
		rungame, // run the game
		quit, // quit
		pause, // pause heartbeat
		resume, // resume heartbeat
	}

	 /**
     * Constructor that initializes the client by looking up remote server objects.
     *
     * @param serverUrl  URL of the puzzle server.
     * @param clientName The name of the client.
     */
	public Client(String serverUrl, String clientName) {
		this.serverUrl = serverUrl;
		this.clientname = clientName;

		try {
			// Look up the remote puzzle server object using the provided URL.
			puzzleServer = (CrissCrossPuzzleServer) Naming.lookup(serverUrl);
			clientCallback = new ClientImpl();
			// Look up the remote user account server (assumed to be at a fixed URL).
			accountServer = (UserAccountServer) Naming.lookup("rmi://localhost:1099/UserAccountServer");

			// Look up the remote user account server (assumed to be at a fixed URL).
			wordServer = (WordRepositoryServer) Naming.lookup("rmi://localhost:1099/WordRepositoryServer");
		} catch (Exception e) {
			System.out.println("The runtime failed: " + e.getMessage());
			System.exit(0);
		}
		System.out.println("Connected to puzzle server: " + serverUrl);
	}

	  /**
     * Inner class that implements a Runnable to send periodic heartbeat messages to the server.
     * This helps in detecting and maintaining connectivity.
     */
	private class HeartbeatTask implements Runnable {
		@Override
		public void run() {
			while (true) {
				try {
					Thread.sleep(HEARTBEAT_INTERVAL);
					// Send a heartbeat to the server.
					puzzleServer.heartbeat(clientname);
				} catch (InterruptedException ie) {
					System.out.println("Heartbeat thread interrupted.");
					break;
				} catch (RemoteException re) {
					System.err.println("Heartbeat error: " + re.getMessage());
				}
			}
		}
	}

	/**
	 * Displays the authentication menu.
	 */
	private void displayAuthenticationMenu() {
		System.out.println("---------Welcome to the Game Server---------");
		System.out.println("Please create an account or login to continue:");
		System.out.println("Commands:");
		System.out.println("  create <username> <password>");
		System.out.println("  login <username> <password>");
		System.out.println();
	}
	
	 /**
     * Main client loop.
     * <p>
     * This method handles authentication first and then continuously reads commands
     * from the console to execute game operations.
     * </p>
     */
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
							System.out.println(
									"Connection to the user account server was lost. Attempting to reconnect...");
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
									System.out.println(
											"The user account server is currently unavailable. Please try again later.");
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
								sequenceNumber = 1; // default to 1 if invalid input
							}
							System.out.println("Initial sequence number set to " + sequenceNumber);
							authenticated = true;
						} else {
							System.out.println("Login failed. Please check your credentials and try again.");
						}
					} catch (RemoteException re) {
						if (re.getMessage().contains("Connection refused")) {
							System.out.println(
									"Connection to the user account server was lost. Attempting to reconnect...");
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
									System.out.println(
											"The user account server is currently unavailable. Please try again later.");
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

		// Main command loop for handling game commands.
		while (true) {
			System.out.print(clientname + "@" + serverUrl + ">");
			System.out.println("OUTER\n");
			try {
				if (!puzzleServer.isValidRoomID(activeGameID)) {
					activeGameID = -1;
				}
				if (activeGameID != -1 && puzzleServer.isGameRun(activeGameID)
						&& puzzleServer.isActiveRoom(activeGameID)) {
					System.out.println("WAITING FOR THE HOST TO START THE GAME - HANG ON.....\n");
				} else {
					// System.out.println("NORMAL MODE\nExecutreCommand\n");
					String userInput = consoleIn.readLine();
					execute(parse(userInput), clientname);
				}
			} catch (RejectedException re) {
				System.out.println(re);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	 /**
     * Parses the user input into a Command object.
     *
     * @param userInput the raw input from the user
     * @return a Command object representing the parsed command, or null if input is invalid.
     */
	private Command parse(String userInput) {
		if (userInput == null || userInput.trim().isEmpty()) {
			return null;
		}

		String trimmed = userInput.trim();

		// Check for shortcut characters.
		if (trimmed.equals("$")) {
			// Shortcut for checking score.
			return new Command(CommandName.score, "", 0);
		} else if (trimmed.equals("!")) {
			// Shortcut for starting a new game (restart).
			return new Command(CommandName.restart, "", 0);
		} else if (trimmed.equals("#")) {
			// Shortcut for ending the current game.
			return new Command(CommandName.end, "", 0);
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

		// Create a Command object. (Assuming your Command constructor takes a
		// CommandName, a string, and a float.)
		Command command = new Command(commandName, "", 0);

		// Handle the "start" command specially (it requires two parameters).
		if (commandName == CommandName.start) {
			if (tokens.length < 3) {
				System.out.println("Usage: start <numberOfWords> <failedAttemptFactor>");
				return null;
			}
			command.param1 = tokens[1]; // numberOfWords as a string
			command.param2 = tokens[2]; // failedAttemptFactor as a string
		} // For commands that require one argument: letter, word, add, remove, check.
		else if (commandName == CommandName.letter || commandName == CommandName.word || commandName == CommandName.add
				|| commandName == CommandName.remove || commandName == CommandName.check) {
			if (tokens.length < 2) {
				System.out.println("Command " + commandName + " requires an argument.");
				return null;
			}
			command.param1 = tokens[1];
			if (tokens.length >= 3) {
				command.param2 = tokens[2];
			}
		}

		else {
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

	 /**
     * Executes a given command by calling the corresponding remote method.
     *
     * @param command    the parsed Command object.
     * @param clientName the client's name.
     * @throws RemoteException   if a remote communication error occurs.
     * @throws RejectedException if the command is rejected.
     */
	void execute(Command command, String clientName) throws RemoteException, RejectedException {
		if (command == null) {
			return;
		}

		try {
			switch (command.commandName) {
			case start:
				int numberOfWords = Integer.parseInt(command.param1);
				int failedAttemptFactor = Integer.parseInt(command.param2);
				String startResponse = puzzleServer.startGame(username, numberOfWords, failedAttemptFactor,
						sequenceNumber);

				// [At-most-once] Simulate duplicate invocation
				if (Math.random() < 0.5) {

					try {
						String dupResponse = puzzleServer.startGame(username, numberOfWords, failedAttemptFactor,
								sequenceNumber);
					} catch (RemoteException reDup) {

					}
				}
				sequenceNumber++; // increment only after successful call

				// Start the heartbeat thread only when the game starts.
				if (heartbeatThread == null || !heartbeatThread.isAlive()) {
					heartbeatThread = new Thread(new HeartbeatTask());
					heartbeatThread.start();
				}
				System.out.println(startResponse);
				break;
			case letter:
                // For the letter command, use the first character of the parameter.
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
			case word:
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
			case end:
                String endResponse = puzzleServer.endGame(clientName, sequenceNumber);
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
                // Stop the heartbeat thread when the game ends.
                if (heartbeatThread != null) {
                    heartbeatThread.interrupt();
                    heartbeatThread = null;
                }
                break;
			 case restart:
                 String restartResponse = puzzleServer.restartGame(clientName, sequenceNumber);
              // Start the heartbeat thread only when the game starts.
                 if (heartbeatThread == null || !heartbeatThread.isAlive()) {
                     heartbeatThread = new Thread(new HeartbeatTask());
                     heartbeatThread.start();
                 }
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
			 case add:
                 try {
                     boolean addSuccess = puzzleServer.addWord(username, command.param1, sequenceNumber);
                     System.out.println(addSuccess
                             ? "Word added successfully."
                             : "Failed to add word.");
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
                     // Check if the error message indicates the PuzzleServer is offline
                     if (re.getMessage() != null && re.getMessage().contains("Connection refused")) {
                         System.out.println("Connection to PuzzleServer refused. Attempting to reconnect...");
                         reconnectPuzzleServer();
                         // After reconnect attempt, retry once
                         if (puzzleServer != null) {
                             try {
                                 boolean addSuccess = puzzleServer.addWord(username, command.param1, sequenceNumber);
                                 System.out.println(addSuccess
                                         ? "Word added successfully."
                                         : "Failed to add word.");
                             } catch (RemoteException re2) {
                                 System.out.println("Remote error after reconnection attempt: " + re2.getMessage());
                             }
                         }
                     } else {
                         // Different remote error
                         System.out.println("Remote error: " + re.getMessage());
                     }
                 }
                 break;


             case remove:
                 try {
                     boolean removeSuccess = puzzleServer.removeWord(username, command.param1, sequenceNumber);
                     System.out.println(removeSuccess
                             ? "Word removed successfully."
                             : "Failed to remove word.");
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
                                 System.out.println(removeSuccess
                                         ? "Word removed successfully."
                                         : "Failed to remove word.");
                             } catch (RemoteException re2) {
                                 System.out.println("Remote error after reconnection attempt: " + re2.getMessage());
                             }
                         }
                     } else {
                         System.out.println("Remote error: " + re.getMessage());
                     }
                 }
                 break;

             case check:
                 try {
                     boolean exists = puzzleServer.checkWord(username, command.param1, sequenceNumber);
                     System.out.println(exists
                             ? "Word exists in the repository."
                             : "Word does not exist in the repository.");
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
                                 System.out.println(exists
                                         ? "Word exists in the repository."
                                         : "Word does not exist in the repository.");
                             } catch (RemoteException re2) {
                                 System.out.println("Remote error after reconnection attempt: " + re2.getMessage());
                             }
                         }
                     } else {
                         System.out.println("Remote error: " + re.getMessage());
                     }
                 }
                 break;

			case startmultiplayer:
				// Expected usage: startmultiplayer <numPlayers> <level>
				if (command.param1 == null || command.param2 == null) {
					System.out.println("Usage: startmultiplayer <numberOfPlayers> <level>");
					break;
				}
				
				int numPlayers = Integer.parseInt(command.param1);
				int level = Integer.parseInt(command.param2);
				// Call remote method to start a multi-player game.
				String startMPResponse = puzzleServer.startMultiGame(username, numPlayers, level);
				System.out.println(startMPResponse);
				break;

			case joinmultiplayer:
				// Expected usage: startmultiplayer <numPlayers> <level>
				if (command.param1 == null) {
					System.out.println("Usage: joinmultiplayer <gameId>");
					break;
				}

				if (heartbeatThread == null || !heartbeatThread.isAlive()) {
                    heartbeatThread = new Thread(new HeartbeatTask());
                    heartbeatThread.start();
                }
				int gameId = Integer.parseInt(command.param1);
				// Call remote method to start a multi-player game.
				String joinMPResponse = puzzleServer.joinMultiGame(username, gameId, clientCallback);
				System.out.println(joinMPResponse);
				break;

			case startgameroom:
				if (command.param1 == null) {
					System.out.println("Usage: joinmultiplayer <gameId>");
					break;
				}
				int roomId = Integer.parseInt(command.param1);
				String gameMPResponse = puzzleServer.startGameRoom(username, roomId);
				System.out.println(gameMPResponse);
				if (gameMPResponse.equals("You have successfully started the game room.\n")) {
					activeGameID = roomId;
					// System.out
					// .println("Active room: " + roomId + " isStarted: " +
					// puzzleServer.isActiveRoom(roomId));
				}
				break;
			case rungame:
				if (command.param1 == null) {
					System.out.println("Usage: rungame <gameId>");
					break;
				}
				roomId = Integer.parseInt(command.param1);
				if (activeGameID != -1 && roomId == activeGameID && puzzleServer.isActiveRoom(activeGameID)) {
					String runMPResponse = puzzleServer.runGame(username, activeGameID, wordServer);
					System.out.println(runMPResponse);
					if (!runMPResponse.equals("No winner. Game ended with no active players.\\n")
							|| !runMPResponse.equals("Only the host can run the game!\\n" + //
									"")) {
						runMPResponse = runMPResponse.trim();
						Pattern pattern = Pattern.compile("WINNER: (.+?) - Total Scores: (\\d+)");
						Matcher matcher = pattern.matcher(runMPResponse);
						if (matcher.find()) {
							String winnerName = matcher.group(1); // Extract name
							int score = Integer.parseInt(matcher.group(2)); // Extract score as integer
							accountServer.updateScore(winnerName, score, true);
							// System.out.println("Winner: " + winnerName);
							// System.out.println("Score: " + score);
						} else {
							System.out.println("No match found.");
						}
					}
				} else {
					System.out.println("This is not active game id");
				}
				break;

			case ready:
				if (command.param1 == null) {
					System.out.println("Usage: ready <gameId>");
					break;
				}
				roomId = Integer.parseInt(command.param1);
				String readyMPResponse = puzzleServer.setActivePlayer(username, roomId);
				System.out.println(readyMPResponse);
				if (readyMPResponse.equals("You have been marked as ready.\n")) {
					activeGameID = roomId;
					// System.out.println("Hello " + activeGameID);
					// System.out
					// .println("Active room: " + roomId + " isStarted: " +
					// puzzleServer.isActiveRoom(roomId));
				}
				break;

			case leave:
				if (command.param1 == null) {
					System.out.println("Usage: leave <gameId>");
					break;
				}
				roomId = Integer.parseInt(command.param1);
				String leaveMPResponse = puzzleServer.leaveRoom(username, roomId);
				System.out.println(leaveMPResponse);
				break;

			case showactivegames:
				String activeRooms = puzzleServer.showActiveGameRooms();
				System.out.println(activeRooms);
				break;

			case help:
				printHelp();
				break;
			case score:
				// Get the current user's score.
				int score = accountServer.getScore(username);
				System.out.println("Your current score is: " + score);
				break;
			case scoreboard:
				try {
					// Retrieve the scoreboard map from the account server.
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
			case multiscoreboard:
				try {
					// Retrieve the scoreboard map from the account server.
					java.util.Map<String, Integer> scoreboard = accountServer.getScoreboard(true);
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
			case pause:
				if (heartbeatThread != null) {
					heartbeatThread.interrupt();
					heartbeatThread = null;
				}
				break;
			case resume:
				// Immediately send a heartbeat upon resuming
				puzzleServer.heartbeat(clientname);
				if (heartbeatThread == null || !heartbeatThread.isAlive()) {
					heartbeatThread = new Thread(new HeartbeatTask());
					heartbeatThread.start();
					System.out.println("Heartbeat resumed.");
				}
				break;
			case quit:
				System.out.println("Quitting...");
				// Stop the heartbeat thread if it's running
				if (heartbeatThread != null) {
					heartbeatThread.interrupt();
					heartbeatThread = null;
				}
				System.exit(0);
				break;
			default:
				System.out.println("Unknown command");
				break;
			}
		} catch (

		RemoteException re) {
			System.out.println("Remote error: " + re.getMessage());
		} catch (NumberFormatException nfe) {
			System.out.println("Invalid number format: " + nfe.getMessage());
		}

		switch (command.getCommandName()) {
		case quit:
			System.exit(0);

			return;
		default:
			break;

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

	/**
     * Inner class representing a parsed command.
     */
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

	    /**
         * Constructs a new Command object.
         *
         * @param commandName the type of command
         * @param userName    the username associated with the command
         * @param amount      an amount (if applicable)
         */
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
		String border = "+-----------------------------------------------------------------------------+";
		System.out.println(border);
		System.out.println("|                          CRISS CROSS PUZZLE                                 |");
		System.out.println(border);
		System.out.println("|                                                                             |");
		System.out.println("|                         MULTI-PLAYER MODE                                   |");
		System.out.println("|                                                                             |");
		System.out.println("|   startmultiplayer <numPlayers> <level>        - Start a multi-player game  |");
		System.out.println("|   joinmultiplayer <gameId>                     - Join a multi-player game   |");
		System.out.println("|   startgameroom <gameId>                       - Start the game room        |");
		System.out.println("|   rungame <gameId>                             - run the game room          |");
		System.out.println("|   ready <gameId>                               - Ready for the game         |");
		System.out.println("|   leave  <gameId>                              - Leave the game room        |");
		System.out.println("|   showactivegames                              - Show all active game rooms |");
		System.out.println("|   multiscoreboard                              - Show the score board       |");
		System.out.println("|                                                                             |");
		System.out.println(border);
		System.out.println("|                                                                             |");
		System.out.println("|                         SINGLE-PLAYER MODE                                  |");
		System.out.println("|                                                                             |");
		System.out.println("|   start <numberOfWords> <failedAttemptFactor>  - Start a new game           |");
		System.out.println("|   letter <character>                           - Guess a letter             |");
		System.out.println("|   word <word>                                  - Guess a word               |");
		System.out.println("|   end                                          - End the current game       |");
		System.out.println("|   restart                                      - Restart the game           |");
		System.out.println("|   add <word>                                   - Add a new word             |");
		System.out.println("|   remove <word>                                - Remove a word              |");
		System.out.println("|   check <word>                                 - Check word existence       |");
		System.out.println("|   score                                        - Get your user score        |");
		System.out.println("|   scoreboard                                   - Get the scoreboard         |");
		System.out.println("|   help                                         - Display this help          |");
		System.out.println("|   quit                                         - Exit the client            |");
		System.out.println("|                                                                             |");
		System.out.println(border);
	}

	 /**
     * Helper method to reconnect to the UserAccountServer.
     */
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

    /**
     * Helper method to reconnect to the PuzzleServer.
     */
	private void reconnectPuzzleServer() {
		try {
			puzzleServer = (CrissCrossPuzzleServer) Naming.lookup(serverUrl);
			System.out.println("Reconnected to PuzzleServer at " + serverUrl);
		} catch (Exception e) {
			System.out.println("Reconnection to PuzzleServer failed: " + e.getMessage());
			puzzleServer = null; // ensure we don't leave a bad reference
		}
	}

	 /**
     * The entry point of the client application.
     *
     * @param args Command line arguments; expects exactly 2 arguments.
     */
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

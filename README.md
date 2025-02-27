# **COMP-4635 Assignment 2**

Welcome to the COMP-4635 Assignment 2 repository. This document outlines the game instructions, including both manual and scripted server startups, as well as additional commands to interact with the game.

---

## **Table of Contents**

- [Game Instructions](#game-instructions)
  - [Option A: Manual Start](#option-a-manual-start)
  - [Option B: Orchestration Script](#option-b-orchestration-script)
- [Multiplayer Games](#multiplayer-games)
- [Additional Commands](#additional-commands)
- [Shortcuts (After Starting Criss Cross Puzzle)](#shortcuts-after-starting-criss-cross-puzzle)

---

## **Game Instructions**

### **Option A: Manual Start**

1. **Start the Game Server**  
   *Command:*  
   ```bash
   java GameServer test
   ```

2. **Start the Word Server**  
   *Command:*  
   ```bash
   java WordRepositoryImpl
   ```

3. **Start the Account Server**  
   *Command:*  
   ```bash
   java UserAccountImpl
   ```

4. **Start the Client**  
   *Command:*  
   ```bash
   java Client rmi://localhost:1099/GameServer test
   ```

5. **Create an Account**  
   *Command:*  
   ```bash
   create [username] [password]
   ```

6. **Login to the Account**  
   *Command:*  
   ```bash
   login [username] [password]
   ```

7. **Start the Game**  
   *Command:*  
   ```bash
   start [level] [failed attempts factor]
   ```

---

### **Option B: Orchestration Script**

1. **Run the Powershell Orchestration Script**  
   *Command:*  
   ```powershell
   .\startServers.ps1
   ```

2. **Start the Client**  
   *Command:*  
   ```bash
   java Client rmi://localhost:1099/GameServer test
   ```

---

## **Multiplayer Games**

Steps to start a multiplayer game:

1. **Host:**  
   ```bash
   startmultiplayer <numPlayers> <level>        - Start a multi-player game
   ```

2. **Players:**  
   ```bash
   joinmultiplayer <gameId>                     - Join a multi-player game
   ```

3. **Host:**  
   ```bash
   startgame <gameId>                           - Start the game room
   ```

4. **Players:**  
   ```bash
   ready <gameId>                               - Ready for the game
   ```

5. **Host:**  
   ```bash
   rungame <gameId>                             - Run the game room
   ```

6. **Anyone:**  
   ```bash
   leave <gameId>                               - Leave the game room
   ```

7. **Anyone:**  
   ```bash
   showactivegames                              - Show all active game rooms
   ```

8. **Anyone:**  
   ```bash
   multiplayerscoreboard
   ```

---

## **Additional Commands**

- **Add a Word**  
  *Command:*  
  ```bash
  add [word]
  ```

- **Remove a Word**  
  *Command:*  
  ```bash
  remove [word]
  ```

- **Check a Word**  
  *Command:*  
  ```bash
  check [word]
  ```

- **Check Score**  
  *Command:*  
  ```bash
  check score
  ```

- **Check Scoreboard**  
  *Command:*  
  ```bash
  scoreboard
  ```

- **Quit the Game**  
  *Command:*  
  ```bash
  quit
  ```

---


## **Shortcuts (After Starting Criss Cross Puzzle - Single Player)**

- **`$`** : Display Score
- **`!`** : Start New Game
- **`#`** : End the Game

---

This update integrates the multiplayer game commands in a dedicated section while preserving the original formatting and structure of the README.

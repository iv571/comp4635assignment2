# **COMP-4635 Assignment 2**

Welcome to the COMP-4635 Assignment 2 repository. This document outlines the game instructions, including both manual and scripted server startups, as well as additional commands to interact with the game.

---

## **Table of Contents**

- [Game Instructions](#game-instructions)
  - [Option A: Manual Start](#option-a-manual-start)
  - [Option B: Orchestration Script](#option-b-orchestration-script)
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

- **Start a Multiplayer Game**  
  *Command:*  
  ```bash
  startmultiplayer [numberOfPlayers] [level]
  ```

- **Join a Multiplayer Game**  
  *Command:*  
  ```bash
  joinmultiplayer [gameId]
  ```

- **Quit the Game**  
  *Command:*  
  ```bash
  quit
  ```

---

## **Shortcuts (After Starting Criss Cross Puzzle)**

- **`$`** : Display Score
- **`!`** : Start New Game
- **`#`** : End the Game

---

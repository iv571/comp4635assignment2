#!/bin/bash
# Orchestration script for starting the microservices in order using Bash
# USAGE: ./startServers.sh

# --- Start the User Account Server ---
echo "Starting User Account Server..."
java UserAccountImpl &
accountServer=$!
echo "User Account Server started with PID $accountServer."

# Wait a few seconds to allow the server to initialize.
sleep 5

# --- Start the Word Repository Server ---
echo "Starting Word Repository Server..."
java WordRepositoryImpl &
wordRepository=$!
echo "Word Repository Server started with PID $wordRepository."

# Wait again to ensure the repository server is up.
sleep 5

# --- Start the Game Server ---
# Define the RMI URL for the Game Server.
RMI_URL="rmi://localhost:1099/UserAccountServer"
echo "Starting Game Server with RMI URL: $RMI_URL..."
java GameServer "$RMI_URL" &
gameServer=$!
echo "Game Server started with PID $gameServer."

# Optionally wait for the Game Server process to exit before ending the script:
# wait $gameServer
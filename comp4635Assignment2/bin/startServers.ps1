# Orchestration script for starting the microservices in order using PowerShell
# USAGE
#Set-ExecutionPolicy RemoteSigned
#.\start_servers.ps1

# --- Start the User Account Server ---
Write-Host "Starting User Account Server..."
# Start the UserAccountImpl process in the background.
$accountServer = Start-Process -FilePath "java" -ArgumentList "UserAccountImpl" -PassThru
Write-Host "User Account Server started with PID $($accountServer.Id)."

# Wait a few seconds to allow the server to initialize.
Start-Sleep -Seconds 5

# --- Start the Word Repository Server ---
Write-Host "Starting Word Repository Server..."
$wordRepository = Start-Process -FilePath "java" -ArgumentList "WordRepositoryImpl" -PassThru
Write-Host "Word Repository Server started with PID $($wordRepository.Id)."

# Wait again to ensure the repository server is up.
Start-Sleep -Seconds 5

# --- Start the Game Server ---
# Define the RMI URL for the Game Server.
$RMI_URL = "rmi://localhost:1099/UserAccountServer"
Write-Host "Starting Game Server with RMI URL: $RMI_URL..."
$gameServer = Start-Process -FilePath "java" -ArgumentList "GameServer", $RMI_URL -PassThru
Write-Host "Game Server started with PID $($gameServer.Id)."

# Optionally, wait for the Game Server process to exit before ending the script.
# Wait-Process -Id $gameServer.Id



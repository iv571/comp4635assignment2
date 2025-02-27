/**
 * Represents a player in the game.
 * <p>
 * Each player has a name, a score, and tracks their current failed attempts.
 * The total number of failed attempts allowed is fixed.
 * </p>
 */

public class Player {
        private String name;
        private int currentFailedAttempts;
        private static final int TOTAL_FAILED_ATTEMPTS = 5;
        private int score;

        /**
         * Constructs a new Player with the given name.
         *
         * @param name the name of the player.
         */
        public Player(String name) {
            this.name = name;
            this.currentFailedAttempts = 0;
            this.score = 0;
        }

        /**
         * Retrieves the player's name.
         *
         * @return the name of the player.
         */
        public String getName() {
            return this.name;
        }
        
        /**
         * Retrieves the player's current score.
         *
         * @return the player's score.
         */
        public int getScore() {
            return score;
        }
        
        /**
         * Increases the player's score by one.
         */
        public void increaseScore() {
            score++;
        }
    }
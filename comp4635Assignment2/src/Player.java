public class Player {
        private String name;
        private int currentFailedAttempts;
        private static final int TOTAL_FAILED_ATTEMPTS = 5;
        private int score;

        public Player(String name) {
            this.name = name;
            this.currentFailedAttempts = 0;
            this.score = 0;
        }

        public String getName() {
            return this.name;
        }
        
        // Getter for score
        public int getScore() {
            return score;
        }
        
        public void increaseScore() {
            score++;
        }
    }
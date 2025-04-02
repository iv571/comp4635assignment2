import java.util.*;
public class test {
    public static void main(String[] args) throws InterruptedException {
        // Create nodes
        LamportClock node0 = new LamportClock(0);
        LamportClock node1 = new LamportClock(1);
        LamportClock node2 = new LamportClock(2);

        // Set up peers
        List<LamportClock> allNodes = Arrays.asList(node0, node1, node2);
        node0.setPeers(allNodes);
        node1.setPeers(allNodes);
        node2.setPeers(allNodes);

        // Simulate broadcasting messages
        System.out.println("=== Starting message broadcasts ===");
        node0.send("Hello from Node 0");
        Thread.sleep(100);  // Simulate network delay

        node1.send("Greetings from Node 1");
        Thread.sleep(100);  // Simulate network delay

        node2.send("Hi from Node 2");

        // Wait for all messages to be processed
        Thread.sleep(500);
        System.out.println("=== Simulation complete ===");
    }
}

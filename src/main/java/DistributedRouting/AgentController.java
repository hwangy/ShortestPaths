package DistributedRouting;

import DistributedRouting.objects.Graph;
import DistributedRouting.objects.SampleGraphs;
import DistributedRouting.util.Logging;

import java.util.concurrent.CountDownLatch;

public class AgentController {

    public static void main(String[] args) {
        Graph graph = SampleGraphs.simpleGraph;

        // We'll wait till all threads terminate
        CountDownLatch countdown = new CountDownLatch(graph.getVertices().size());

        // Initialize Threads for each vertex
        for (Integer vertex : graph.getVertices()) {
            Thread agent = new Thread(new AgentRunner(vertex, graph.neighborsOf(vertex), countdown));
            agent.start();
        }

        try {
            countdown.await();
        } catch (InterruptedException ex) {
            Logging.logError("Failed to wait on threads!");
            ex.printStackTrace();
        }
        Logging.logService("All threads finished.");
    }
}

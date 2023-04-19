package DistributedRouting;

import DistributedRouting.grpc.LogGrpc;
import DistributedRouting.grpc.MessageLog;
import DistributedRouting.grpc.StatusReply;
import DistributedRouting.objects.RawGraph;
import DistributedRouting.objects.SampleGraphs;
import DistributedRouting.util.Constants;
import DistributedRouting.util.GraphUtil;
import DistributedRouting.util.Logging;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.stub.StreamObserver;

import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.graphstream.graph.Graph;
import org.graphstream.graph.implementations.SingleGraph;

public class AgentController {

    private static final Lock graphLock = new ReentrantLock();

    public static Server initializeListener(Graph graphVis) throws Exception {
        Server server = Grpc.newServerBuilderForPort(Constants.MESSAGE_PORT, InsecureServerCredentials.create())
                .addService(new AgentLoggerImpl(graphVis))
                .build()
                .start();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                interrupt();
            }
        });
        return server;
    }

    /**
     * Draws the graph using the GraphStream framework.
     * @param rawGraph  The raw graph consisting of edges and vertices.
     * @return  returns the GraphStream graph
     */
    public static Graph drawGraph(RawGraph rawGraph) {
        Graph graph = new SingleGraph("Graph");
        for (Integer vertex : rawGraph.getVertices()) {
            graph.addNode(vertex.toString());
        }
        graph.getNode(String.valueOf(1)).setAttribute("ui.class", "terminal");
        for (Map.Entry<Integer, Set<Integer>> entry : rawGraph.getEdges().entrySet()) {
            String s = entry.getKey().toString();
            for (Integer dest : entry.getValue()) {
                String d = dest.toString();
                String label = GraphUtil.edgeLabel(s,d);
                Logging.logDebug(label);
                graph.addEdge(label, s, d);
            }
        }
        graph.display();
        return graph;
    }

    // Some nice seeds:
    // n = 20 782097489244492214
    // n = 20 5924385651977311760
    // n = 40 5843648202025435093

    public static void main(String[] args) {
        // Ask user for a preset seed, or generate a new one
        Scanner inputReader = new Scanner(System.in);
        System.out.println("Seed?");
        String seed = inputReader.nextLine();
        Random random;
        if (seed.isEmpty()) {
            random = new Random();
            Long currSeed = random.nextLong();
            random.setSeed(currSeed);
            System.out.println("Using seed: " + currSeed);
        } else {
            random = new Random(Long.valueOf(seed));
        }

        RawGraph graph = SampleGraphs.erdosReyniGraph(20,0.15f, random);
        Graph graphVis = drawGraph(graph);
        graphVis.setAttribute("ui.stylesheet", """
                edge {
                    size: 2px;
                    fill-mode: dyn-plain;
                    fill-color: black, green;
                }
                
                node.terminal {
                    fill-color: blue;
                }""");

        try {
            initializeListener(graphVis);
        } catch (Exception ex) {
            Logging.logError("Failed to start logging service");
            ex.printStackTrace();
        }

        // We'll wait till all threads terminate
        CountDownLatch countdown = new CountDownLatch(graph.getVertices().size());

        // Initialize Threads for each vertex
        graph = graph.asUndirectedGraph();
        int lambda = 1;
        for (Integer vertex : graph.getVertices()) {
            Thread agent = new Thread(new AgentRunner(vertex, graph.neighborsOf(vertex), countdown, lambda));
            agent.start();
        }

        try {
            countdown.await();
        } catch (InterruptedException ex) {
            Logging.logError("Failed to wait on threads!");
            ex.printStackTrace();
        }
    }

    static class AgentLoggerImpl extends LogGrpc.LogImplBase {
        private Graph graphVis;
        public AgentLoggerImpl(Graph graphVis) {
            this.graphVis = graphVis;
        }

        @Override
        public void sendLog(MessageLog req, StreamObserver<StatusReply> responseObserver) {
            String label = GraphUtil.edgeLabel(req.getSendingNode(), req.getReceivingNode());
            try {
                graphLock.tryLock(500, TimeUnit.MILLISECONDS);
                graphVis.getEdge(label).setAttribute("ui.color", 1);
            } catch (InterruptedException ex) {
                Logging.logError("Failed to acquire lock for graph update: " + ex.getMessage());
            } finally {
                graphLock.unlock();
            }
            responseObserver.onNext(StatusReply.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        }
    }
}

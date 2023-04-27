package DistributedRouting;

import DistributedRouting.grpc.*;
import DistributedRouting.objects.RawGraph;
import DistributedRouting.objects.SampleGraphs;
import DistributedRouting.util.Constants;
import DistributedRouting.util.GraphUtil;
import DistributedRouting.util.Logging;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.stub.StreamObserver;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.ui.spriteManager.SpriteManager;

public class AgentController {

    private static final Lock graphLock = new ReentrantLock();
    private static HashMap<Integer, Graph> graphMap = new HashMap<>();
    private static SpriteManager manager;

    private static int terminal = 1;

    /**
     * Initializes the listener, which listens for messages from the other machines (nodes)
     */
    public static Server initializeListener() throws Exception {
        Server server = Grpc.newServerBuilderForPort(Constants.MESSAGE_PORT, InsecureServerCredentials.create())
                .addService(new AgentLoggerImpl())
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
            Node node = graph.addNode(vertex.toString());
            node.addAttribute("ui.label", vertex.toString());
        }
        graph.getNode(String.valueOf(1)).setAttribute("ui.class", "terminal");
        for (Map.Entry<Integer, Set<Integer>> entry : rawGraph.getEdges().entrySet()) {
            String s = entry.getKey().toString();
            for (Integer dest : entry.getValue()) {
                String d = dest.toString();
                String label = GraphUtil.edgeLabel(s,d);
                graph.addEdge(label, s, d);
            }
        }
        graph.display();
        return graph;
    }

    // Some nice seeds:
    // n = 5 (p=0.5) 3632690309280280925
    // n = 20 (p=0.15) 782097489244492214
    // n = 20 (p=0.15) 5924385651977311760
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

        int numVertices = 40;
        RawGraph graph = SampleGraphs.erdosReyniGraph(numVertices,0.15f, random);
        for (int i = 1; i <= 1; i++) {
            Graph graphVis = drawGraph(graph);
            graphVis.setAttribute("ui.stylesheet", """
                edge {
                    size: 2px;
                    fill-mode: dyn-plain;
                    fill-color: black, green;
                }
                
                node.terminal {
                    fill-color: blue;
                }
                
                node.distribute {
                    fill-color: green;
                }
                
                node.bfs {
                    fill-color: orange;
                }
                
                node.bfsComplete {
                    fill-color: orangered;
                }
                
                node.wait {
                    fill-color: lavender;
                }
                
                node.sample {
                    fill-color: purple;
                }
                
                node.sync {
                    fill-color: black;
                }
                """);
            graphMap.put(i, graphVis);
        }

        try {
            initializeListener();
        } catch (Exception ex) {
            Logging.logError("Failed to start logging service");
            ex.printStackTrace();
        }

        // We'll wait till all threads terminate
        CountDownLatch countdown = new CountDownLatch(graph.getVertices().size());

        // Initialize Threads for each vertex
        graph = graph.asUndirectedGraph();
        int lambda = 5;
        int totalLength = 15;
        for (Integer vertex : graph.getVertices()) {
            Thread agent = new Thread(new AgentRunner(
                    vertex, graph.neighborsOf(vertex), countdown, lambda, totalLength));
            agent.start();
        }

        try {
            countdown.await();
        } catch (InterruptedException ex) {
            Logging.logError("Failed to wait on threads!");
            ex.printStackTrace();
        }
    }

    /**
     * AgentLoggerImpl implements the functions related to logging the progress of the components of the distributed random walk algorithm.
     */
    static class AgentLoggerImpl extends LogGrpc.LogImplBase {
        public AgentLoggerImpl() {
        }

        @Override
        public void sendNodeLog(NodeLog log, StreamObserver<StatusReply> responseObserver) {
            if (log.getNodeId() != terminal) {
                try {
                    graphLock.tryLock(500, TimeUnit.MILLISECONDS);
                    String nodeClass = switch (log.getPhaseValue()) {
                        case Phase.TERMINAL_VALUE -> {
                            terminal = log.getNodeId();
                            yield "terminal";
                        }
                        case Phase.DISTRIBUTE_VALUE -> "distribute";
                        case Phase.BFS_VALUE -> "bfs";
                        case Phase.BFS_COMPLETE_VALUE -> "bfsComplete";
                        case Phase.SAMPLE_WAIT_VALUE -> "wait";
                        case Phase.SAMPLE_VALUE -> "sample";
                        case Phase.SYNC_VALUE -> "sync";
                        default -> "";
                    };
                    graphMap.get(1).getNode(String.valueOf(log.getNodeId())).setAttribute("ui.class", nodeClass);
                } catch (InterruptedException ex) {
                    Logging.logError("Failed to acquire lock for graph update: " + ex.getMessage());
                } finally {
                    graphLock.unlock();
                }
            }
            responseObserver.onNext(StatusReply.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();

        }

        @Override
        public void sendLog(MessageLog req, StreamObserver<StatusReply> responseObserver) {
            String label = GraphUtil.edgeLabel(req.getSendingNode(), req.getReceivingNode());
            try {
                graphLock.tryLock(500, TimeUnit.MILLISECONDS);
                graphMap.get(1).getEdge(label).setAttribute("ui.color", 1);
            } catch (InterruptedException ex) {
                Logging.logError("Failed to acquire lock for graph update: " + ex.getMessage());
            } finally {
                graphLock.unlock();
            }
            responseObserver.onNext(StatusReply.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        }

        @Override
        public void pathLog(PathRequest req, StreamObserver<StatusReply> responseObserver) {
            int last = -1;
            Graph graphVis = graphMap.get(1);
            List<Integer> traversedNodes = req.getNodesList();
            for (int i = 0; i < traversedNodes.size(); i++) {
                if (i > 0) {
                    try {
                        graphLock.tryLock(500, TimeUnit.MILLISECONDS);
                        String label = GraphUtil.edgeLabel(last, traversedNodes.get(i));
                        Edge edge = graphVis.getEdge(label);
                        if (edge != null) edge.setAttribute("ui.color", 1);
                    } catch (InterruptedException ex) {
                        Logging.logError("Failed to acquire lock for graph update: " + ex.getMessage());
                    } finally {
                        graphLock.unlock();
                    }
                }
                last = traversedNodes.get(i);
            }
            responseObserver.onNext(StatusReply.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        }

        @Override
        public void couponLog(CouponLogRequest req, StreamObserver<StatusReply> responseObserver) {
            for (CouponMessageRequest coupon : req.getCouponsList()) {
                Graph graphVis = graphMap.get(coupon.getOriginId());
                if (graphVis != null) {
                    try {
                        graphLock.tryLock(500, TimeUnit.MILLISECONDS);
                        String label = GraphUtil.edgeLabel(req.getNodeId(), coupon.getParentId());
                        Edge edge = graphVis.getEdge(label);
                        if (edge != null) edge.setAttribute("ui.color", 1);
                    } catch (InterruptedException ex) {
                        Logging.logError("Failed to acquire lock for graph update: " + ex.getMessage());
                    } finally {
                        graphLock.unlock();
                    }
                }
            }
            responseObserver.onNext(StatusReply.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        }
    }
}

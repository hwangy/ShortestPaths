package DistributedRouting;

import DistributedRouting.grpc.LogGrpc;
import DistributedRouting.grpc.MessageLog;
import DistributedRouting.grpc.StatusReply;
import DistributedRouting.objects.RawGraph;
import DistributedRouting.objects.SampleGraphs;
import DistributedRouting.util.Constants;
import DistributedRouting.util.Logging;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.stub.StreamObserver;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import org.graphstream.graph.Graph;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.ui.spriteManager.Sprite;
import org.graphstream.ui.spriteManager.SpriteManager;
import org.graphstream.ui.view.Viewer;

public class AgentController {

    public static Server initializeListener(Graph graphVis) throws Exception {
        Server server = Grpc.newServerBuilderForPort(Constants.MESSAGE_PORT, InsecureServerCredentials.create())
                .addService(new AgentLoggerImpl(graphVis))
                .build()
                .start();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                interrupt();
                System.err.println("*** server shut down");
            }
        });
        return server;
    }

    public static Graph drawGraph(RawGraph rawGraph) {
        Graph graph = new SingleGraph("Graph");
        for (Integer vertex : rawGraph.getVertices()) {
            graph.addNode(vertex.toString());
        }
        for (Map.Entry<Integer, Set<Integer>> entry : rawGraph.getEdges().entrySet()) {
            String s = entry.getKey().toString();
            for (Integer dest : entry.getValue()) {
                String d = dest.toString();
                String label = String.format("(%s,%s)", s, d);
                Logging.logDebug(label);
                graph.addEdge(label, s, d);
            }
        }
        Viewer viewer = graph.display();
        return graph;
    }

    public static void main(String[] args) {
        RawGraph graph = SampleGraphs.simpleGraph;
        Graph graphVis = drawGraph(graph);
        try {
            initializeListener(graphVis);
        } catch (Exception ex) {
            Logging.logError("Failed to start logging service");
            ex.printStackTrace();
        }

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
    }

    static class AgentLoggerImpl extends LogGrpc.LogImplBase {
        private Graph graphVis;
        private Sprite sprite;
        public AgentLoggerImpl(Graph graphVis) {
            this.graphVis = graphVis;
            SpriteManager spriteManager = new SpriteManager(this.graphVis);
            sprite = spriteManager.addSprite("loc");
            sprite.attachToNode("1");
        }

        @Override
        public void sendLog(MessageLog req, StreamObserver<StatusReply> responseObserver) {
            String label = String.format("(%s,%s)", req.getSendingNode(), req.getReceivingNode());
            sprite.attachToEdge(label);
            sprite.setPosition(0.5);
            Logging.logInfo("Set to " + label);
            responseObserver.onNext(StatusReply.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        }
    }
}

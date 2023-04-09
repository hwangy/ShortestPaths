package DistributedRouting;

import DistributedRouting.grpc.LogGrpc;
import DistributedRouting.grpc.MessageLog;
import DistributedRouting.grpc.StatusReply;
import DistributedRouting.objects.Graph;
import DistributedRouting.objects.SampleGraphs;
import DistributedRouting.util.Constants;
import DistributedRouting.util.Logging;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.stub.StreamObserver;

import java.util.concurrent.CountDownLatch;

public class AgentController {

    public static Server initializeListener() throws Exception {
        Server server = Grpc.newServerBuilderForPort(Constants.MESSAGE_PORT, InsecureServerCredentials.create())
                .addService(new AgentLoggerImpl())
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

    public static void main(String[] args) {
        Graph graph = SampleGraphs.simpleGraph;
        try {
            initializeListener();
        } catch (Exception ex) {
            Logging.logError("Failed to start logging service");
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
        Logging.logService("All threads finished.");
    }

    static class AgentLoggerImpl extends LogGrpc.LogImplBase {
        public AgentLoggerImpl() {

        }

        @Override
        public void sendLog(MessageLog req, StreamObserver<StatusReply> responseObserver) {
            Logging.logInfo(req.toString());
            responseObserver.onNext(StatusReply.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        }
    }
}

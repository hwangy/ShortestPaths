package DistributedRouting;

import DistributedRouting.grpc.*;
import DistributedRouting.util.Constants;
import DistributedRouting.util.GrpcUtil;
import DistributedRouting.util.Logging;
import io.grpc.*;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;

/**
 * An Agent in the graph. The agent is able to send and receive messages from
 * its neighbors.
 */
public class AgentRunner implements Runnable {

    private Queue<MessageRequest> receivedMessages;
    private Queue<BFSMessageRequest> bfsReceivedMessages;
    private LogGrpc.LogBlockingStub loggingStub;
    private Map<Integer, MessageGrpc.MessageBlockingStub> channelMap;
    private Set<Integer> neighbors;
    private final int port;
    private final int id;

    private Integer bfsParent;
    private Boolean bfsAlreadyVisited; 

    private CountDownLatch countdown;

    public Server initializeListener() throws IOException {
        Server server = Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create())
                .addService(new AgentReceiverImpl(receivedMessages))
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

    public AgentRunner(Integer id, Set<Integer> neighbors, CountDownLatch countdown) {
        Logging.logService("Starting agent " + id);
        this.port = Constants.MESSAGE_PORT + id;
        this.id = id;
        this.receivedMessages = new LinkedList<MessageRequest>();
        this.neighbors = neighbors;
        this.countdown = countdown;
        channelMap = new HashMap<>();

        try {
            initializeListener();
        } catch (IOException ex) {
            Logging.logError("Failed to initialize server for agent " + id);
        }

        // Connect to logger
        String target = String.format("localhost:%d", Constants.MESSAGE_PORT);
        ManagedChannel channel = Grpc.newChannelBuilder(target, InsecureChannelCredentials.create())
                .build();
        loggingStub = LogGrpc.newBlockingStub(channel);

    }

    /**
     * For each neighbor, creates a ManagedConnection and adds the corresponding blocking
     * stub to the global hashmap. After creating the connection, this method waits till
     * the connection's status becomes `READY`.
     */
    public void initializeConnections() {
        // Create channel for each neighbor
        for (Integer neighbor : neighbors) {
            String target = String.format("localhost:%d", Constants.MESSAGE_PORT + neighbor);
            ManagedChannel channel = Grpc.newChannelBuilder(target, InsecureChannelCredentials.create())
                    .build();
            channelMap.put(neighbor, MessageGrpc.newBlockingStub(channel));
            while (true) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
                ConnectivityState state = channel.getState(true);
                if (state.equals(ConnectivityState.READY)) {
                    break;
                }
            }
        }
    }

    // won't want to do this then. Instead get parent and send message to children/neighbors.
    public void constructBFSTree(int rootNodeID) {
        // List of the visited nodes for the BFS tree
        List<Integer> visitedNodes = new ArrayList<Integer>();
        // Queue of the nodes to check for the BFS tree
        Queue<Integer> nodesToCheck = new LinkedList<Integer>();

        visitedNodes.add(rootNodeID);
        nodesToCheck.add(rootNodeID);

        while(!nodesToCheck.isEmpty()){
            Integer vertexID = nodesToCheck.remove();
            // I'm confused by the layout and where to put this constructBFSTree etc....

        }

    }

    /**
     * Main loop of AgentRunner. This method regularly checks if a message has been
     * received and, if it has, sends off another message to all of its neighbors
     * except the one from which the message was received.
     */
    public void run() {
        initializeConnections();
        bfsAlreadyVisited = false;

        // Start off the messages
        if (id == 1) {
            bfsAlreadyVisited = true;
            channelMap.get(id + 1).sendMessage(MessageRequest.newBuilder().setNodeId(id).build());

            for (Integer neighbor : neighbors) {
                channelMap.get(neighbor).runBFS(BFSMessageRequest.newBuilder().setNodeId(id).build());
            }
            
        }

        int messageLimit = 2;
        int currMessages = 0;
        try {
            while (true) {
                Thread.sleep(1);

                if (receivedMessages.peek() != null) {
                    currMessages++;
                    MessageRequest msg = receivedMessages.poll();
                    Thread.sleep(1000);
                    // Send message to all neighbors, except the one who sent the message
                    for (Integer vertex : neighbors) {
                        if (vertex != msg.getNodeId()) {
                            MessageReply reply = channelMap.get(vertex).sendMessage(MessageRequest.newBuilder()
                                    .setNodeId(id).build());
                            if (!reply.getSuccess()) {
                                Logging.logService("Received failure from " + vertex);
                            } else {
                                loggingStub.sendLog(MessageLog.newBuilder()
                                        .setSendingNode(id)
                                        .setReceivingNode(vertex).build());
                            }
                        }
                    }
                }

                // Double check that bfs replies are being sent back to the parent successfully.

                if (bfsReceivedMessages.peek() != null && !bfsAlreadyVisited) {
                    bfsAlreadyVisited = true;
                    currMessages++;
                    BFSMessageRequest msg = bfsReceivedMessages.poll();
                    bfsParent = msg.getNodeId();
                    Thread.sleep(1000);
                    // Send message to all neighbors, except the one who sent the message
                    for (Integer vertex : neighbors) {
                        if (vertex != msg.getNodeId()) {
                            BFSMessageReply reply = channelMap.get(vertex).runBFS(BFSMessageRequest.newBuilder()
                                    .setNodeId(id).build());
                            if (!reply.getSuccess()) {
                                Logging.logService("Received failure from " + vertex);
                            } else {
                                loggingStub.sendLog(MessageLog.newBuilder()
                                        .setSendingNode(id)
                                        .setReceivingNode(vertex).build());
                            }
                        }
                    }
                }

                if (currMessages == messageLimit) break;
            }
        } catch (Exception ex) {
            Logging.logError("Encountered error in agent " + id + " in main loop.");
        }
        countdown.countDown();
    }

    /**
     * Message receiver for AgentRunner.
     */
    class AgentReceiverImpl extends MessageGrpc.MessageImplBase {

        private Queue<MessageRequest> requestQueue;
        private Queue<BFSMessageRequest> bfsRequestQueue;

        public AgentReceiverImpl(Queue<MessageRequest> requestQueue) {
            this.requestQueue = requestQueue;
        }

        /**
         * On receiving a message, `sendMessage` will add the received `MessageRequest` object
         * to a global queue, which can be read by the main thread.
         * @param req               A received MessageRequest contained the sender's ID
         * @param responseObserver
         */
        @Override
        public void sendMessage(MessageRequest req, StreamObserver<MessageReply> responseObserver) {
            requestQueue.add(req);
            responseObserver.onNext(GrpcUtil.genSuccessfulReply());
            responseObserver.onCompleted();
        }

        @Override
        public void runBFS(BFSMessageRequest req, StreamObserver<BFSMessageReply> responseObserver) {
            bfsRequestQueue.add(req);
            responseObserver.onNext(GrpcUtil.genSuccessfulReplyBFS());
            responseObserver.onCompleted();
        }
    }
}

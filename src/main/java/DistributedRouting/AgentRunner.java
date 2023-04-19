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
import java.util.concurrent.ThreadLocalRandom;

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
                .addService(new AgentReceiverImpl(receivedMessages, bfsReceivedMessages))
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

    public AgentRunner(Integer id, Set<Integer> neighbors, CountDownLatch countdown) {
        Logging.logService("Starting agent " + id);
        this.port = Constants.MESSAGE_PORT + id;
        this.id = id;
        this.receivedMessages = new LinkedList<>();
        this.bfsReceivedMessages = new LinkedList<>();
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

    public List<CouponMessageRequest> phaseOne(Integer lambda){

        //Integer degree = neighbors.size();
        // how to set eta?
        Integer eta = 10;

        List<CouponMessageRequest> coupons = new ArrayList<>();
        for (int iteration = 1; iteration <= eta; iteration++){
            Integer randomNum = ThreadLocalRandom.current().nextInt(lambda);
            // Create the initial messages ('coupons') for node v containing the node's ID and the desired walk length of lambda + randomNum
            coupons.add(CouponMessageRequest.newBuilder().setNodeId(id).setDesiredWalkLength(lambda + randomNum).build());
        }

        for (int i = 1; i <= 2 * lambda; i++){
            List<CouponMessageRequest> couponsToRemove = new ArrayList<CouponMessageRequest>();
            for (CouponMessageRequest coupon : coupons) {
                if(coupon.getDesiredWalkLength() > i) {

                    // Pick a neighbor of the vertex uniformly at random
                    Integer randomIndex = ThreadLocalRandom.current().nextInt(coupons.size());
                    Iterator<Integer> iter = neighbors.iterator();
                    for (int j = 0; j < randomIndex; j++) {
                        iter.next();
                    }
                    Integer randomNeighbor = iter.next();
                    channelMap.get(randomNeighbor).sendCoupon(coupon);
                    couponsToRemove.add(coupon);
                }
            }

            for (CouponMessageRequest coupon : couponsToRemove) {
                coupons.remove(coupon);
            }

            // TO ADD: WAITING FOR THE POTENTIAL MESSAGES FROM NEIGHBORING NODES
        }

        // Return the final coupons after the iterations.
        return coupons;

    }

    /**
     * Main loop of AgentRunner. This method regularly checks if a message has been
     * received and, if it has, sends off another message to all of its neighbors
     * except the one from which the message was received.
     */
    public void run() {
        initializeConnections();
        bfsAlreadyVisited = false;

        // how to set lambda / should we put it as a parameter?
        Integer lambda = 10;

        /*List<CouponMessageRequest> coupons = phaseOne(lambda);*/
     
        // Start off the messages
        if (id == 1) {
            bfsReceivedMessages.add(BFSMessageRequest.newBuilder()
                    .setOriginId(-1)
                    .setParentId(-1).build());
        }

        try {
            while (true) {
                Thread.sleep(1);

                // Double check that bfs replies are being sent back to the parent successfully.
                if (bfsReceivedMessages.peek() != null && !bfsAlreadyVisited) {
                    Thread.sleep(1000);
                    bfsAlreadyVisited = true;
                    BFSMessageRequest msg = bfsReceivedMessages.poll();
                    bfsParent = msg.getParentId();
                    // Send message to all neighbors, except the one who sent the message
                    for (Integer vertex : neighbors) {
                        if (vertex != msg.getParentId()) {
                            BFSMessageReply reply = channelMap.get(vertex).runBFS(BFSMessageRequest.newBuilder()
                                    .setOriginId(msg.getOriginId())
                                    .setParentId(id).build());
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
            }
        } catch (Exception ex) {
            Logging.logError("Encountered error in agent " + id + " in main loop.");
            ex.printStackTrace();
        }
        countdown.countDown();
    }

    /**
     * Message receiver for AgentRunner.
     */
    class AgentReceiverImpl extends MessageGrpc.MessageImplBase {

        private Queue<MessageRequest> requestQueue;
        private Queue<BFSMessageRequest> bfsRequestQueue;
        private Queue<CouponMessageRequest> couponRequestQueue;

        public AgentReceiverImpl(Queue<MessageRequest> requestQueue, Queue<BFSMessageRequest> bfsQueue) {
            this.requestQueue = requestQueue;
            this.bfsRequestQueue = bfsQueue;
        }

        /**
         * On receiving a message, `sendMessage` will add the received `MessageRequest` object
         * to a global queue, which can be read by the main thread.
         * @param req               A received MessageRequest containing the sender's ID
         * @param responseObserver
         */
        @Override
        public void sendMessage(MessageRequest req, StreamObserver<MessageReply> responseObserver) {
            requestQueue.add(req);
            responseObserver.onNext(GrpcUtil.genSuccessfulReply());
            responseObserver.onCompleted();
        }

        /**
         * On receiving a BFS message, `runBFS` will add the received `BFSMessageRequest` object
         * to a global queue, which can be read by the main thread.
         * UNSURE IF THIS IS CORRECT.
         * @param req               A received BFSMessageRequest contained the sender's ID
         * @param responseObserver
         */
        @Override
        public void runBFS(BFSMessageRequest req, StreamObserver<BFSMessageReply> responseObserver) {
            bfsRequestQueue.add(req);
            responseObserver.onNext(GrpcUtil.genSuccessfulReplyBFS());
            responseObserver.onCompleted();
        }

        @Override
        public void sendCoupon(CouponMessageRequest req, StreamObserver<CouponMessageReply> responseObserver) {
            couponRequestQueue.add(req);
            responseObserver.onNext(GrpcUtil.genSuccessfulReplyCoupon());
            responseObserver.onCompleted();
        }
    }
}

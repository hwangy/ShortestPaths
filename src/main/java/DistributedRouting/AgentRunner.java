package DistributedRouting;

import DistributedRouting.grpc.*;
import DistributedRouting.util.Constants;
import DistributedRouting.util.GrpcUtil;
import DistributedRouting.util.Logging;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import org.checkerframework.checker.units.qual.A;
import scala.Int;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;

/**
 * An Agent in the graph. The agent is able to send and receive messages from
 * its neighbors.
 */
public class AgentRunner implements Runnable {

    private Queue<BFSMessageRequest> bfsReceivedMessages;

    private HashMap<Integer, Queue<CouponMessageRequest>> receivedCoupons;
    private HashMap<Integer, Set<Integer>> receivedNeighbors;

    private LogGrpc.LogBlockingStub loggingStub;
    private Map<Integer, MessageGrpc.MessageBlockingStub> channelMap;
    private Set<Integer> neighbors;
    private final int port;
    private final int id;

    private final int lambda;

    private Integer bfsParent;
    private Boolean bfsAlreadyVisited; 

    private CountDownLatch countdown;

    public Server initializeListener() throws IOException {
        Server server = Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create())
                .addService(new AgentReceiverImpl(bfsReceivedMessages))
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

    public AgentRunner(Integer id, Set<Integer> neighbors, CountDownLatch countdown, int lambda) {
        Logging.logService("Starting agent " + id);
        port = Constants.MESSAGE_PORT + id;
        this.id = id;
        this.neighbors = neighbors;
        this.countdown = countdown;
        this.lambda = lambda;

        bfsReceivedMessages = new LinkedList<>();
        receivedCoupons = new HashMap<>();
        receivedNeighbors = new HashMap<>();
        for (int i = 0; i <= lambda; i++) {
            receivedCoupons.put(i, new LinkedList<>());
            receivedNeighbors.put(i, new HashSet<>());
        }

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

    public List<CouponMessageRequest> phaseOne(){

        //Integer degree = neighbors.size();
        // how to set eta?
        Integer eta = 1;

        List<CouponMessageRequest> coupons = new ArrayList<>();
        int numNeighbors = neighbors.size();
        List<Integer> neighborList = neighbors.stream().toList();

        Queue<CouponMessageRequest> startingCoupons = new LinkedList<>();
        for (int i = 1; i <= numNeighbors; i++){
            // Integer randomNum = ThreadLocalRandom.current().nextInt(lambda);
            // Create the initial messages ('coupons') for node v containing the node's ID and the desired walk length of lambda + randomNum
            startingCoupons.add(CouponMessageRequest.newBuilder()
                    .setCurrentWalkLength(0).setOriginId(id).setParentId(-1)
                    .setForward(i <= eta).build());
        }
        receivedCoupons.put(0, startingCoupons);
        receivedNeighbors.put(0, new HashSet<>(neighbors));

        // Iterate from 1 to lambda + 1. The final round is used for bookkeeping
        for (int iter = 1; iter <= lambda + 1; iter++) {
            try {
                while (true) {
                    Thread.sleep(1);
                    // Wait until we've received a message from all neighbors from
                    // iteration iter-1
                    if (receivedNeighbors.get(iter-1).size() == neighbors.size()) {
                        Set<Integer> receivedFromNeighbors = receivedNeighbors.get(iter-1);
                        Queue<CouponMessageRequest> couponsToProcess = receivedCoupons.get(iter-1);

                        Thread.sleep(1000);
                        while (couponsToProcess.size() > 0) {
                            CouponMessageRequest req = couponsToProcess.poll();

                            if (req.getForward()) {
                                if (req.getCurrentWalkLength() < lambda) {
                                    // Pick a neighbor of the vertex uniformly at random
                                    Integer randomNeighbor = neighborList.get(
                                            ThreadLocalRandom.current().nextInt(neighbors.size()));
                                    channelMap.get(randomNeighbor).sendCoupon(
                                            CouponMessageRequest.newBuilder(req)
                                                    .setParentId(id)
                                                    .setCurrentWalkLength(req.getCurrentWalkLength() + 1).build());

                                    // This node will have received a message in this iteration
                                    receivedFromNeighbors.remove(randomNeighbor);
                                } else {
                                    coupons.add(req);
                                }
                            }
                        }

                        if (iter == lambda + 1) break;
                        // Now forward terminal coupons to the rest of the neighbors which
                        // have not received a message
                        for (Integer others : receivedFromNeighbors) {
                            channelMap.get(others).sendCoupon(CouponMessageRequest.newBuilder()
                                    .setCurrentWalkLength(iter)
                                    .setParentId(id).setForward(false).build());
                        }
                        break;
                    }
                }
            } catch (InterruptedException ex) {
                Logging.logError("Encountered exception in thread " + id + ": " + ex.getMessage());
            }
       }
       return coupons;
    }

    public void sendMoreCoupons(int vertex, int eta, int lambda) {
        List<CouponMessageRequest> coupons = sendMoreCouponsPart1(vertex, eta, lambda);
        sendMoreCouponsPart2(vertex, eta, lambda, coupons);
    }

    public List<CouponMessageRequest>  sendMoreCouponsPart1(int vertex, int eta, int lambda) {
        List<CouponMessageRequest> newCoupons = new ArrayList<CouponMessageRequest>();
        List<Integer> neighborList = neighbors.stream().toList();
        for (int j = 1; j <= eta; j++) {
            newCoupons.add(CouponMessageRequest.newBuilder()
                    .setCurrentWalkLength(0).setOriginId(id).setParentId(-1)
                    .setForward(true).build());

        }

        for (int i = 1; i <= lambda; i++) {
            for(CouponMessageRequest coupon : newCoupons) {
                Integer randomNeighbor = neighborList.get(
                    ThreadLocalRandom.current().nextInt(neighbors.size()));
                channelMap.get(randomNeighbor).sendCoupon(coupon);
                // Need to send the number of new coupons for which z is
                // picked as a receiver, denoted by c(u, v).
            }
        }

        return newCoupons;
    }

    public void sendMoreCouponsPart2(int vertex, int eta, int lambda, List<CouponMessageRequest> coupons) {
        for (int i = 0; i <= lambda - 1; i++) {
            Integer randomNum = Math.random();
            if (randomNum <= 1/(lambda - i)) {
                // stop sending the coupon further
            }
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

        // how to set lambda / should we put it as a parameter?
        Integer lambda = 1;

        List<CouponMessageRequest> coupons = phaseOne();

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

        private Queue<BFSMessageRequest> bfsRequestQueue;

        public AgentReceiverImpl(Queue<BFSMessageRequest> bfsQueue) {
            this.bfsRequestQueue = bfsQueue;
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
            receivedCoupons.get(req.getCurrentWalkLength()).add(req);
            receivedNeighbors.get(req.getCurrentWalkLength()).add(req.getParentId());
            responseObserver.onNext(GrpcUtil.genSuccessfulReplyCoupon());
            responseObserver.onCompleted();
        }
    }
}
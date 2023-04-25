package DistributedRouting;

import DistributedRouting.grpc.*;
import DistributedRouting.util.Constants;
import DistributedRouting.util.GrpcUtil;
import DistributedRouting.util.Logging;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import org.checkerframework.checker.units.qual.A;
import scala.Int;

import javax.sound.midi.Receiver;
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
    private Set<Integer> treeChildren;
    private final int port;
    private final int id;

    private final int lambda;

    private final int numVertices;
    private Integer bfsParent = null;
    private Integer bfsOrigin = null;
    private Boolean bfsAlreadyVisited;

    private Integer bfsDoneCount = 0;
    private Integer bfsLevel;

    private AgentCore core;

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

    private static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Constructor for AgentRunner, which initializes all of the variable and the listener.
     * @param numVertices the number of vertices in the graph
     * @param id The id of the node (the machine being run)
     * @param neighbors The neighbors of the node
     * @param countdown 
     * @param lambda A parameter determining the performance guarantees of the algorithm
     */
    public AgentRunner(Integer numVertices, Integer id, Set<Integer> neighbors, CountDownLatch countdown, int lambda) {
        port = Constants.MESSAGE_PORT + id;
        this.id = id;
        this.neighbors = neighbors;
        this.countdown = countdown;
        this.lambda = lambda;
        this.numVertices = numVertices;
        this.core = new AgentCore();

        treeChildren = new HashSet<>();
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
                sleep(100);
                ConnectivityState state = channel.getState(true);
                if (state.equals(ConnectivityState.READY)) {
                    break;
                }
            }
        }
    }

    /**
     * phaseOne of the algorithm generates short walks by coupon distribution. 
     * Each node performs short random walks. At the end of the process, different nodes
     * are holding a coupon containing the ID of the starting vertex v.
     */
    public Map<Integer, List<CouponMessageRequest>> phaseOne(){
        Integer eta = 2;

        Map<Integer, List<CouponMessageRequest>> coupons = new HashMap<>();
        int numNeighbors = neighbors.size();

        Queue<CouponMessageRequest> startingCoupons = new LinkedList<>();
        for (int i = 1; i <= numNeighbors; i++){
            startingCoupons.add(CouponMessageRequest.newBuilder()
                    .setCurrentWalkLength(0).setOriginId(id).setParentId(-1)
                    .setForward(i <= eta).build());
        }
        receivedCoupons.put(0, startingCoupons);
        receivedNeighbors.put(0, new HashSet<>(neighbors));

        // Iterate from 1 to lambda + 1. The final round is used for bookkeeping
        for (int iter = 1; iter <= lambda + 1; iter++) {
            waitForCouponsAndSend(iter, lambda, coupons);
        }
        // Indicate this node has finished distributing its coupons
        loggingStub.sendNodeLog(NodeLog.newBuilder().setNodeId(id).setPhase(Phase.DISTRIBUTE).build());

       return coupons;
    }

    /**
     * This method waits to recieve coupons from the neighbors of the node, and then 
     * forwards coupon to a uniformly random neighbor.
     */
    public Map<Integer, List<CouponMessageRequest>> waitForCouponsAndSend(int iter, int lambda, Map<Integer, List<CouponMessageRequest>> coupons) {
        List<Integer> neighborList = neighbors.stream().toList();
        try {
            while (true) {
                Thread.sleep(1);
                // Wait until we've received a message from all neighbors from
                // iteration iter-1
                if (receivedNeighbors.get(iter-1).size() == neighbors.size()) {
                    loggingStub.couponLog(CouponLogRequest.newBuilder()
                            .addAllCoupons(receivedCoupons.get(iter-1))
                            .setNodeId(id).build());
                    Set<Integer> receivedFromNeighbors = receivedNeighbors.get(iter-1);
                    Queue<CouponMessageRequest> couponsToProcess = receivedCoupons.get(iter-1);

                    Thread.sleep(500);
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
                                coupons.computeIfAbsent(req.getOriginId(), k -> new ArrayList<>()).add(req);
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

        return coupons;

    }

    /**
     * sendMoreCoupons is one of the subroutines of the algorithm focused on sending more coupons probabilistically 
     * to neighbors of the node. It is broken up into sendMoreCouponsPart1 and sendMoreCouponsPart2.
     */
    public void sendMoreCoupons(int vertex, int eta, int lambda) {
        sendMoreCouponsPart2(vertex, eta, lambda, sendMoreCouponsPart1(vertex, eta, lambda));
    }

    public Map<Integer, List<CouponMessageRequest>>  sendMoreCouponsPart1(int vertex, int eta, int lambda) {

        Map<Integer, List<CouponMessageRequest>> coupons = new HashMap<>();

        Queue<CouponMessageRequest> newCoupons = new LinkedList<CouponMessageRequest>();
        for (int j = 1; j <= eta; j++) {
            newCoupons.add(CouponMessageRequest.newBuilder()
                    .setCurrentWalkLength(0).setOriginId(id).setParentId(-1)
                    .setForward(true).build());
        }
        
        receivedCoupons.put(0, newCoupons);
        receivedNeighbors.put(0, new HashSet<>(neighbors));

        // Check if this is ok or need to send c(u, v) and reconstruct that many coupons again. 
        // Can add this to design notebook as well.
        for (int iter = 1; iter <= lambda; iter++) {
            waitForCouponsAndSend(iter, lambda, coupons);
        }

        return coupons;
    }

    /**
     * Each coupon has now been forwarded for lambda steps. 
     * These coupons are now extended probabilistically further by r steps where each r is independent and uniform in the range [0, lambda − 1].
     */
    public void sendMoreCouponsPart2(int vertex, int eta, int lambda, Map<Integer, List<CouponMessageRequest>> coupons) {
        List<Integer> sourceNodes = new LinkedList<Integer>();
        List<Integer> neighborList = neighbors.stream().toList();
        
        for (int i = 0; i <= lambda - 1; i++) {
            for (Integer node : coupons.keySet()) {
                for (CouponMessageRequest coupon : coupons.get(node)) {
                    double randomNum = ThreadLocalRandom.current().nextDouble();
                    if (randomNum <= 1 / (lambda - i)) {
                        sourceNodes.add(coupon.getOriginId());
                    } else {
                        // Each node picks a neighbor correspondingly: does this mean uniformly?
                        Integer randomNeighbor = neighborList.get(
                                ThreadLocalRandom.current().nextInt(neighbors.size()));
                        channelMap.get(randomNeighbor).sendCoupon(coupon);
                    }
                }
            }
        }       
    }

    /**
     * Receive and forward BFS messages.
     */
    public void bfsTree(int root) {
        bfsDoneCount = 0;
        bfsOrigin = null;
        bfsParent = null;
        bfsLevel = null;
        bfsAlreadyVisited = false;
        treeChildren.clear();

        // Start off the messages
        if (id == root) {
            bfsReceivedMessages.add(BFSMessageRequest.newBuilder()
                    .setOriginId(root)
                    .setLevel(0)
                    .setParentId(-1).build());
        }

        try {
            while (true) {
                Thread.sleep(1);

                if (bfsDoneCount == neighbors.size() && bfsReceivedMessages.size() == 0) {
                    loggingStub.sendNodeLog(NodeLog.newBuilder().setNodeId(id).setPhase(Phase.BFS).build());
                    if (bfsParent > 0) {
                        channelMap.get(bfsParent).completeBFS(
                                BFSDoneRequest.newBuilder().setNodeId(id).build());
                    }
                    break;
                } else if (bfsReceivedMessages.peek() != null) {
                    Thread.sleep(500);
                    BFSMessageRequest msg = bfsReceivedMessages.poll();

                    if (!bfsAlreadyVisited) {
                        if (id != root) bfsDoneCount++;

                        bfsAlreadyVisited = true;
                        bfsOrigin = msg.getOriginId();
                        bfsParent = msg.getParentId();
                        bfsLevel = msg.getLevel();

                        // Set as child
                        if (bfsParent > 0) {
                            channelMap.get(bfsParent).setChild(BFSChildRequest.newBuilder().setNodeId(id).build());
                        }

                        // Send message to all neighbors, except the one who sent the message
                        for (Integer vertex : neighbors) {
                            if (vertex != msg.getParentId()) {
                                BFSMessageReply reply = channelMap.get(vertex).runBFS(BFSMessageRequest.newBuilder()
                                        .setOriginId(msg.getOriginId())
                                        .setLevel(msg.getLevel() + 1)
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
                    } else {
                        channelMap.get(msg.getParentId()).completeBFS(
                                BFSDoneRequest.newBuilder().setNodeId(id).build());
                    }
                }
            }
        } catch (Exception ex) {
            Logging.logError("Encountered error in agent " + id + " in main loop.");
            ex.printStackTrace();
        }
        //countdown.countDown();
    }

    public CouponMessageRequest sampleCoupon(int startId, Map<Integer, List<CouponMessageRequest>> coupons) {
        // Clear all previously received coupons
        receivedCoupons.clear();
        receivedNeighbors.clear();

        bfsTree(startId);

        // If there are descendents wait for all children's messages
        if (treeChildren.size() > 0) {
            loggingStub.sendNodeLog(NodeLog.newBuilder().setNodeId(id).setPhase(Phase.SAMPLE_WAIT).build());
            while (receivedCoupons.get(bfsLevel) == null || receivedCoupons.get(bfsLevel).size() < treeChildren.size()) {
                sleep(10);
            }
            sleep(500);
        }

        // Pick random coupon to send up a level
        loggingStub.sendNodeLog(NodeLog.newBuilder().setNodeId(id).setPhase(Phase.SAMPLE).build());
        List<CouponMessageRequest> originCoupons = coupons.getOrDefault(bfsOrigin, new ArrayList<>());
        List<CouponMessageRequest> fromChildren = (List) receivedCoupons.getOrDefault(bfsLevel, new LinkedList<>());
        CouponMessageRequest toForward = core.pickWithWeights(id, originCoupons, fromChildren);
        if (toForward == null) {
            toForward = CouponMessageRequest.newBuilder().setOriginId(id).setWeight(0).build();
        }

        // Clear all previously received coupons
        receivedCoupons.clear();
        receivedNeighbors.clear();
        if (bfsLevel == 0) {
            return toForward;
        } else {
            channelMap.get(bfsParent).sendCoupon(CouponMessageRequest.newBuilder(toForward)
                    .setCurrentWalkLength(bfsLevel - 1).build());
            return null;
        }
    }

    public Integer phaseTwo(Map<Integer, List<CouponMessageRequest>> coupons) {
        Integer destinationNode = -1;

        // Source node creates token and set of connectors
        int start = 1;
        if (id == 1) {
            CouponMessageRequest token = CouponMessageRequest.newBuilder()
                .setCurrentWalkLength(0).setOriginId(id).setParentId(-1)
                .setForward(true).build();
            List<Integer> connectors = new LinkedList<Integer>();
            // Initially C = {s} where s is the source node
            connectors.add(id);
        }

        int maxLength = 2;
        for (int l = 0; l < maxLength; l++) {
            CouponMessageRequest next = sampleCoupon(start, coupons);
            
            if (next != null) {
                sendMoreCoupons(id, eta, lambda);
                next = sampleCoupon(id, coupons);
            }
            
            if (id == start) {
                Logging.logInfo("Node " + id + " sampled " + next.getOriginId());
            } else {
                // Wait for coupon receipt
                while (true) {
                    sleep(500);
                    if (receivedCoupons.get(bfsLevel) != null && receivedCoupons.get(bfsLevel).peek() != null) {
                        next = receivedCoupons.get(bfsLevel).poll();
                        break;
                    }
                }
            }

            // Forward request to children
            next = CouponMessageRequest.newBuilder()
                    .setOriginId(next.getOriginId()).setCurrentWalkLength(bfsLevel + 1)
                    .setParentId(id).build();
            for (int child : treeChildren) {
                channelMap.get(child).sendCoupon(next);
            }
            start = next.getOriginId();

            loggingStub.sendNodeLog(NodeLog.newBuilder().setNodeId(id).setPhase(Phase.SYNC).build());
        }

        return destinationNode;
    }

    /**
     * Main loop of AgentRunner. This method regularly checks if a message has been
     * received and, if it has, sends off another message to all of its neighbors
     * except the one from which the message was received.
     */
    public void run() {
        initializeConnections();

        // how to set lambda / should we put it as a parameter?
        Integer lambda = 1;

        Map<Integer, List<CouponMessageRequest>> coupons = phaseOne();
        Integer destinationNode = phaseTwo(coupons);
        Logging.logInfo("Node " + id + " exiting.");
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
        public void setChild(BFSChildRequest req, StreamObserver<BFSMessageReply> responseObserver) {
            treeChildren.add(req.getNodeId());
            responseObserver.onNext(GrpcUtil.genSuccessfulReplyBFS());
            responseObserver.onCompleted();
        }

        @Override
        public void completeBFS(BFSDoneRequest reply, StreamObserver<BFSMessageReply> responseObserer) {
            bfsDoneCount++;
            responseObserer.onNext(GrpcUtil.genSuccessfulReplyBFS());
            responseObserer.onCompleted();
        }

        @Override
        public void sendCoupon(CouponMessageRequest req, StreamObserver<CouponMessageReply> responseObserver) {
            receivedCoupons.computeIfAbsent(req.getCurrentWalkLength(), k -> new LinkedList<>()).add(req);
            receivedNeighbors.computeIfAbsent(req.getCurrentWalkLength(), k -> new HashSet<>()).add(req.getParentId());
            responseObserver.onNext(GrpcUtil.genSuccessfulReplyCoupon());
            responseObserver.onCompleted();
        }
    }
}
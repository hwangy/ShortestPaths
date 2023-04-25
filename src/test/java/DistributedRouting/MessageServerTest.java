package DistributedRouting;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import DistributedRouting.grpc.MessageRequest;
import DistributedRouting.grpc.BFSMessageRequest;
import DistributedRouting.grpc.CouponMessageRequest;

/**
 * Unit tests for the MessageServer class. These unit tests encompass the various functionalities of 
 * MessageServer, including sending one or multiple messages, receiving a message, and performing an internal event.
 * These tests mostly concern how the logical clock gets updated.
 */
class MessageServerTest {

    @Test
    void testMessageRequest() {
        MessageRequest message = MessageRequest.newBuilder()
            .setNodeId(1)
            .build();

        Assertions.assertTrue(message.getNodeId() == 1);
    }

    @Test
    void testBFSMessageRequest() {
        BFSMessageRequest message = BFSMessageRequest.newBuilder()
            .setOriginId(1)
            .setParentId(2)
            .setLevel(5)
            .build();

        Assertions.assertTrue(message.getOriginId() == 1);
        Assertions.assertTrue(message.getParentId() == 2);
        Assertions.assertTrue(message.getLevel() == 5);
    }

    @Test
    void testCouponMessageRequest() {
        CouponMessageRequest message = CouponMessageRequest.newBuilder()
            .setForward(true)
            .setOriginId(1)
            .setParentId(2)
            .setCurrentWalkLength(10)
            .addFullWalk(11)
            .setWeight(1)
            .build();
        
        Assertions.assertTrue(message.getForward() == true);
        Assertions.assertTrue(message.getOriginId() == 1);
        Assertions.assertTrue(message.getParentId() == 2);
        Assertions.assertTrue(message.getCurrentWalkLength() == 10);
        //Assertions.assertTrue(message.getFullWalk().contains(11));
        Assertions.assertTrue(message.getWeight() == 1);
    }
}
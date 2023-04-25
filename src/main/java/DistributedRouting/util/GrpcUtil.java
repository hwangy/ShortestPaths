package DistributedRouting.util;

import DistributedRouting.grpc.MessageReply;
import DistributedRouting.grpc.BFSMessageReply;
import DistributedRouting.grpc.CouponMessageReply;


public class GrpcUtil {
    /**
     * Generates a message reply for success
     */
    public static MessageReply genSuccessfulReply() {
        return MessageReply.newBuilder().setSuccess(true).build();
    }

    /**
     * Generates a BFS message reply for success
     */
    public static BFSMessageReply genSuccessfulReplyBFS() {
        return BFSMessageReply.newBuilder().setSuccess(true).build();
    }

    /**
     * Generates a coupon message reply for success
     */
    public static CouponMessageReply genSuccessfulReplyCoupon() {
        return CouponMessageReply.newBuilder().setSuccess(true).build();
    }
}

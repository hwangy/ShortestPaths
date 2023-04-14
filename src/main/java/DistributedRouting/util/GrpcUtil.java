package DistributedRouting.util;

import DistributedRouting.grpc.MessageReply;
import DistributedRouting.grpc.BFSMessageReply;
import DistributedRouting.grpc.CouponMessageReply;


public class GrpcUtil {
    public static MessageReply genSuccessfulReply() {
        return MessageReply.newBuilder().setSuccess(true).build();
    }

    public static BFSMessageReply genSuccessfulReplyBFS() {
        return BFSMessageReply.newBuilder().setSuccess(true).build();
    }

    public static CouponMessageReply genSuccessfulReplyCoupon() {
        return CouponMessageReply.newBuilder().setSuccess(true).build();
    }
}

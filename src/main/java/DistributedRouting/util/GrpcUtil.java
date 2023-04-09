package DistributedRouting.util;

import DistributedRouting.grpc.MessageReply;

public class GrpcUtil {
    public static MessageReply genSuccessfulReply() {
        return MessageReply.newBuilder().setSuccess(true).build();
    }
}

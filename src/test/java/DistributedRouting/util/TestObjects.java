package DistributedRouting.util;

import DistributedRouting.grpc.CouponMessageRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestObjects {

    public static CouponMessageRequest testCouponNoWeight(int id) {
        return CouponMessageRequest.newBuilder().setOriginId(id).build();
    }

    public static CouponMessageRequest testCoupon(int id, int weight) {
        return CouponMessageRequest.newBuilder().setOriginId(id).setWeight(weight).build();
    }
    public static List<CouponMessageRequest> oneCouponListNoWeights(int id) {
        return new ArrayList<>(Arrays.asList(testCouponNoWeight(id)));
    }

    public static List<CouponMessageRequest> twoCouponListNoWeights(int id1, int id2) {
        return new ArrayList<>(Arrays.asList(testCouponNoWeight(id1), testCouponNoWeight(id2)));
    }

    public static List<CouponMessageRequest> twoCouponListWithWeights(int id1, int weight1, int id2, int weight2) {
        return new ArrayList<>(Arrays.asList(testCoupon(id1, weight1), testCoupon(id2, weight2)));
    }
}

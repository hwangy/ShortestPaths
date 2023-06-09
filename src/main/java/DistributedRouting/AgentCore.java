package DistributedRouting;

import DistributedRouting.grpc.CouponMessageRequest;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class AgentCore {

    private ThreadLocalRandom random;

    public AgentCore() {
        random = ThreadLocalRandom.current();
    }

    /**
     * Primarily used for testing, where ThreadLocalRandom object can be mocked.
     * @param random    An instance of a ThreadLocalRandom object.
     */
    public AgentCore(ThreadLocalRandom random) {
        this.random = random;
    }

    /**
     * Picks a coupon between those received from children as well as coupons held by the node itself.
     * @param id                The ID of the node.
     * @param ownCoupons        A list of held coupons.
     * @param receivedCoupons   A list of received coupons.
     * @return                  Return the selected coupon.
     */
    public CouponMessageRequest pickWithWeights(int id, List<CouponMessageRequest> ownCoupons,
                                                 List<CouponMessageRequest> receivedCoupons) {
        // Get the cumulative weights of the coupons
        int cumSum = ownCoupons.size();
        cumSum += receivedCoupons.stream().map(c -> c.getWeight()).reduce(0, Integer::sum);

        CouponMessageRequest.Builder builder = null;
        if (cumSum != 0) {
            int randChoice = random.nextInt(1, cumSum+1);

            if (randChoice <= ownCoupons.size()) {
                // Case: Coupon from current vertex is selected. Return the corresponding coupon
                // and write this node's ID on the coupon
                builder = CouponMessageRequest.newBuilder(ownCoupons.remove(randChoice - 1)).setOriginId(id);
            } else {
                // Case: Coupon from a descendant in the BFS tree is selected
                // Pick the coupon based off of `randChoice`
                int sum = ownCoupons.size();
                for (CouponMessageRequest msg : receivedCoupons) {
                    sum += msg.getWeight();
                    if (randChoice <= sum) {
                        builder = CouponMessageRequest.newBuilder(msg);
                        break;
                    }
                }
            }
        }

        return (builder == null) ? null : builder.setWeight(cumSum).build();
    }

}

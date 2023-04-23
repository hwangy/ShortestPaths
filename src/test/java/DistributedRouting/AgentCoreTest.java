package DistributedRouting;

import DistributedRouting.grpc.CouponMessageRequest;
import DistributedRouting.util.TestObjects;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class AgentCoreTest {
    @Test
    void testPickFirstWithWeights() {
        ThreadLocalRandom random = Mockito.mock(ThreadLocalRandom.class, withSettings().withoutAnnotations());
        when(random.nextInt(anyInt(), anyInt())).thenReturn(1);
        AgentCore core = new AgentCore(random);

        List<CouponMessageRequest> testList = TestObjects.oneCouponListNoWeights(1);
        CouponMessageRequest returnedCoupon = core.pickWithWeights(1, testList, new LinkedList<>());
        Assert.assertEquals(1, returnedCoupon.getOriginId());
    }

    @Test
    void testPickFirstWithWeightsFromLengthTwo() {
        ThreadLocalRandom random = Mockito.mock(ThreadLocalRandom.class, withSettings().withoutAnnotations());
        when(random.nextInt(anyInt(), anyInt())).thenReturn(1);
        AgentCore core = new AgentCore(random);

        List<CouponMessageRequest> testList = TestObjects.twoCouponListNoWeights(1, 2);
        CouponMessageRequest returnedCoupon = core.pickWithWeights(1, testList, new LinkedList<>());
        Assert.assertEquals(1, returnedCoupon.getOriginId());
    }

    @Test
    void testPickDescendentWithWeights() {
        ThreadLocalRandom random = Mockito.mock(ThreadLocalRandom.class, withSettings().withoutAnnotations());
        when(random.nextInt(anyInt(), anyInt())).thenReturn(4);
        AgentCore core = new AgentCore(random);

        List<CouponMessageRequest> testList = TestObjects.twoCouponListNoWeights(1, 2);
        List<CouponMessageRequest> descendentList =
                TestObjects.twoCouponListWithWeights(3,1,4,2);
        CouponMessageRequest returnedCoupon = core.pickWithWeights(1, testList, descendentList);
        Assert.assertEquals(4, returnedCoupon.getOriginId());
    }
}

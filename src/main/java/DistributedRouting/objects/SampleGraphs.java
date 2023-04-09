package DistributedRouting.objects;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.Arrays;

/**
 * Sample graphs from which the network topology can be derived.
 */
public class SampleGraphs {

    /**
     * K3 graph.
     */
    public static RawGraph simpleGraph = new RawGraph(Arrays.asList(1,2,3), ImmutableMap.of(
            1, ImmutableSet.of(2),
            2,ImmutableSet.of(3),
            3,ImmutableSet.of(1)));
}

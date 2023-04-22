package DistributedRouting.objects;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.*;
import java.util.stream.IntStream;

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

    public static RawGraph simpleGraphTwo = new RawGraph(Arrays.asList(1,2,3,4,5), ImmutableMap.of(
            1, ImmutableSet.of(2,3),
            3,ImmutableSet.of(4,5),
            2,ImmutableSet.of(4)));

    public static RawGraph erdosReyniGraph(int n, float p, Random random) {
        List<Integer> vertices = IntStream.rangeClosed(1, n).boxed().toList();
        HashMap<Integer, Set<Integer>> edges = new HashMap<>();
        for (int i = 1; i < n; i++) {
            Set<Integer> currentEdges = new HashSet<>();
            for (int j = i+1; j <= n; j++) {
                if (random.nextFloat(0,1) > p) continue;
                currentEdges.add(j);
            }
            edges.put(i, currentEdges);
        }
        return new RawGraph(vertices, edges);
    }
}

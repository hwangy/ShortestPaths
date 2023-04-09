package DistributedRouting.objects;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Simple interface to define a Graph. Graphs have vertices and edges and are
 * able to answer queries like "what are neighbors of vetex 1?".
 */
public class Graph {
    private List<Integer> vertices;
    private Map<Integer, Set<Integer>> edges;

    public Graph(List<Integer> vertices, Map<Integer, Set<Integer>> edges) {
        this.vertices = vertices;
        this.edges = edges;
    }

    public List<Integer> getVertices() {
        return vertices;
    }

    public Map<Integer, Set<Integer>> getEdges() {
        return edges;
    }

    /**
     * Get all neighbors of a given vertex.
     *
     * @param vertex    Vertex to fetch neighbors of.
     * @return          The neighbors of the provided vertex
     */
    public Set<Integer> neighborsOf(int vertex) {
        return edges.get(vertex);
    }
}

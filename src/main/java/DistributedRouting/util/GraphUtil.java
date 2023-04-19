package DistributedRouting.util;

public class GraphUtil {
    public static String edgeLabel(String start, String end) {
        if (start.compareTo(end) > 0) {
            return String.format("(%s,%s)", end, start);
        } else {
            return String.format("(%s,%s)", start, end);
        }
    }

    public static String edgeLabel(int start, int end) {
        return edgeLabel(String.valueOf(start), String.valueOf(end));
    }
}

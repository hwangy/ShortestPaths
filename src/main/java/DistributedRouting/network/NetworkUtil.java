package DistributedRouting.network;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Network-related utility methods.
 */
public class NetworkUtil {
    /**
     * Attempts to get the local IP address
     * @return                          The string IP address
     * @throws UnknownHostException     Thrown if no network device is detected.
     */
    public static String getLocalIPAddress() throws UnknownHostException {
        return InetAddress.getLocalHost().getHostAddress();
    }
}

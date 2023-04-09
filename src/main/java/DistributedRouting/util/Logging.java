package DistributedRouting.util;

/**
 * Helper methods for wrapping the different kinds of messages
 * to be printed on the console, both on the Server and Client
 * side.
 */
public class Logging {
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_RESET = "\u001B[0m";

    /**
     * Logs a message giving unrequested information
     * about the status of either the server or client.
     * @param toLog     The message to log.
     */
    public static void logInfo(String toLog) {
        System.out.printf("[INFO]\t%s\n", toLog);
    }

    /**
     * Logs a debugging message. Should only be used temporary
     * for debugging and eventually removed.
     * @param toLog     Debug message to log.
     */
    public static void logDebug(String toLog) {
        System.out.printf("[DEBUG]\t%s\n", toLog);
    }

    /**
     * Logs information that is either requested (e.g. a list of
     * accounts) or part of the specification of the service
     * (e.g. an immediately delivered message).
     * @param toLog     Message to log.
     */
    public static void logService(String toLog) {
        System.out.printf("[SERVICE] %s\n", toLog);
    }

    public static void logError(String toLog) {
        System.out.printf(ANSI_RED + "[ERROR] %s" + ANSI_RESET + "\n", toLog);
    }
}

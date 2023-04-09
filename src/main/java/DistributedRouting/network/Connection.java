package DistributedRouting.network;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * Manages an input / output DataStream connection. This is designed
 * to be modular so that it can be easily mocked for integration tests.
 */
public class Connection {
    private final Socket clientSocket;
    private final DataInputStream inputStream;
    private final DataOutputStream outputStream;

    /**
     * Generates a Connection object from a socket.
     *
     * @param socket        Server socket to connect to.
     * @throws IOException  Handles network exception
     */
    public Connection(Socket socket) throws IOException {
        this.clientSocket = socket;
        this.inputStream = new DataInputStream(socket.getInputStream());
        this.outputStream = new DataOutputStream(socket.getOutputStream());
    }

    /**
     * Cleans up connections.
     *
     * @throws IOException  Handles network exception
     */
    public void close() throws IOException {
        if (inputStream != null) {
            outputStream.flush();
            outputStream.close();
        }
        if (outputStream != null) inputStream.close();
        clientSocket.close();
    }

    /**
     * Reads an integer from the stream.
     * @return              The read integer.
     * @throws IOException  Thrown on network exception
     */
    public int readInt() throws IOException {
        return inputStream.readInt();
    }

    /**
     * Writes an integer to the stream.
     * @param toSend        Integer to send over network
     * @throws IOException  Thrown on network exception
     */
    public void writeInt(int toSend) throws IOException {
        outputStream.writeInt(toSend);
    }

    /**
     * Reads a Long from the stream.
     * @return              The read Long.
     * @throws IOException  Thrown on network exception
     */
    public Long readLong() throws IOException {
        return inputStream.readLong();
    }

    /**
     * Writes an Long to the stream.
     * @param toSend        Long to send over network
     * @throws IOException  Thrown on network exception
     */
    public void writeLong(Long toSend) throws IOException {
        outputStream.writeLong(toSend);
    }

    /**
     * Reads a Java String from the input connection, using the UTF
     * format.
     *
     * @return                  A string read from input connection.
     * @throws  IOException     Thrown on network exception.
     */
    public String readString() throws IOException {
        return inputStream.readUTF();
    }

    /**
     * Writes an String to the stream.
     * @param toSend        String to send over network
     * @throws IOException  Thrown on network exception
     */
    public void writeString(String toSend) throws IOException {
        outputStream.writeUTF(toSend);
    }

    /**
     * Flush any message yet to be sent.
     * @throws IOException  Thrown on network exception
     */
    public void flushOutput() throws IOException {
        outputStream.flush();
    }

}

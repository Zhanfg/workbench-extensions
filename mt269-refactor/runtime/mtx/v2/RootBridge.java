package mtx.v2;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

/** Android 17 root-helper accept loop: poll timeout is not a fatal handshake failure. */
public final class RootBridge {
    private RootBridge() {}

    public static Socket accept(ServerSocket server) throws IOException {
        for (;;) {
            if (server == null || server.isClosed()) {
                throw new SocketException("root bridge closed");
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new SocketException("root bridge interrupted");
            }
            try {
                return server.accept();
            } catch (SocketTimeoutException poll) {
                // Keep polling. The legacy owner thread provides the overall handshake deadline.
                if (server.isClosed() || Thread.currentThread().isInterrupted()) {
                    throw poll;
                }
            }
        }
    }
}

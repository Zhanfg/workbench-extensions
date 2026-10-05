package mt269.refactor;

import java.net.ServerSocket;
import java.net.Socket;
import mtx.v2.PanelBridge;
import mtx.v2.RootBridge;

public final class RuntimeBridgeRegression {
    public static void main(String[] args) throws Exception {
        latestRequestWins();
        socketPollTimeoutIsNotFatal();
        System.out.println("MT269 runtime bridge regression: PASS");
    }

    private static void latestRequestWins() {
        Object pane = new Object();
        Object first = new Object();
        Object second = new Object();

        PanelBridge.register(pane, first);
        check(PanelBridge.isCurrent(pane, first), "first request not registered");

        PanelBridge.register(pane, second);
        check(!PanelBridge.isCurrent(pane, first), "stale request still current");
        check(PanelBridge.isCurrent(pane, second), "latest request rejected");
    }

    private static void socketPollTimeoutIsNotFatal() throws Exception {
        ServerSocket server = new ServerSocket(0);
        server.setSoTimeout(100);

        Thread client = new Thread(() -> {
            try {
                Thread.sleep(650);
                new Socket("127.0.0.1", server.getLocalPort()).close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        client.start();

        long start = System.nanoTime();
        Socket accepted = RootBridge.accept(server);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        accepted.close();
        server.close();
        client.join();

        check(elapsedMs >= 500, "accept returned before multiple SO_TIMEOUT polls elapsed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

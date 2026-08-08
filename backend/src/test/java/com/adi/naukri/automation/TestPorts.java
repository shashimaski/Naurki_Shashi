package com.adi.naukri.automation;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;

/**
 * Test utility for allocating a free TCP port and waiting until a server is listening.
 *
 * Author: Adikarthik Gupta C B
 */
public final class TestPorts {

    private TestPorts() {}

    /**
     * Allocates a free OS port by binding a temporary server socket.
     *
     * @return an available port number.
     * @throws IOException if no port could be allocated.
     */
    public static int free() throws IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            ss.setReuseAddress(true);
            return ss.getLocalPort();
        }
    }

    /**
     * Polls a TCP port until it is accepting connections or the timeout expires.
     *
     * @param port    port to probe on {@code 127.0.0.1}.
     * @param timeout maximum time to wait.
     * @throws RuntimeException if the port did not open within the timeout.
     */
    public static void waitUntilOpen(int port, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            try (Socket s = new Socket("127.0.0.1", port)) {
                return; // connected — port is open
            } catch (IOException ignored) {
                // not yet open
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for port " + port, ie);
            }
        }
        throw new RuntimeException("Port " + port + " did not open within " + timeout);
    }
}

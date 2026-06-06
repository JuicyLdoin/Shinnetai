package net.ldoin.shinnetai;

import net.ldoin.shinnetai.client.ShinnetaiClient;
import net.ldoin.shinnetai.client.options.ClientOptions;
import net.ldoin.shinnetai.server.ShinnetaiServer;
import net.ldoin.shinnetai.server.options.ServerOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ShinnetaiClientTest {

    @Test
    void start() throws IOException {
        ShinnetaiServer<?> server = new ShinnetaiServer<>(ServerOptions.of(5555));
        server.start();

        ShinnetaiClient client = new ShinnetaiClient(ClientOptions.of("localhost", 5555));
        client.start();
    }
}
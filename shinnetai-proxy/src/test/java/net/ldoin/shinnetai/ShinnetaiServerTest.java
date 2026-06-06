package net.ldoin.shinnetai;

import net.ldoin.shinnetai.server.ShinnetaiServer;
import net.ldoin.shinnetai.server.options.ServerOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ShinnetaiServerTest {

    @Test
    void start() {
        ShinnetaiServer<?> server = new ShinnetaiServer<>(ServerOptions.of(5555));
        server.start();
    }
}
package net.ldoin.shinnetai;

import net.ldoin.shinnetai.client.ShinnetaiClient;
import net.ldoin.shinnetai.client.options.ClientOptions;
import net.ldoin.shinnetai.packet.registry.PacketRegistry;
import net.ldoin.shinnetai.packet.test.TestCounterPacket;
import net.ldoin.shinnetai.server.ShinnetaiServer;
import net.ldoin.shinnetai.server.options.ServerOptions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShinnetaiSecurityTest {

    private static final int BASE_PORT = 8710;

    @Test
    void packetAuthorizerRejectsBeforeHandler() throws Exception {
        PacketRegistry registry = new PacketRegistry().withCommons().register(TestCounterPacket.class);
        CountDownLatch handled = new CountDownLatch(1);

        ShinnetaiServer<?> server = new ShinnetaiServer<>(registry,
                ServerOptions.builder(BASE_PORT)
                        .packetAuthorizer((auth, packet, ctx) -> !(packet instanceof TestCounterPacket))
                        .build());
        server.on(TestCounterPacket.class, (packet, ctx) -> handled.countDown());

        ShinnetaiClient client = null;
        try {
            server.start();
            client = new ShinnetaiClient(registry, ClientOptions.builder("localhost", BASE_PORT).build());
            client.start();
            client.sendPacket(new TestCounterPacket(1));

            assertFalse(handled.await(700, TimeUnit.MILLISECONDS));
        } finally {
            if (client != null) {
                client.close();
            }
            server.close();
        }
    }

    @Test
    void sessionTokensCanRequireTls() {
        assertThrows(IllegalStateException.class, () -> ServerOptions.builder(BASE_PORT + 1)
                .requireSessionToken(true)
                .sessionTokenValidator(token -> true)
                .requireTlsForSessionTokens(true)
                .build());
    }

    @Test
    void sessionTokensRequireValidatorUnlessExplicitlyAllowed() {
        assertThrows(IllegalStateException.class, () -> ServerOptions.builder(BASE_PORT + 2)
                .requireSessionToken(true)
                .build());
    }
}

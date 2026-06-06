package net.ldoin.shinnetai;

import net.ldoin.shinnetai.client.ShinnetaiClient;
import net.ldoin.shinnetai.client.options.ClientOptions;
import net.ldoin.shinnetai.exception.ShinnetaiExceptions;
import net.ldoin.shinnetai.packet.common.ExceptionPacket;
import net.ldoin.shinnetai.server.ShinnetaiServer;
import net.ldoin.shinnetai.server.options.ServerOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ShinnetaiNetworkTest {

    private static final int PLAIN_PORT = 5555;
    private static final int SSL_PORT = 5556;

    private static final Path KEYSTORE = Path.of("src/test/resources/testkeystore.jks");
    private static final char[] STORE_PASS = "password".toCharArray();
    private static final char[] KEY_PASS = "password".toCharArray();

    @Test
    @DisplayName(value = "without-ssl")
    void testPlainSocketConnection() throws Exception {
        ShinnetaiServer<?> server = new ShinnetaiServer<>(ServerOptions.builder(PLAIN_PORT)
                .ssl(false)
                .build());
        server.start();

        ShinnetaiClient client = new ShinnetaiClient(ClientOptions.builder("localhost", PLAIN_PORT)
                .ssl(false)
                .build());
        client.start();

        client.addPacket(new ExceptionPacket(ShinnetaiExceptions.FAILED_READ_PACKET, 1));
    }

    @Test
    @DisplayName(value = "with-ssl")
    void testSslSocketConnection() throws Exception {
        ShinnetaiServer<?> server = new ShinnetaiServer<>(ServerOptions.builder(SSL_PORT)
                .ssl(true)
                .sslKeystore(KEYSTORE)
                .sslKeystorePassword(STORE_PASS)
                .sslKeyPassword(KEY_PASS)
                .build());
        server.start();

        ShinnetaiClient client = new ShinnetaiClient(ClientOptions.builder("localhost", SSL_PORT)
                .ssl(true)
                .sslKeystore(KEYSTORE)
                .sslKeystorePassword(STORE_PASS)
                .sslKeyPassword(KEY_PASS)
                .build());
        client.start();

        client.addPacket(new ExceptionPacket(ShinnetaiExceptions.FAILED_READ_PACKET, 1));
    }
}

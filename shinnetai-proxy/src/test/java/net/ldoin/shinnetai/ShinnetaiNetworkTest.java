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

import java.io.File;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ShinnetaiNetworkTest {

    private static final int PLAIN_PORT = 5555;
    private static final int SSL_PORT = 5556;

    private static final File KEYSTORE = new File("src/test/resources/testkeystore.jks");
    private static final String STORE_PASS = "password";
    private static final String KEY_PASS = "password";

    @Test
    @DisplayName(value = "without-ssl")
    void testPlainSocketConnection() throws Exception {
        ShinnetaiServer<?> server = new ShinnetaiServer<>(ServerOptions.builder(PLAIN_PORT)
                .setSSL(false)
                .build());
        server.start();

        ShinnetaiClient client = new ShinnetaiClient(ClientOptions.builder("localhost", PLAIN_PORT)
                .setSSL(false)
                .build());
        client.start();

        client.addPacket(new ExceptionPacket(ShinnetaiExceptions.FAILED_READ_PACKET, 1));
    }

    @Test
    @DisplayName(value = "with-ssl")
    void testSslSocketConnection() throws Exception {
        ShinnetaiServer<?> server = new ShinnetaiServer<>(ServerOptions.builder(SSL_PORT)
                .setSSL(true)
                .setSSLKeystore(KEYSTORE)
                .setSSLKeystorePassword(STORE_PASS)
                .setSSLKeyPassword(KEY_PASS)
                .build());
        server.start();

        ShinnetaiClient client = new ShinnetaiClient(ClientOptions.builder("localhost", SSL_PORT)
                .setSSL(true)
                .setSSLKeystore(KEYSTORE)
                .setSSLKeystorePassword(STORE_PASS)
                .setSSLKeyPassword(KEY_PASS)
                .build());
        client.start();

        client.addPacket(new ExceptionPacket(ShinnetaiExceptions.FAILED_READ_PACKET, 1));
    }
}

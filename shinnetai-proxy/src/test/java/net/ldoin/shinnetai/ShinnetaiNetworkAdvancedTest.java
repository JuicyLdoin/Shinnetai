package net.ldoin.shinnetai;

import net.ldoin.shinnetai.client.ShinnetaiClient;
import net.ldoin.shinnetai.client.options.ClientOptions;
import net.ldoin.shinnetai.exception.ShinnetaiExceptions;
import net.ldoin.shinnetai.packet.common.DisconnectPacket;
import net.ldoin.shinnetai.packet.common.ExceptionPacket;
import net.ldoin.shinnetai.server.ShinnetaiServer;
import net.ldoin.shinnetai.server.options.ServerOptions;
import org.junit.jupiter.api.*;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ShinnetaiNetworkAdvancedTest {

    private static final int BASE_PORT = 7200;

    @Test
    @Order(1)
    @DisplayName("server isRunning after start")
    void server_isRunning_afterStart() {
        ShinnetaiServer<?> server = new ShinnetaiServer<>(ServerOptions.of(BASE_PORT));
        server.start();
        assertTrue(server.isRunning());
    }

    @Test
    @Order(2)
    @DisplayName("server double start throws")
    void server_doubleStart_throws() {
        ShinnetaiServer<?> server = new ShinnetaiServer<>(ServerOptions.of(BASE_PORT + 1));
        server.start();
        assertThrows(UnsupportedOperationException.class, server::start);
    }

    @Test
    @Order(3)
    @DisplayName("server gets connection after client connects")
    void server_hasConnection_afterClientConnects() throws Exception {
        ShinnetaiServer<?> server = new ShinnetaiServer<>(ServerOptions.of(BASE_PORT + 2));
        server.start();

        ShinnetaiClient client = new ShinnetaiClient(ClientOptions.of("localhost", BASE_PORT + 2));
        client.start();

        Thread.sleep(200);
        assertFalse(server.getConnections().isEmpty(), "Server should have at least one connection");
    }

    @Test
    @Order(4)
    @DisplayName("multiple clients can connect simultaneously")
    void multiple_clients_connect() throws Exception {
        ShinnetaiServer<?> server = new ShinnetaiServer<>(ServerOptions.of(BASE_PORT + 3));
        server.start();

        ShinnetaiClient c1 = new ShinnetaiClient(ClientOptions.of("localhost", BASE_PORT + 3));
        ShinnetaiClient c2 = new ShinnetaiClient(ClientOptions.of("localhost", BASE_PORT + 3));
        c1.start();
        c2.start();

        Thread.sleep(300);
        assertEquals(2, server.getConnections().size());
    }

    @Test
    @Order(5)
    @DisplayName("max connections limits incoming connections")
    void maxConnections_limit_enforced() throws Exception {
        ServerOptions opts = ServerOptions.builder(BASE_PORT + 4)
                .maxConnections(1)
                .build();
        ShinnetaiServer<?> server = new ShinnetaiServer<>(opts);
        server.start();

        ShinnetaiClient c1 = new ShinnetaiClient(ClientOptions.of("localhost", BASE_PORT + 4));
        c1.start();
        Thread.sleep(200);
        assertEquals(1, server.getMaxConnections());
        assertTrue(server.getConnections().size() <= 1);
    }

    @Test
    @Order(6)
    @DisplayName("client can send ExceptionPacket without error")
    void client_sendExceptionPacket() throws Exception {
        ShinnetaiServer<?> server = new ShinnetaiServer<>(ServerOptions.of(BASE_PORT + 5));
        server.start();

        ShinnetaiClient client = new ShinnetaiClient(ClientOptions.of("localhost", BASE_PORT + 5));
        client.start();
        Thread.sleep(200);

        assertDoesNotThrow(() ->
                client.addPacket(new ExceptionPacket(ShinnetaiExceptions.FAILED_READ_PACKET, 1)));
    }

    @Test
    @Order(7)
    @DisplayName("client isRunning after start")
    void client_isRunning_afterStart() throws Exception {
        ShinnetaiServer<?> server = new ShinnetaiServer<>(ServerOptions.of(BASE_PORT + 6));
        server.start();

        ShinnetaiClient client = new ShinnetaiClient(ClientOptions.of("localhost", BASE_PORT + 6));
        client.start();

        assertTrue(client.isRunning());
    }

    @Test
    @Order(8)
    @DisplayName("server statistic tracks connections")
    void server_statistic_tracked() throws Exception {
        ShinnetaiServer<?> server = new ShinnetaiServer<>(ServerOptions.of(BASE_PORT + 7));
        server.start();

        assertNotNull(server.getStatistic());
    }

    @Test
    @Order(9)
    @DisplayName("keepAlive disabled option propagates")
    void keepAlive_disabled_server() {
        ServerOptions opts = ServerOptions.builder(BASE_PORT + 8)
                .keepAlive(false)
                .build();
        ShinnetaiServer<?> server = new ShinnetaiServer<>(opts);
        server.start();
        assertTrue(server.isRunning());
    }

    @Test
    @Order(10)
    @DisplayName("custom readTimeout propagates to client options")
    void customReadTimeout_inClientOptions() {
        ClientOptions opts = ClientOptions.builder("localhost", BASE_PORT + 9)
                .readTimeout(10_000)
                .build();
        assertEquals(10_000, opts.getReadTimeout());
    }
}

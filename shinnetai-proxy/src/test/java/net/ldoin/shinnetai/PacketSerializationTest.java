package net.ldoin.shinnetai;

import net.ldoin.shinnetai.buffered.buf.smart.SmartByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.packet.common.DisconnectPacket;
import net.ldoin.shinnetai.packet.common.ExceptionPacket;
import net.ldoin.shinnetai.packet.common.HandshakePacket;
import net.ldoin.shinnetai.exception.ShinnetaiExceptions;
import net.ldoin.shinnetai.protocol.ShinnetaiProtocol;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PacketSerializationTest {

    @Test
    void handshakePacket_writeRead_roundtrip() {
        HandshakePacket original = new HandshakePacket(3, 0xCAFEBABE);

        WriteOnlySmartByteBuf writeBuf = WriteOnlySmartByteBuf.empty();
        original.write(writeBuf);

        ReadOnlySmartByteBuf readBuf = ReadOnlySmartByteBuf.of(writeBuf.toBytes());
        HandshakePacket restored = new HandshakePacket();
        restored.read(readBuf);

        assertEquals(3, restored.getProtocolVersion());
        assertEquals(0xCAFEBABE, restored.getMagic());
    }

    @Test
    void handshakePacket_defaultZeroValues() {
        HandshakePacket p = new HandshakePacket(0, 0);
        WriteOnlySmartByteBuf writeBuf = WriteOnlySmartByteBuf.empty();
        p.write(writeBuf);

        ReadOnlySmartByteBuf readBuf = ReadOnlySmartByteBuf.of(writeBuf.toBytes());
        HandshakePacket restored = new HandshakePacket();
        restored.read(readBuf);

        assertEquals(0, restored.getProtocolVersion());
        assertEquals(0, restored.getMagic());
    }

    @Test
    void handshakePacket_knownFixtureBytes() {
        HandshakePacket packet = new HandshakePacket(
                ShinnetaiProtocol.VERSION,
                ShinnetaiProtocol.DEFAULT_MAGIC,
                0L,
                null,
                0L);

        WriteOnlySmartByteBuf writeBuf = WriteOnlySmartByteBuf.empty();
        packet.write(writeBuf);

        assertArrayEquals(new byte[]{
                0x01,
                (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE,
                0x00,
                0x00,
                0x00
        }, writeBuf.toBytes());
    }

    @Test
    void handshakePacket_oldFixtureWithoutOptionalFieldsStillReads() {
        ReadOnlySmartByteBuf readBuf = ReadOnlySmartByteBuf.of(new byte[]{
                0x01,
                (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE
        });
        HandshakePacket restored = new HandshakePacket();
        restored.read(readBuf);

        assertEquals(ShinnetaiProtocol.VERSION, restored.getProtocolVersion());
        assertEquals(ShinnetaiProtocol.DEFAULT_MAGIC, restored.getMagic());
        assertEquals(0L, restored.getFeatureFlags());
        assertNull(restored.getSessionToken());
        assertEquals(0L, restored.getHandshakeTimestamp());
    }

    @Test
    void disconnectPacket_writeRead_roundtrip() {
        DisconnectPacket original = new DisconnectPacket(42);

        WriteOnlySmartByteBuf writeBuf = WriteOnlySmartByteBuf.empty();
        original.write(writeBuf);

        ReadOnlySmartByteBuf readBuf = ReadOnlySmartByteBuf.of(writeBuf.toBytes());
        DisconnectPacket restored = new DisconnectPacket();
        restored.read(readBuf);

        assertEquals(42, restored.getConnectionId());
    }

    @Test
    void disconnectPacket_defaultConnectionId_zero() {
        DisconnectPacket p = new DisconnectPacket();
        WriteOnlySmartByteBuf writeBuf = WriteOnlySmartByteBuf.empty();
        p.write(writeBuf);

        ReadOnlySmartByteBuf readBuf = ReadOnlySmartByteBuf.of(writeBuf.toBytes());
        DisconnectPacket restored = new DisconnectPacket();
        restored.read(readBuf);

        assertEquals(0, restored.getConnectionId());
    }

    @Test
    void exceptionPacket_writeRead_roundtrip() {
        ExceptionPacket original = new ExceptionPacket(ShinnetaiExceptions.FAILED_READ_PACKET, 7);

        WriteOnlySmartByteBuf writeBuf = WriteOnlySmartByteBuf.empty();
        original.write(writeBuf);

        ReadOnlySmartByteBuf readBuf = ReadOnlySmartByteBuf.of(writeBuf.toBytes());
        ExceptionPacket restored = new ExceptionPacket();
        restored.read(readBuf);

        assertNotNull(restored);
    }
}

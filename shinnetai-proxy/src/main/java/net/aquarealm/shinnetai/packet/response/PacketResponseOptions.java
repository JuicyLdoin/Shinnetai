package net.aquarealm.shinnetai.packet.response;

import net.aquarealm.shinnetai.buffered.buf.smart.SmartByteBuf;

public class PacketResponseOptions {

    public static PacketResponseOptions empty() {
        return new PacketResponseOptions(0, 0);
    }

    public static PacketResponseOptions waitResponse(int response) {
        return new PacketResponseOptions(response, 0);
    }

    public static PacketResponseOptions response(int response) {
        return new PacketResponseOptions(0, response);
    }

    public static PacketResponseOptions of(SmartByteBuf buf) {
        return new PacketResponseOptions(buf);
    }

    private final int waitResponse;
    private final int response;

    protected PacketResponseOptions(int waitResponse, int response) {
        this.waitResponse = waitResponse;
        this.response = response;
    }

    protected PacketResponseOptions(SmartByteBuf buf) {
        byte data = buf.readByte();

        this.waitResponse = data & 0x0F;
        this.response = (data >> 4) & 0x0F;
    }

    public boolean isWaitResponse() {
        return waitResponse != 0;
    }

    public int getWaitResponse() {
        return waitResponse;
    }

    public boolean isResponse() {
        return response != 0;
    }

    public int getResponseId() {
        return response;
    }

    public void write(SmartByteBuf buf) {
        buf.writeByte((byte) ((Math.max(waitResponse, 0) & 0xFF) | ((Math.max(response, 0) & 0xFF) << 4)));
    }
}
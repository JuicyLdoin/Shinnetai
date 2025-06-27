package net.ldoin.shinnetai.packet.common;

import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.ldoin.shinnetai.client.ShinnetaiClient;
import net.ldoin.shinnetai.packet.registry.ShinnetaiPacket;
import net.ldoin.shinnetai.packet.side.ClientSidePacket;
import net.ldoin.shinnetai.worker.ShinnetaiIOWorker;

@ShinnetaiPacket(id = -3)
public class ServerDisablePacket extends ClientSidePacket<ShinnetaiClient> {

    public ServerDisablePacket() {
    }

    @Override
    public void attachServerWorker(ShinnetaiIOWorker<?> serverWorker) {
    }

    @Override
    public void handleClient() {
        getClientWorker().closeClient(true);
        getClientWorker().getLogger().info("Disconnect: Server disabled!");
    }

    @Override
    public void read(ReadOnlySmartByteBuf buf) {
    }

    @Override
    public void write(WriteOnlySmartByteBuf buf) {
    }
}
package net.aquarealm.shinnetai.packet.common;

import net.aquarealm.shinnetai.ShinnetaiIOWorker;
import net.aquarealm.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.aquarealm.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.aquarealm.shinnetai.client.ShinnetaiClient;
import net.aquarealm.shinnetai.packet.registry.ShinnetaiPacket;
import net.aquarealm.shinnetai.packet.side.ClientSidePacket;

@ShinnetaiPacket(id = 2)
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
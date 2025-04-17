package net.aquarealm.shinnetai.packet;

import net.aquarealm.shinnetai.ShinnetaiIOWorker;
import net.aquarealm.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.aquarealm.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.aquarealm.shinnetai.packet.side.PacketSide;

public abstract class AbstractPacket<C extends ShinnetaiIOWorker<?>, S extends ShinnetaiIOWorker<?>> {

    private C clientWorker;
    private S serverWorker;

    public void attachClientWorker(C clientWorker) {
        this.clientWorker = clientWorker;
    }

    public void attachServerWorker(S serverWorker) {
        this.serverWorker = serverWorker;
    }

    public C getClientWorker() {
        return clientWorker;
    }

    public S getServerWorker() {
        return serverWorker;
    }

    public PacketSide getHandleSide() {
        return PacketSide.MULTIPLE;
    }

    public void handle() {
    }

    public final void handle(PacketSide side) {
        if (!getHandleSide().canHandle(side)) {
            return;
        }

        switch (side) {
            case CLIENT -> handleClient();
            case SERVER -> handleServer();
            case MULTIPLE -> handle();
        }
    }

    public void handleClient() {
    }

    public void handleServer() {
    }

    public <P extends AbstractPacket<?, ?>> P response() {
        return null;
    }

    public abstract void read(ReadOnlySmartByteBuf buf);

    public abstract void write(WriteOnlySmartByteBuf buf);
}
package net.aquarealm.shinnetai.packet.side;

public enum PacketSide {

    MULTIPLE(true),
    SERVER(false),
    CLIENT(false);

    private final boolean canHandleOthers;

    PacketSide(boolean canHandleOthers) {
        this.canHandleOthers = canHandleOthers;
    }

    public boolean canHandle(PacketSide side) {
        return canHandleOthers || side == this;
    }
}
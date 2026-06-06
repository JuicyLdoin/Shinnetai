package net.ldoin.shinnetai.protocol;

public final class ShinnetaiProtocol {

    public static final int VERSION = 1;
    public static final int DEFAULT_MAGIC = 0xCAFEBABE;
    public static final int DEFAULT_MAX_FRAME_SIZE = 64 * 1024;
    public static final int ABSOLUTE_MAX_FRAME_SIZE = 16 * 1024 * 1024;

    private ShinnetaiProtocol() {
    }
}
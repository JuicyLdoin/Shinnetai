package net.ldoin.shinnetai.metric;

public final class ShinnetaiRuntimeMetrics {

    private final long timestampMs;
    private final long uptimeMs;
    private final double processCpuLoad;
    private final double systemCpuLoad;
    private final long totalPhysicalMemoryBytes;
    private final long freePhysicalMemoryBytes;
    private final long usedPhysicalMemoryBytes;
    private final long heapUsedBytes;
    private final long heapMaxBytes;
    private final int liveThreads;
    private final long appInputBytes;
    private final long appOutputBytes;
    private final int appInputPackets;
    private final int appOutputPackets;

    public ShinnetaiRuntimeMetrics(long timestampMs,
                                   long uptimeMs,
                                   double processCpuLoad,
                                   double systemCpuLoad,
                                   long totalPhysicalMemoryBytes,
                                   long freePhysicalMemoryBytes,
                                   long usedPhysicalMemoryBytes,
                                   long heapUsedBytes,
                                   long heapMaxBytes,
                                   int liveThreads,
                                   long appInputBytes,
                                   long appOutputBytes,
                                   int appInputPackets,
                                   int appOutputPackets) {
        this.timestampMs = timestampMs;
        this.uptimeMs = uptimeMs;
        this.processCpuLoad = processCpuLoad;
        this.systemCpuLoad = systemCpuLoad;
        this.totalPhysicalMemoryBytes = totalPhysicalMemoryBytes;
        this.freePhysicalMemoryBytes = freePhysicalMemoryBytes;
        this.usedPhysicalMemoryBytes = usedPhysicalMemoryBytes;
        this.heapUsedBytes = heapUsedBytes;
        this.heapMaxBytes = heapMaxBytes;
        this.liveThreads = liveThreads;
        this.appInputBytes = appInputBytes;
        this.appOutputBytes = appOutputBytes;
        this.appInputPackets = appInputPackets;
        this.appOutputPackets = appOutputPackets;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public long getUptimeMs() {
        return uptimeMs;
    }

    public double getProcessCpuLoad() {
        return processCpuLoad;
    }

    public double getSystemCpuLoad() {
        return systemCpuLoad;
    }

    public long getTotalPhysicalMemoryBytes() {
        return totalPhysicalMemoryBytes;
    }

    public long getFreePhysicalMemoryBytes() {
        return freePhysicalMemoryBytes;
    }

    public long getUsedPhysicalMemoryBytes() {
        return usedPhysicalMemoryBytes;
    }

    public long getHeapUsedBytes() {
        return heapUsedBytes;
    }

    public long getHeapMaxBytes() {
        return heapMaxBytes;
    }

    public int getLiveThreads() {
        return liveThreads;
    }

    public long getAppInputBytes() {
        return appInputBytes;
    }

    public long getAppOutputBytes() {
        return appOutputBytes;
    }

    public int getAppInputPackets() {
        return appInputPackets;
    }

    public int getAppOutputPackets() {
        return appOutputPackets;
    }
}
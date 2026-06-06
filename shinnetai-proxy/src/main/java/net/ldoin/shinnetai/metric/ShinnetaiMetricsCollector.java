package net.ldoin.shinnetai.metric;

import com.sun.management.OperatingSystemMXBean;
import net.ldoin.shinnetai.statistic.ShinnetaiStatistic;

import java.lang.management.ManagementFactory;

public final class ShinnetaiMetricsCollector {

    private ShinnetaiMetricsCollector() {
    }

    public static ShinnetaiRuntimeMetrics collect(ShinnetaiStatistic statistic) {
        long now = System.currentTimeMillis();
        long uptimeMs = statistic != null ? statistic.getTimeAlive() : 0L;

        double processCpu = -1D;
        double systemCpu = -1D;
        long totalPhysical = -1L;
        long freePhysical = -1L;

        java.lang.management.OperatingSystemMXBean base = ManagementFactory.getOperatingSystemMXBean();
        if (base instanceof OperatingSystemMXBean osBean) {
            processCpu = normalize(osBean.getProcessCpuLoad());
            systemCpu = normalize(osBean.getCpuLoad());
            totalPhysical = osBean.getTotalMemorySize();
            freePhysical = osBean.getFreeMemorySize();
        }

        Runtime runtime = Runtime.getRuntime();
        long heapUsed = runtime.totalMemory() - runtime.freeMemory();
        long heapMax = runtime.maxMemory();

        long appInputBytes = statistic != null ? statistic.getInputBytes() : 0L;
        long appOutputBytes = statistic != null ? statistic.getOutputBytes() : 0L;
        int appInputPackets = statistic != null ? statistic.getInputPackets() : 0;
        int appOutputPackets = statistic != null ? statistic.getOutputPackets() : 0;

        return new ShinnetaiRuntimeMetrics(
                now,
                uptimeMs,
                processCpu,
                systemCpu,
                totalPhysical,
                freePhysical,
                totalPhysical >= 0 && freePhysical >= 0 ? totalPhysical - freePhysical : -1L,
                heapUsed,
                heapMax,
                Thread.activeCount(),
                appInputBytes,
                appOutputBytes,
                appInputPackets,
                appOutputPackets
        );
    }

    private static double normalize(double value) {
        if (value < 0D) {
            return value;
        }

        return Math.round(value * 10000D) / 100D;
    }
}
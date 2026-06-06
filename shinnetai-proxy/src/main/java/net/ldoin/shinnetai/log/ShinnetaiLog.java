package net.ldoin.shinnetai.log;

import net.ldoin.shinnetai.debug.TrafficLog;

import java.nio.file.Path;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class ShinnetaiLog {

    private static boolean initialized = false;
    private static volatile TrafficLog globalTrafficLog = null;

    public static void init() {
        if (initialized) {
            return;
        }

        ShinnetaiLogFormatter formatter = new ShinnetaiLogFormatter();
        Logger rootLogger = LogManager.getLogManager().getLogger("");
        for (Handler handler : rootLogger.getHandlers()) {
            handler.setFormatter(formatter);
        }

        initialized = true;
    }

    public static TrafficLog enableTrafficRecording(Path directory) {
        TrafficLog log = TrafficLog.toDirectory(directory);
        globalTrafficLog = log;
        return log;
    }

    public static TrafficLog getTrafficLog() {
        return globalTrafficLog;
    }

    public static void disableTrafficRecording() {
        globalTrafficLog = null;
    }
}
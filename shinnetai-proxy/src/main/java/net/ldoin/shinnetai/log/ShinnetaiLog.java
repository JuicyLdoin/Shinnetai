package net.ldoin.shinnetai.log;

import java.util.logging.*;

public class ShinnetaiLog {

    private static boolean initialized = false;

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
}
package net.ldoin.shinnetai.log;

import java.util.logging.*;

public class ShinnetaiLog {

    public static void init() {
        Logger rootLogger = LogManager.getLogManager().getLogger("");
        for (Handler handler : rootLogger.getHandlers()) {
            handler.setFormatter(new ShinnetaiLogFormatter());
        }
    }
}
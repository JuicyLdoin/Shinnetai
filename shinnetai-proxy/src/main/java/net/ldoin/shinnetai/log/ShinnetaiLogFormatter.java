package net.ldoin.shinnetai.log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class ShinnetaiLogFormatter extends Formatter {

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    public String format(LogRecord record) {
        String timestamp = dateFormat.format(new Date(record.getMillis()));
        String level = record.getLevel().getName();
        String loggerName = record.getLoggerName();

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(String.format("[%s] [%s] %s - %s%n", timestamp, level, loggerName, formatMessage(record)));

        Throwable thrown = record.getThrown();
        if (thrown != null) {
            appendThrowable(stringBuilder, thrown);
        }

        return stringBuilder.toString();
    }

    private static void appendThrowable(StringBuilder sb, Throwable t) {
        sb.append(t).append(System.lineSeparator());
        for (StackTraceElement element : t.getStackTrace()) {
            sb.append("\tat ").append(element).append(System.lineSeparator());
        }

        Throwable cause = t.getCause();
        if (cause != null) {
            sb.append("Caused by: ");
            appendThrowable(sb, cause);
        }
    }
}
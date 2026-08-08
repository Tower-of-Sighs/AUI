package com.sighs.apricityui.dev;

import com.sighs.apricityui.util.AuiLogging;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/** Bridges ApricityUI log events to the client-only DevTools console. */
public final class DevToolsLogBridge {
    private static final String APPENDER_NAME = "ApricityUI-DevToolsConsole";
    private static final int MAX_PENDING_LOGS = 2048;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final BlockingQueue<ConsoleLog> PENDING = new ArrayBlockingQueue<>(MAX_PENDING_LOGS);
    private static final Object INSTALL_LOCK = new Object();

    private DevToolsLogBridge() {
    }

    /** Installs an additional appender without changing the game's existing logging pipeline. */
    public static void install(org.slf4j.Logger sourceLogger) {
        if (sourceLogger == null) return;

        final String loggerName;
        try {
            loggerName = sourceLogger.getName();
        } catch (Throwable ignored) {
            return;
        }
        if (loggerName == null || loggerName.isBlank()) return;

        synchronized (INSTALL_LOCK) {
            try {
                LoggerContext context = (LoggerContext) LogManager.getContext(false);
                Appender attached = context.getConfiguration().getAppender(APPENDER_NAME);
                if (attached instanceof BridgeAppender) {
                    AuiLogging.attachPackageAppender(context, attached, null);
                    return;
                }
                if (attached != null) return;

                BridgeAppender appender = new BridgeAppender();
                appender.start();
                AuiLogging.attachPackageAppender(context, appender, null);
            } catch (Throwable ignored) {
                // Log4j's concrete backend is supplied by Minecraft. A missing or replaced
                // backend must never prevent the client from starting.
            }
        }
    }

    /** Drains pending events. This method is called only from the client thread. */
    public static List<ConsoleLog> drain() {
        return drain(Integer.MAX_VALUE);
    }

    /** Drains at most maxEntries events for one client tick. */
    public static List<ConsoleLog> drain(int maxEntries) {
        if (maxEntries <= 0) return List.of();
        ArrayList<ConsoleLog> drained = new ArrayList<>();
        PENDING.drainTo(drained, maxEntries);
        return drained;
    }

    private static void enqueue(LogEvent event) {
        if (event == null) return;
        try {
            String text = event.getMessage() == null ? "" : event.getMessage().getFormattedMessage();
            String stack = formatStack(event.getThrown());
            ConsoleLog log = new ConsoleLog(
                    level(event),
                    text == null ? "" : text,
                    source(event),
                    stack,
                    formatTime(event.getTimeMillis())
            );
            if (!PENDING.offer(log)) {
                PENDING.poll();
                PENDING.offer(log);
            }
        } catch (Throwable ignored) {
            // A logging mirror must not become a second source of logging failures.
        }
    }

    private static String level(LogEvent event) {
        String value = event.getLevel() == null
                ? "INFO" : event.getLevel().toString().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "ERROR", "FATAL" -> "error";
            case "WARN" -> "warn";
            default -> "info";
        };
    }

    private static String source(LogEvent event) {
        String loggerName = event.getLoggerName();
        return loggerName == null || loggerName.isBlank() ? "ApricityUI" : loggerName;
    }

    private static String formatTime(long timeMillis) {
        try {
            return LocalTime.ofInstant(Instant.ofEpochMilli(timeMillis), ZoneId.systemDefault())
                    .format(TIME_FORMAT);
        } catch (Throwable ignored) {
            return LocalTime.now().format(TIME_FORMAT);
        }
    }

    private static String formatStack(Throwable throwable) {
        if (throwable == null) return null;
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString().trim();
    }

    public record ConsoleLog(String level, String text, String source, String stack, String time) {
    }

    private static final class BridgeAppender extends AbstractAppender {
        private BridgeAppender() {
            super(APPENDER_NAME, null, null, true);
        }

        @Override
        public void append(LogEvent event) {
            enqueue(event);
        }
    }
}

package com.sighs.apricityui.dev;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.message.ReusableMessageFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Bridges ApricityUI log events to the client-only DevTools console.
 */
public final class DevToolsLogBridge {
    private static final String APPENDER_NAME = "ApricityUI-DevToolsConsole";
    private static final int MAX_PENDING_LOGS = 2048;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final BlockingQueue<ConsoleLog> PENDING = new ArrayBlockingQueue<>(MAX_PENDING_LOGS);
    private static final Object INSTALL_LOCK = new Object();

    private static Logger installedLogger;
    private static Appender installedAppender;

    private DevToolsLogBridge() {
    }

    /**
     * Installs an additional appender without changing the game's existing logging pipeline.
     */
    public static void install(org.slf4j.Logger sourceLogger) {
        if (sourceLogger == null) return;

        final String loggerName;
        try {
            loggerName = sourceLogger.getName();
        } catch (Throwable ignored) {
            return;
        }
        install(sourceLogger, loggerName);
    }

    private static void install(org.slf4j.Logger sourceLogger, String loggerName) {
        if (loggerName == null || loggerName.isBlank()) return;

        synchronized (INSTALL_LOCK) {
            try {
                Logger logger = resolveCoreLogger(sourceLogger, loggerName);
                if (logger == null) return;
                Appender attached = logger.getAppenders().get(APPENDER_NAME);
                if (attached instanceof BridgeAppender) {
                    installedLogger = logger;
                    installedAppender = attached;
                    return;
                }
                if (logger == installedLogger && installedAppender != null
                        && logger.getAppenders().containsValue(installedAppender)) {
                    return;
                }
                if (attached != null) return;

                BridgeAppender appender = new BridgeAppender();
                appender.start();
                logger.addAppender(appender);
                installedLogger = logger;
                installedAppender = appender;
            } catch (Throwable ignored) {
                // Log4j's concrete backend is supplied by Minecraft. A missing or replaced
                // backend must never prevent the client from starting.
            }
        }
    }

    private static Logger resolveCoreLogger(org.slf4j.Logger sourceLogger, String loggerName) {
        try {
            for (Class<?> type = sourceLogger.getClass(); type != null; type = type.getSuperclass()) {
                Field field = type.getDeclaredField("logger");
                field.setAccessible(true);
                Object delegate = field.get(sourceLogger);
                if (delegate instanceof Logger logger) return logger;
                break;
            }
        } catch (Throwable ignored) {
            // Fall back to the normal context lookup for other SLF4J providers.
        }
        try {
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            return context.getLogger(loggerName, ReusableMessageFactory.INSTANCE);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Drains pending events. This method is called only from the client thread.
     */
    public static List<ConsoleLog> drain() {
        return drain(Integer.MAX_VALUE);
    }

    /**
     * Drains at most maxEntries events for one client tick.
     */
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

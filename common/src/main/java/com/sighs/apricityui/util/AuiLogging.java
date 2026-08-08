package com.sighs.apricityui.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RandomAccessFileAppender;
import org.apache.logging.log4j.core.appender.RollingRandomAccessFileAppender;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.Serializable;
import java.util.Locale;

/** Configures the package-wide ApricityUI log outputs. */
public final class AuiLogging {
    public static final String LOGGER_NAMESPACE = "com.sighs.apricityui";

    private static final String APPENDER_NAME = "ApricityUI-File";
    private static final String FALLBACK_FILE_NAME = "logs/aui.log";
    private static final String FALLBACK_LAYOUT =
            "[%d{ddMMMyyyy HH:mm:ss.SSS}] [%t/%level] [%logger/%markerSimpleName]: %msg%n%xEx";
    private static final Object INSTALL_LOCK = new Object();

    private AuiLogging() {
    }

    /** Installs the current-run AUI log while preserving propagation to Minecraft's root loggers. */
    public static void installFileAppender() {
        try {
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            Configuration configuration = context.getConfiguration();
            RollingRandomAccessFileAppender latest = findLatestFileAppender(configuration);
            Layout<? extends Serializable> layout = latest == null ? null : latest.getLayout();
            String fileName = latest == null ? FALLBACK_FILE_NAME : auiFileName(latest.getFileName());
            Level level = latest == null ? Level.INFO : appenderLevel(configuration, latest.getName());
            installFileAppender(context, fileName, layout, level);
        } catch (Throwable ignored) {
            // Logging setup must never prevent the mod or a dedicated server from starting.
        }
    }

    static void installFileAppender(LoggerContext context, String fileName,
                                    Layout<? extends Serializable> layout, Level level) {
        if (context == null) return;
        synchronized (INSTALL_LOCK) {
            Configuration configuration = context.getConfiguration();
            Appender existing = configuration.getAppender(APPENDER_NAME);
            if (existing != null) {
                attachPackageAppender(context, existing, level);
                return;
            }

            Layout<? extends Serializable> effectiveLayout = layout == null
                    ? PatternLayout.newBuilder()
                    .withConfiguration(configuration)
                    .withPattern(FALLBACK_LAYOUT)
                    .build()
                    : layout;
            RandomAccessFileAppender appender = RandomAccessFileAppender.newBuilder()
                    .withName(APPENDER_NAME)
                    .withConfiguration(configuration)
                    .withLayout(effectiveLayout)
                    .setFileName(fileName == null || fileName.isBlank() ? FALLBACK_FILE_NAME : fileName)
                    .setAppend(false)
                    .build();
            if (appender == null) return;
            appender.start();
            attachPackageAppender(context, appender, level);
        }
    }

    /** Attaches an appender to all AUI loggers without disabling root propagation. */
    public static void attachPackageAppender(LoggerContext context, Appender appender, Level level) {
        if (context == null || appender == null) return;
        synchronized (INSTALL_LOCK) {
            Configuration configuration = context.getConfiguration();
            Appender registered = configuration.getAppender(appender.getName());
            if (registered == null) {
                if (!appender.isStarted()) appender.start();
                configuration.addAppender(appender);
                registered = appender;
            }

            LoggerConfig logger = configuration.getLoggerConfig(LOGGER_NAMESPACE);
            if (!LOGGER_NAMESPACE.equals(logger.getName())) {
                logger = new LoggerConfig(LOGGER_NAMESPACE, logger.getLevel(), true);
                configuration.addLogger(LOGGER_NAMESPACE, logger);
            }
            if (!logger.getAppenders().containsKey(registered.getName())) {
                logger.addAppender(registered, level, null);
            }
            context.updateLoggers();
        }
    }

    static String auiFileName(String latestFileName) {
        return siblingName(latestFileName, "aui.log", FALLBACK_FILE_NAME);
    }

    private static String siblingName(String source, String targetName, String fallback) {
        if (source == null || source.isBlank()) return fallback;
        int separator = Math.max(source.lastIndexOf('/'), source.lastIndexOf('\\'));
        return (separator < 0 ? "" : source.substring(0, separator + 1)) + targetName;
    }

    private static RollingRandomAccessFileAppender findLatestFileAppender(Configuration configuration) {
        Appender named = configuration.getAppender("File");
        if (named instanceof RollingRandomAccessFileAppender rolling && isLatestLog(rolling.getFileName())) {
            return rolling;
        }
        for (Appender appender : configuration.getAppenders().values()) {
            if (appender instanceof RollingRandomAccessFileAppender rolling && isLatestLog(rolling.getFileName())) {
                return rolling;
            }
        }
        return null;
    }

    private static boolean isLatestLog(String fileName) {
        if (fileName == null) return false;
        String normalized = fileName.replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalized.endsWith("/latest.log") || "latest.log".equals(normalized);
    }

    private static Level appenderLevel(Configuration configuration, String appenderName) {
        AppenderRef reference = appenderReference(configuration.getRootLogger(), appenderName);
        if (reference != null) return reference.getLevel();
        for (LoggerConfig logger : configuration.getLoggers().values()) {
            reference = appenderReference(logger, appenderName);
            if (reference != null) return reference.getLevel();
        }
        return Level.INFO;
    }

    private static AppenderRef appenderReference(LoggerConfig logger, String appenderName) {
        for (AppenderRef reference : logger.getAppenderRefs()) {
            if (reference.getRef().equals(appenderName)) return reference;
        }
        return null;
    }
}

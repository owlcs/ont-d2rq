package d2rq.utils;

import org.apache.jena.atlas.logging.LogCtl;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * All Log4j-specific stuff is encapsulated here.
 * <p>
 * Default configuration is in /etc/log4j.properties.
 * We always have to put that on the classpath so Log4j will find it.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class LogHelper {

    /**
     * Turns off any logging.
     */
    public static void turnLoggingOff() {
        forceDisableExternalLogging();
        org.apache.log4j.Logger.getRootLogger().setLevel(org.apache.log4j.Level.OFF);
        /*
        try {
            Class<?> logger = Class.forName("org.apache.log4j.Logger");
            Class<?> level = Class.forName("org.apache.log4j.Level");
            Object rootLogger = logger.getMethod("getRootLogger").invoke(null);
            Object off = level.getField("OFF").get(null);
            //noinspection JavaReflectionInvocation
            logger.getMethod("setLevel", level).invoke(rootLogger, off);
        } catch (Exception e) {
            throw new IllegalStateException("Can't turn off logging", e);
        }
        */
    }

    /**
     * Adjusts Log4j log level to show more stuff.
     */
    public static void setVerboseLogging() {
        /*
        org.apache.log4j.Logger.getLogger("d2rq").setLevel(org.apache.log4j.Level.INFO);
        org.apache.log4j.Logger.getLogger("de.fuberlin.wiwiss.d2rq").setLevel(org.apache.log4j.Level.INFO);
        org.apache.log4j.Logger.getLogger("org.eclipse.jetty").setLevel(org.apache.log4j.Level.INFO);
        org.apache.log4j.Logger.getLogger("org.joseki").setLevel(org.apache.log4j.Level.INFO);
        */
        setApplicationLogLevelTo("INFO");
        setSystemLogLevelTo("INFO");
    }

    /**
     * Adjusts Log4j log level to show MUCH more stuff.
     */
    public static void setDebugLogging() {
        /*
        org.apache.log4j.Logger.getLogger("d2rq").setLevel(org.apache.log4j.Level.ALL);
        org.apache.log4j.Logger.getLogger("de.fuberlin.wiwiss.d2rq").setLevel(org.apache.log4j.Level.ALL);
        org.apache.log4j.Logger.getLogger("org.eclipse.jetty").setLevel(org.apache.log4j.Level.INFO);
        org.apache.log4j.Logger.getLogger("org.joseki").setLevel(org.apache.log4j.Level.INFO);
        */
        setApplicationLogLevelTo("ALL");
        setSystemLogLevelTo("INFO");
    }

    private static void setApplicationLogLevelTo(String level) {
        LogCtl.setLevel("d2rq", level);
        LogCtl.setLevel("ru.avicomp.d2rq", level);
        LogCtl.setLevel("de.fuberlin.wiwiss.d2rq", level);
    }

    @SuppressWarnings("SameParameterValue")
    private static void setSystemLogLevelTo(String level) {
        LogCtl.setLevel("org.eclipse.jetty", level);
        LogCtl.setLevel("org.apache.jena.fuseki", level);
    }

    @SuppressWarnings({"unchecked", "JavaReflectionInvocation"})
    private static void forceDisableExternalLogging() {
        // somewhere from caffeine, etc:
        java.util.logging.LogManager.getLogManager().reset();
        try {
            // java9 hack:
            Class loggerClazz = Class.forName("jdk.internal.module.IllegalAccessLogger");
            Field logger = loggerClazz.getDeclaredField("logger");
            Class unsafeClass = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Object unsafeInstance = theUnsafe.get(null);
            Method staticFieldOffset = unsafeClass.getMethod("staticFieldOffset", Field.class);
            Method putObjectVolatile = unsafeClass.getMethod("putObjectVolatile", Object.class, Long.TYPE, Object.class);
            putObjectVolatile.invoke(unsafeInstance, loggerClazz, staticFieldOffset.invoke(unsafeInstance, logger), null);
        } catch (Exception e) {
            // ignore
        }
    }
}

package d2rq;

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
@SuppressWarnings("WeakerAccess")
public class LogHelper {

    public static void turnLoggingOff() {
        forceDisableExternalLogging();
        //Original:
        //org.apache.log4j.Logger.getRootLogger().setLevel(org.apache.log4j.Level.OFF);
        //Does not work, see description of org.apache.log4j.Logger.getRootLogger()
        //LogCtl.disable("root");
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

    }

    public static void setVerboseLogging() {
        // Adjust Log4j log level to show more stuff
        /*
        org.apache.log4j.Logger.getLogger("d2rq").setLevel(org.apache.log4j.Level.INFO);
        org.apache.log4j.Logger.getLogger("de.fuberlin.wiwiss.d2rq").setLevel(org.apache.log4j.Level.INFO);
        org.apache.log4j.Logger.getLogger("org.eclipse.jetty").setLevel(org.apache.log4j.Level.INFO);
        org.apache.log4j.Logger.getLogger("org.joseki").setLevel(org.apache.log4j.Level.INFO);
        */
        LogCtl.setLevel("d2rq", "INFO");
        LogCtl.setLevel("de.fuberlin.wiwiss.d2rq", "INFO");
        LogCtl.setLevel("org.eclipse.jetty", "INFO");
        LogCtl.setLevel("org.joseki", "INFO");
    }

    public static void setDebugLogging() {
        // Adjust Log4j log level to show MUCH more stuff
        /*
        org.apache.log4j.Logger.getLogger("d2rq").setLevel(org.apache.log4j.Level.ALL);
        org.apache.log4j.Logger.getLogger("de.fuberlin.wiwiss.d2rq").setLevel(org.apache.log4j.Level.ALL);
        org.apache.log4j.Logger.getLogger("org.eclipse.jetty").setLevel(org.apache.log4j.Level.INFO);
        org.apache.log4j.Logger.getLogger("org.joseki").setLevel(org.apache.log4j.Level.INFO);
        */
        LogCtl.setLevel("d2rq", "ALL");
        LogCtl.setLevel("de.fuberlin.wiwiss.d2rq", "ALL");
        LogCtl.setLevel("org.eclipse.jetty", "INFO");
        LogCtl.setLevel("org.joseki", "INFO");
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

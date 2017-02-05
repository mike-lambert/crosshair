package ru.cyberspacelabs.crosshair.util;

import ru.cyberspacelabs.crosshair.MasterServer;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Created by mike on 05.02.17.
 */
public class RuntimeUtil {
    public static final String CONFIG_CROSSHAIR = "crosshair.properties";
    public static final String PROPERTY_MASTER_PORT = "crosshair.port";
    public static final String PROPERTY_RECORD_LIFETIME = "crosshair.record.lifetime";
    public static final String PROPERTY_CLEANUP_INTERVAL = "crosshair.cleanup.interval";
    public static void log(String message){
        System.out.println(
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS ZZZ").format(new Date()) + " | " +
                Thread.currentThread().getName() + " : " + message
        );
    }

    public static void applyConfiguration(MasterServer server){
        try {
            Properties config = new Properties();
            config.load(new FileInputStream(CONFIG_CROSSHAIR));
            int port = Integer.parseInt(config.getProperty(PROPERTY_MASTER_PORT));
            long lifetime = Long.parseLong(config.getProperty(PROPERTY_RECORD_LIFETIME));
            long cleanup = Long.parseLong(config.getProperty(PROPERTY_CLEANUP_INTERVAL));
            server.setCleanupPeriod(cleanup);
            server.setExpireTimeout(lifetime);
            server.setPort(port);
            log("Configuring OK");
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public static String getAppVersion() {
        Class clazz = RuntimeUtil.class;
        String className = clazz.getSimpleName() + ".class";
        String classPath = clazz.getResource(className).toString();
        if (!classPath.startsWith("jar")) {
            // Class not from JAR
            return "[NOT IN JAR]";
        }
        String manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) +
                "/META-INF/MANIFEST.MF";
        Manifest manifest = null;
        try {
            manifest = new Manifest(new URL(manifestPath).openStream());
        } catch (IOException e) {
            e.printStackTrace();
            return "[UNKNOWN]";
        }
        Attributes attr = manifest.getMainAttributes();
        String value = attr.getValue("Implementation-Version");
        return value;
    }
}

package ru.cyberspacelabs.crosshair.util;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by mike on 05.02.17.
 */
public class RuntimeUtil {
    public static void log(String message){
        System.out.println(
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS ZZZ").format(new Date()) + " | " +
                Thread.currentThread().getName() + " : " + message
        );
    }
}

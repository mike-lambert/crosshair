package ru.cyberspacelabs.crosshair;

import ru.cyberspacelabs.crosshair.util.RuntimeUtil;

import static ru.cyberspacelabs.crosshair.util.RuntimeUtil.log;

/**
 * Created by mzakharov on 03.02.17.
 */
public class Main {
    public static void main(String[] args){
        MasterServer master = new MasterServer();
        try {
            log("*** Crosshair Master Server v." + RuntimeUtil.getAppVersion());
            log("Applying coniguration");
            RuntimeUtil.applyConfiguration(master);
            log("  Binding to UDP port " + master.getPort());
            log("  Expiration timeout " + master.getExpireTimeout() + " ms");
            log("  Cache clean period " + master.getCleanupPeriod() + " ms");
            master.start();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}

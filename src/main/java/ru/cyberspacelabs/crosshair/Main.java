package ru.cyberspacelabs.crosshair;

/**
 * Created by mzakharov on 03.02.17.
 */
public class Main {
    public static void main(String[] args){
        MasterServer master = new MasterServer();
        try {
            System.out.println("Starting Crosshair Master Server on UDP port " + master.getPort());
            System.out.println("  Expiration timeout " + master.getExpireTimeout() + " ms");
            System.out.println("  Cache clean period " + master.getCleanupPeriod() + " ms");
            master.start();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}

package ru.cyberspacelabs.crosshair.util;

import ru.cyberspacelabs.crosshair.model.GameServer;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;

/**
 * Created by mzakharov on 03.02.17.
 */
public class ProtocolUtil {
    private static final String CHALLENGE_CHARSET = "qwertyuiopasdfghjklzxcvbnm1234567890QWERTYUIOPASDFGHJKLZXCVBNM";
    public static final int PACKET_SIZE = 1400;
    public static Callable<GameServer> getServerInfoTask(String entry) {
        return () -> {
            GameServer result = null;
            try {
                long started = System.currentTimeMillis();
                //System.out.println(new Date() + " Contacting zone " + entry);
                String[] addrcomponents = entry.split(":");
                int port = Integer.parseInt(addrcomponents[1]);
                DatagramSocket socket = new DatagramSocket();
                socket.connect(InetAddress.getByName(addrcomponents[0]), port);
                String cmd = "getinfo " + createChallenge();
                //System.out.println(new Date() + " Querying zone: " + entry + ": \"" + cmd + "\"");
                sendMasterCommand(cmd, socket);
                String resp = awaitInfoResponse(socket);
                long completed = System.currentTimeMillis();
                long ping = completed - started;
                result = createServerInfoFromResponse(resp, entry);
                //System.out.println(new Date() + " Zone answer  : " + entry + ": \"" + resp + "\"");
                result.setRequestDuration(ping);
            } catch (Exception e){
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                //System.out.println(new Date() + " <- " + Thread.currentThread().getName() +  ": Zone fault  : " + entry + "\r\n" + sw.toString());
                result = null;
            }
            return result;
        };
    }

    public static GameServer createServerInfoFromResponse(String infoResponse, String address){
        GameServer result = new GameServer();
        Map<String, String> properties = parseInfoResponse(infoResponse);
        result.setAddress(address);
        result.setDisplayName(properties.get("hostname"));
        result.setGameType((properties.get("game") == null ? "ffa" : properties.get("game")));
        result.setMap(properties.get("mapname"));
        result.setPlayersPresent(Integer.parseInt(properties.get("g_humanplayers")));
        result.setServerProtocol(Integer.parseInt(properties.get("protocol")));
        result.setSlotsAvailable(Integer.parseInt(properties.get("sv_maxclients")));
        result.setLastHeartbeat(System.currentTimeMillis());
        return result;
    }

    public static void sendMasterCommand(String command, DatagramSocket socket) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(command.length() + 4);
        buffer.put((byte)0xFF);
        buffer.put((byte)0xFF);
        buffer.put((byte)0xFF);
        buffer.put((byte)0xFF);
        buffer.put(command.getBytes("ASCII"));
        byte[] array = buffer.array();
        DatagramPacket packet = new DatagramPacket(array, array.length);
        socket.send(packet);
    }

    public static String awaitInfoResponse(DatagramSocket socket) throws IOException {
        byte[] buffer = new byte[PACKET_SIZE];
        DatagramPacket rd = new DatagramPacket(buffer, buffer.length);
        socket.receive(rd);
        if (rd.getData() != null && rd.getData().length > 0){
            //System.out.println(new Date() + " RCV: " + hex(rd.getData()));
            // \xFF\xFF\xFF\xFFinfoResponse\x0A = 18;
            // each entry starts with \ then 6 bytes (IPv4 + port)
            int index = 18;
            int end = firstZero(rd.getData());
            int strlen = end - index;
            if (strlen > 0){
                byte[] strbuf = new byte[strlen];
                System.arraycopy(rd.getData(), index, strbuf, 0, strlen);
                return new String(strbuf, "ASCII");
            }
        }
        return "";
    }

    public static String createChallenge(){
        StringBuilder sb = new StringBuilder();
        Random r = new Random();
        for(int i = 0; i < 8; i++){
            int rnd = r.nextInt();
            if (rnd < 0){ rnd = rnd * -1; }
            char c = CHALLENGE_CHARSET.charAt(rnd % CHALLENGE_CHARSET.length());
            sb.append(c);
        }
        return sb.toString();
    }

    public static int firstZero(byte[] data) {
        return indexOfByte((byte)0x00, data);
    }

    public static int first0A(byte[] data){
        return indexOfByte((byte)0x0A, data);
    }

    private static int indexOfByte(byte target, byte[] data){
        for(int i = 0; i < data.length; i++){
            if (data[i] == target){
                return i;
            }
        }
        return -1;
    }

    public static Map<String, String> parseInfoResponse(String infoResponse) {
        String[] tokens = infoResponse.split("\\\\");
        Map<String, String> properties = new HashMap<String, String>();
        int i = 0;
        do{
            properties.put(tokens[i], tokens[i+1]);
            i += 2;
        } while(i < tokens.length);
        return properties;
    }

    public static String awaitGetInfoCommand(DatagramSocket socket) throws IOException {
        String result = "";
        byte[] buffer = new byte[PACKET_SIZE];
        DatagramPacket rd = new DatagramPacket(buffer, buffer.length);
        socket.receive(rd);
        if (rd.getData() != null && rd.getData().length > 0){
            //System.out.println(new Date() + " RCV: " + hex(rd.getData()));
            // \xFF\xFF\xFF\xFFinfoResponse\x0A = 18;
            // each entry starts with \ then 6 bytes (IPv4 + port)
            int index = 4;
            int end = firstZero(rd.getData());
            int strlen = end - index;
            if (strlen > 0){
                byte[] strbuf = new byte[strlen];
                System.arraycopy(rd.getData(), index, strbuf, 0, strlen);
                result = new String(strbuf, "ASCII");
                result = result.replace("getinfo ", "");
            }
        }
        return result;
    }

    public static String hex(byte[] arr){
        StringBuilder sb = new StringBuilder();
        for(byte b : arr){ sb.append(String.format("%02x", b));}
        return sb.toString();
    }
}


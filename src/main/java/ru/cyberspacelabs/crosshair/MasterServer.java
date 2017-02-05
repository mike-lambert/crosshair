package ru.cyberspacelabs.crosshair;

import ru.cyberspacelabs.crosshair.model.GameServer;
import ru.cyberspacelabs.crosshair.util.ProtocolUtil;
import ru.cyberspacelabs.threaded.Threaded;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Created by mzakharov on 03.02.17.
 */
public class MasterServer extends Threaded {
    private static class Command {
        private String endpoint;
        private String commandText;
        private String infoPacket;


        public String getInfoPacket() {
            return infoPacket;
        }

        public void setInfoPacket(String infoPacket) {
            this.infoPacket = infoPacket;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getCommandText() {
            return commandText;
        }

        public void setCommandText(String commandText) {
            this.commandText = commandText;
        }
    }

    private class CacheCleanTask extends TimerTask {

        @Override
        public void run() {
            System.out.println(new Date() + ": " + Thread.currentThread().getName() + " | seeking expired records");
            MasterServer.this.servers.forEach(server -> {
                if (server.getLastHeartbeat() + MasterServer.this.getExpireTimeout() < System.currentTimeMillis()){
                    System.out.println(new Date() + ": " + Thread.currentThread().getName() + " | removing " + server.getAddress() + " - heartbeat lost");
                    MasterServer.this.servers.remove(server);
                }
            });
        }
    }

    private final AtomicLong threadCounter = new AtomicLong(0);
    private final ExecutorService threadPool = Executors.newCachedThreadPool(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName(MasterServer.class.getSimpleName() + "-Worker::" + threadCounter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    private int port;
    private DatagramSocket socket;
    private Map<String, String> challenges;
    private Set<GameServer> servers;
    private long expireTimeout;
    private long cleanupPeriod;
    private Timer timer;

    public MasterServer(){
        challenges = new ConcurrentHashMap<>();
        servers = new ConcurrentSkipListSet<>();
        timer = new Timer();
        setPort(27950);
        setExpireTimeout(60000);
        setCleanupPeriod(10000);
    }

    public long getCleanupPeriod() {
        return cleanupPeriod;
    }

    public void setCleanupPeriod(long cleanupPeriod) {
        this.cleanupPeriod = cleanupPeriod;
    }

    public long getExpireTimeout() {
        return expireTimeout;
    }

    public void setExpireTimeout(long expireTimeout) {
        this.expireTimeout = expireTimeout;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    @Override
    protected void doAfterStop() {
        System.out.println(new Date() + ": " + Thread.currentThread().getName() + " closing socket");
        socket.close();
        socket = null;
        System.out.println(new Date() + ": " + Thread.currentThread().getName() + " cancelling cleanup timer");
        timer.cancel();
    }

    @Override
    protected void doBeforeStart() throws Exception {
        System.out.println(new Date() + ": " + Thread.currentThread().getName() + " binding UDP socket");
        socket = new DatagramSocket(port);
        System.out.println(new Date() + ": " + Thread.currentThread().getName() + " starting cleanup timer");
        timer.schedule(new CacheCleanTask(), 0, cleanupPeriod);
    }

    @Override
    protected void doWorkCycle() {
        byte[] buffer = new byte[ProtocolUtil.PACKET_SIZE];
        DatagramPacket data = new DatagramPacket(buffer, buffer.length);
        try {
            socket.receive(data);
            dispatchCommand(parseCommand(data));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void dispatchCommand(Command command) {
        if (command == null){ return; }
        if (command.getCommandText() == null){ return; }
        threadPool.submit(() -> {
            if (command.getCommandText().startsWith("heartbeat ")){
                System.out.println(new Date() + ": " + Thread.currentThread().getName() + " -> " + command.getEndpoint() + " <<< " + command.getCommandText());
                processHeartbeat(command);
            } else if (command.getCommandText().equalsIgnoreCase("infoResponse")){
                processInfoResponse(command);
            } else if (command.getCommandText().startsWith("getservers ")){
                try {
                    System.out.println(new Date() + ": " + Thread.currentThread().getName() + " -> " + command.getEndpoint() + " <<< " + command.getCommandText());
                    processGetServers(command);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                System.out.println(new Date() + ": " + Thread.currentThread().getName() + " -> " + command.getEndpoint() + " UNKNOWN COMMAND:  " + command.getCommandText());
            }
        });
    }

    private void processGetServers(Command command) throws IOException {
        // 71 empty full demo
        String[] args = command.getCommandText().replace("getservers ", "").trim().split(" ");
        int protocol = Integer.parseInt(args[0]);
        long timestamp = System.currentTimeMillis();
        Set<GameServer> byProtocol = servers.stream().filter(server ->
                        server.getServerProtocol() == protocol &&
                        server.getLastHeartbeat() + expireTimeout >= timestamp
        ).collect(Collectors.toSet());
        Set<GameServer> filtered = byProtocol.stream().filter( server ->
                (
                        // TODO: further filtering
                        (server.getPlayersPresent() == 0 && command.getCommandText().contains("empty")) ||
                                (server.getPlayersPresent() == server.getSlotsAvailable() && command.getCommandText().contains("full")) ||
                                (server.getPlayersPresent() < server.getSlotsAvailable() && server.getPlayersPresent() > 0)
                )
        ).collect(Collectors.toSet());
        System.out.println(new Date() + ": " + Thread.currentThread().getName() + " -> " + command.getEndpoint() + " sending " + filtered.size() + " servers out of " + servers.size());
        byte[] buffer = new byte[28 + 7 * filtered.size()];
        ByteBuffer data = ByteBuffer.wrap(buffer);
        data.put((byte)0xFF);
        data.put((byte)0xFF);
        data.put((byte)0xFF);
        data.put((byte)0xFF);
        data.put("getserversResponse".getBytes("ASCII"));
        filtered.forEach(server -> {
            byte[] epp = parseEndpoint(server.getAddress());
            data.put((byte)0x5C);
            data.put(epp);
        });
        data.put((byte)0x45);
        data.put((byte)0x4F);
        data.put((byte)0x54);
        data.put((byte)0x00);
        data.put((byte)0x00);
        data.put((byte)0x00);
        System.out.println(new Date() + ": " + Thread.currentThread().getName() + " getserversResponse -> " + command.getEndpoint() + " sent " + ProtocolUtil.hex(data.array()));
        socket.send(createPacket(data.array(), command.getEndpoint()));
    }

    private byte[] parseEndpoint(String address) {
        String[] ac = address.split(":");
        String[] ao = ac[0].split("\\.");
        int port = Integer.parseInt(ac[1]);
        byte[] result = new byte[6];
        for(int i = 0; i < 4; i++){
            result[i] = (byte)Integer.parseInt(ao[i]);
        }
        result[5] = (byte)(port & 0xFF);
        result[4] = (byte)((port >> 8) & 0xFF);
        return result;
    }

    private DatagramPacket createPacket(byte[] payload, String endpoint) throws UnknownHostException {
        String[] ac = endpoint.split(":");
        String remote_ip = ac[0];
        int remote_port = Integer.parseInt(ac[1]);
        DatagramPacket packet = new DatagramPacket(payload, payload.length, InetAddress.getByName(remote_ip), remote_port);
        return packet;
    }

    private void processInfoResponse(Command command) {
        Map<String, String> info = ProtocolUtil.parseInfoResponse(command.getInfoPacket());
        String challenge = info.get("challenge");
        String storedEndpoint = challenges.remove(challenge);
        if (storedEndpoint == null){
            System.out.println(new Date() + ": " + Thread.currentThread().getName() + " -> " + command.getEndpoint() + " Unknown challenge \"" + challenge + "\"");
            return;
        }
        if (storedEndpoint != null && !storedEndpoint.equals(command.getEndpoint())){
            System.out.println(new Date() + ": " + Thread.currentThread().getName() + " -> " + command.getEndpoint() + " IP mismatched for challenge \"" + challenge + "\": " + storedEndpoint);
            return;
        }
        GameServer server = ProtocolUtil.createServerInfoFromResponse(command.getInfoPacket(), command.getEndpoint());
        if (!servers.add(server)){
            System.out.println(new Date() + ": " + Thread.currentThread().getName() + " -> " + command.getEndpoint() + " REFRESHING ");
            refreshHit(server);
        } else {
            System.out.println(new Date() + ": " + Thread.currentThread().getName() + " -> " + command.getEndpoint() + " REGISTERING " + server.getDisplayName() + " <- " + server.getGameType());
        }
    }

    private void refreshHit(GameServer server) {
        servers.stream().filter(entry -> entry.equals(server)).findFirst().get().setLastHeartbeat(server.getLastHeartbeat());
    }

    private void processHeartbeat(Command command) {
        String challenge = ProtocolUtil.createChallenge();
        challenges.put(challenge, command.getEndpoint());
        System.out.println(new Date() + ": " + Thread.currentThread().getName() + " registered challenge \"" + challenge + "\" for " + command.getEndpoint());
        String cmd = "getinfo " + challenge;
        try {
            sendMasterCommand(cmd, command.getEndpoint());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendMasterCommand(String command, String endpoint) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(command.length() + 4);
        buffer.put((byte)0xFF);
        buffer.put((byte)0xFF);
        buffer.put((byte)0xFF);
        buffer.put((byte)0xFF);
        buffer.put(command.getBytes("ASCII"));
        byte[] array = buffer.array();
        socket.send(createPacket(array, endpoint));
    }

    private Command parseCommand(DatagramPacket packet) throws UnsupportedEncodingException {
        Command result = new Command();
        result.setEndpoint(packet.getAddress().toString().replace("/", "") + ":" + packet.getPort());
        int eom = ProtocolUtil.first0A(packet.getData());
        if (eom > 4){ // 0-3 is FFFFFFFF
            int strlen = eom - 4;
            byte[] message = new byte[strlen];
            System.arraycopy(packet.getData(), 4, message, 0, strlen);
            String command = new String(message, "ASCII");
            result.setCommandText(command);
            if (command.equalsIgnoreCase("infoResponse")){
                int index = 18;
                int end = ProtocolUtil.firstZero(packet.getData());
                strlen = end - index;
                if (strlen > 0){
                    byte[] strbuf = new byte[strlen];
                    System.arraycopy(packet.getData(), index, strbuf, 0, strlen);
                    result.setInfoPacket(new String(strbuf, "ASCII"));
                }
            }
        }
        return result;
    }
}

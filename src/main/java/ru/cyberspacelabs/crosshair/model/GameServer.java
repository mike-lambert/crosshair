package ru.cyberspacelabs.crosshair.model;

/**
 * Created by mzakharov on 03.02.17.
 */
public class GameServer implements Comparable<GameServer> {
    private String address;
    private int serverProtocol;
    private String map;
    private int playersPresent;
    private int slotsAvailable;
    private String displayName;
    private String gameType;
    private long requestDuration;
    private long lastHeartbeat;

    public GameServer(){
        setRequestDuration(-1);
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(long lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public long getRequestDuration() {
        return requestDuration;
    }

    public void setRequestDuration(long requestDuration) {
        this.requestDuration = requestDuration;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public int getServerProtocol() {
        return serverProtocol;
    }

    public void setServerProtocol(int serverProtocol) {
        this.serverProtocol = serverProtocol;
    }

    public String getMap() {
        return map;
    }

    public void setMap(String map) {
        this.map = map;
    }

    public int getPlayersPresent() {
        return playersPresent;
    }

    public void setPlayersPresent(int playersPresent) {
        this.playersPresent = playersPresent;
    }

    public int getSlotsAvailable() {
        return slotsAvailable;
    }

    public void setSlotsAvailable(int slotsAvailable) {
        this.slotsAvailable = slotsAvailable;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getGameType() {
        return gameType;
    }

    public void setGameType(String gameType) {
        this.gameType = gameType;
    }

    public boolean isValid(){
        return getRequestDuration() >= 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GameServer that = (GameServer) o;

        if (getServerProtocol() != that.getServerProtocol()) return false;
        return getAddress().equals(that.getAddress());

    }

    @Override
    public int hashCode() {
        int result = getAddress().hashCode();
        result = 31 * result + getServerProtocol();
        return result;
    }

    @Override
    public int compareTo(GameServer o) {
        if (o == null){ throw  new NullPointerException("Cannot compare to null");}
        int a = this.getAddress().compareTo(o.getAddress()) * 1;
        int b = this.getServerProtocol() == o.getServerProtocol() ? 0 : -2;
        return this.equals(o) ? 0 :  (a+b) > 0 ? 1 : -1;
    }
}

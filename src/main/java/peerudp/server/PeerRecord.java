package peerudp.server;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PeerRecord {

    private String username;

    private String ip;
    private Integer port;

    public PeerRecord( @JsonProperty("username") String username,@JsonProperty("ip") String ip, @JsonProperty("port")
            Integer port) {
        this.username = username;
        this.ip = ip;
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

}
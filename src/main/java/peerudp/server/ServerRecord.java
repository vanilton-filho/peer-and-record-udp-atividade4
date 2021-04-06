package peerudp.server;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ServerRecord {
    private String ip;
    private Integer port;


    public ServerRecord(@JsonProperty("ip") String ip, @JsonProperty("port")
            Integer port) {
        this.ip = ip;
        this.port = port;
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

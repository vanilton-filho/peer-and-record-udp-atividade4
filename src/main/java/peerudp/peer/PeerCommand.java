package peerudp.peer;

public enum PeerCommand {
    EXIT("/exit"), LS_RECORDS("/ls-records"), CONNECT("/connect"), QUIT("/quit");

    private String value;

    PeerCommand(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return getValue();
    }
}
package peerudp.peer;

/**
 * Os status são constantes que podem ser utilizados por um peer para a troca de
 * estados entre eles.
 */
public enum PeerStatus {

    OK("ok.peer"), CLOSE_PEER("close.peer"), REGISTER("register.peer"), UNREGISTER("unregister.peer"),
    OK_REGISTER("ok_register.peer"), NONE_REGISTER("none_register.peer"), OK_UNREGISTER("ok_unregister.peer"),
    NONE_UNREGISTER("none_unregister.peer"), LIST_RECORDS("list_records.peer"), NOT_RECOGNIZED("not_recognized.peer"),
    DOMAIN_NOT_RECOGNIZED("domain_not_recognized.peer"), QUIT("quit.peer");

    private String value;

    PeerStatus(String value) {
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
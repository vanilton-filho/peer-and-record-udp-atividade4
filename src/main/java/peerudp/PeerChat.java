package peerudp;

import java.io.IOException;
import java.net.InetAddress;

import peerudp.peer.Peer;
import peerudp.server.RecordServer;

/**
 * Hello world!
 *
 */
public class PeerChat {
    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length == 2 && args[0].equals("--run-server")) {
            new RecordServer(Integer.parseInt(args[1]));
        } else if (args.length == 5 && args[0].equals("--run-peer") && args[1].equals("--to-register")) {
            var peer = new Peer(Integer.parseInt(args[2]), InetAddress.getByName(args[3]), Integer.parseInt(args[4]),
                    true);
            peer.execute();
        }
    }
}

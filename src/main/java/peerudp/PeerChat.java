package peerudp;

import java.io.IOException;
import java.net.InetAddress;

import peerudp.peer.Peer;
import peerudp.server.RecordServer;


public class PeerChat {
    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length == 3 && args[0].equals("--run-server")) {
            new RecordServer(Integer.parseInt(args[1]), InetAddress.getLocalHost(), Integer.parseInt(args[2]), false, false);
        } else if (args.length == 5 && args[0].equals("--run-server") && args[1].equals("--to-replicate")) {

            new RecordServer(Integer.parseInt(args[2]), InetAddress.getByName(args[3]), Integer.parseInt(args[4]), false, true);
        }
        else if (args.length == 3 && args[0].equals("--run-peer") && args[1].equals("--to-register")) {
            var peer = new Peer(Integer.parseInt(args[2]), InetAddress.getByName(""), Integer.parseInt("0"),
                    true);
            peer.execute();
        }
    }
}

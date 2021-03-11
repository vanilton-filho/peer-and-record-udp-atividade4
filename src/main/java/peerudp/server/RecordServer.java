package peerudp.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import peerudp.peer.DataRequest;
import peerudp.peer.Peer;
import peerudp.peer.PeerStatus;

/**
 * O servidor de registros é um peer com apenas um servidor em execução. Por
 * isso vamos extender e no futuro caso precise ter um cliente, sem problemas,
 * sobrescreve a thread e seje :) feliz...
 */
public class RecordServer extends Peer {

    private static final Logger LOGGER = Logger.getLogger(RecordServer.class.getName());

    // Vamos salvar os nossos registros associados com uma chave (domain)
    private Map<String, PeerRecord> records = new HashMap<>();

    // Essa estrutura é apenas utilizada para garantir que apenas existam
    // chaves únicas no servidor de registros (tipo domínios únicos)
    private Set<String> domains = new HashSet<>();
    // Separando os IPs para ter uma estrutura de dados mais simples
    private List<String> ips = new ArrayList<>();

    public RecordServer(int port) throws SocketException, UnknownHostException {
        super(port);
        LOGGER.info("| Servidor de registros em execução...");
        LOGGER.info("| Executando em " + InetAddress.getLocalHost().getHostAddress() + ":" + getPortListen());
        runServer();
    }

    @Override
    public void runServer() {
        var running = true;
        while (running) {
            try {
                flowServer();
            } catch (IOException e) {
                LOGGER.severe(e.getMessage());
                running = false;
            }
        }
    }

    private void flowServer() throws IOException {
        // Sempre ficamos na escuta de qualquer pacote UDP que chegar
        this.getSocket().receive(getDatagramPacket());
        var req = getDataRequest();
        var data = req.getData();
        var extractStatus = data.substring(data.lastIndexOf("%") + 1);
        if (extractStatus.equals(PeerStatus.REGISTER.getValue())) {
            LOGGER.info("|~>| Pedido de registro... (" + req.getHostAddress() + "@" + extractUsername(data) + ")");

            // O servidor inicia o processo de registro
            var status = registerPeer(req);
            // O servidor faz uma tentativa de resposta para o servidor do outro peer
            // informando que o registro foi efetuado ou não (já estava registrado)
            if (status == 1) {
                send(PeerStatus.OK_REGISTER, req.getAddress(), req.getPort());
            } else {
                send(PeerStatus.NONE_REGISTER, req.getAddress(), req.getPort());
            }
        } else if (extractStatus.equals(PeerStatus.UNREGISTER.getValue())) {
            LOGGER.info("|~X| Pedido de desregistro do peer... (" + req.getHostAddress() + "@" + extractUsername(data)
                    + ")");
            var status = unregisterPeer(req);
            LOGGER.info(getListRecords());
            // Se o peer foi desregistrado então enviamos uma mensagem de sucesso,
            // caso contrário significa que este peer não está registrado e retornamos um
            // código de status para isso.
            if (status == 1) {
                send(PeerStatus.OK_UNREGISTER, req.getAddress(), req.getPort());
            } else {
                send(PeerStatus.NONE_UNREGISTER, req.getAddress(), req.getPort());
            }
        } else if (extractStatus.contains(PeerStatus.LIST_RECORDS.getValue())) {
            LOGGER.info("|~| Pedido para listagem de registros por (" + req.getHostAddress() + "@"
                    + extractUsername(data) + ")");
            // O servidor deve retornar uma lista com todos os registros
            send(getListRecords(), req.getAddress(), req.getPort());
        } else if (extractStatus.contains(PeerStatus.OK.getValue())) {
            LOGGER.info("|~|:OK A lista de registros chegou ao destino " + req.getHostAddress());
        } else if (data.contains("%domain%")) {
            var domain = extractDomain(data);
            var exists = existsDomain(domain);

            if (exists) {
                var infoHost = "%ip%" + records.get(domain).getIp() + "%port%" + records.get(domain).getPort();
                send(infoHost, req.getAddress(), req.getPort());
            } else {
                send(PeerStatus.DOMAIN_NOT_RECOGNIZED, req.getAddress(), req.getPort());
            }
        } else {
            // Se o servidor receber qualquer coisa diferente, então
            // ele envia um código de status dizendo que não entendeu
            // a requisição
            send(PeerStatus.NOT_RECOGNIZED, req.getAddress(), req.getPort());
        }
    }

    public String extractDomain(String data) {
        // Primeiro devemos lembrar que o primeiro padrão é nome de usuário
        // %vanilton-filho%%domain%eo98-2982h-82982
        return data.substring(data.lastIndexOf("%") + 1);
    }

    private int registerPeer(DataRequest data) {
        int status;

        // Se lembra que em DataRequest nos extraímos o ip e porta do pacote que foi
        // recebido?
        // Isso agora vai ser de grande ajuda para registro desses peer no servidor

        // Primeiro vamos verificar se esse peer já está registrado pelo IP
        status = ips.contains(data.getHostAddress()) ? -1 : 0;

        if (status == 0) {
            // Vamos gerar um key para o nosso peer, usaremos um UUID aleatório
            // para isso
            var domain = UUID.randomUUID().toString();
            domains.add(domain);
            // Nome do usuário do peer que se registrou
            var username = extractUsername(data.getData());
            // IP do endereço remoto
            var remoteIP = data.getHostAddress();
            // Número da porta pela qual ele está escutando
            var remotePort = data.getPort();

            records.put(domain, new PeerRecord(username, remoteIP, remotePort));
            ips.add(remoteIP);
            status = 1;
        } else {
            LOGGER.info("|X| O peer " + data.getHostAddress() + " já se encontra registrado!");
        }

        return status;

    }

    private int unregisterPeer(DataRequest req) {
        var aux = new HashMap<String, String>();

        records.forEach((domain, peerRecord) -> {
            if (req.getHostAddress().equals(peerRecord.getIp())) {
                aux.put("status", "ok");
                aux.put("domain", domain);
                aux.put("ip", peerRecord.getIp());
                return;
            }
        });

        if (aux.get("status").equals("ok")) {
            records.remove(aux.get("domain"));
            ips.remove(aux.get("ip"));
            return 1;
        }

        return 0;
    }

    private String getListRecords() {
        var list = new StringBuilder();
        list.append("\n|\t* Lista de Registros *\t|\n");
        list.append("\n| domains | usuario | ip | porta\n");
        records.forEach((domain, peerRecord) -> {
            list.append("| " + domain + " |");
            list.append("| " + peerRecord.getUsername() + " |");
            list.append("| " + peerRecord.getIp() + " |");
            list.append("| " + peerRecord.getPort() + " |\n");
        });

        return list.toString();
    }

    public void send(Object data, InetAddress host, int port) throws IOException {
        // Só inicializando o buffer...
        byte[] dataSerialized = new byte[1];

        if (data instanceof String) {
            var dataMessage = (String) data;
            dataSerialized = dataMessage.getBytes();
        } else if (data instanceof PeerStatus) {
            var dataStatus = (PeerStatus) data;
            dataSerialized = dataStatus.getValue().getBytes();
        }
        var response = new DatagramPacket(dataSerialized, dataSerialized.length, host, port);
        this.getSocket().send(response);
    }

    private boolean existsDomain(String domainTarget) {
        return domains.contains(domainTarget);
    }

}

package peerudp.server;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private Map<String, ServerRecord> servers = new HashMap<>();

    // Essa estrutura é apenas utilizada para garantir que apenas existam
    // chaves únicas no servidor de registros (tipo domínios únicos)
    private Set<String> domains = new HashSet<>();
    // Separando os IPs para ter uma estrutura de dados mais simples
    private List<String> ips = new ArrayList<>();
    private DatagramSocket subscribeService;

    public RecordServer(int port) throws SocketException, UnknownHostException {
        super(port);
        LOGGER.info("| Servidor de registros em execução...");
        LOGGER.info("| Executando em " + InetAddress.getLocalHost().getHostAddress() + ":" + getPortListen());
        runServer();
    }


    public RecordServer(int portListen, InetAddress hostTarget, int portTarget, boolean isToRegister, boolean isToReplicate) throws IOException {
        // Inicializando socket onde o servidor vai ouvir as requisições dos peers
        super(portListen);
        LOGGER.info("| Servidor de registros em execução...");
        LOGGER.info("| Serviço de registros executando em " + InetAddress.getLocalHost().getHostAddress() + ":" + getPortListen());
        servers.put(UUID.randomUUID().toString(), new ServerRecord(InetAddress.getLocalHost().getHostAddress(), portTarget));

        if (!isToReplicate) {
            this.subscribeService = new DatagramSocket(portTarget, InetAddress.getLocalHost());
            runServer();
            runServerSubscribe();
            LOGGER.info("| Serviço de replicação executando em " + InetAddress.getLocalHost().getHostAddress() + ":" + portTarget);

        } else if (isToReplicate) {
            this.subscribeService = new DatagramSocket(0, InetAddress.getLocalHost());
            runServer();
            runServerSubscribe();

            LOGGER.info("| Serviço de replicação executando em " + InetAddress.getLocalHost().getHostAddress() + ":" + this.subscribeService.getLocalPort());

            this.subscribeService.send(new DatagramPacket(PeerStatus.REPLICATE.getValue().getBytes(), PeerStatus.REPLICATE.getValue().length(), hostTarget, portTarget));
        }

    }

    @Override
    public void runServer() {
        Runnable server = () -> {
            var running = true;
            while (running) {
                try {
                    flowServer();
                } catch (IOException e) {
                    LOGGER.severe(e.getMessage());
                    running = false;
                }
            }
        };
        new Thread(server).start();
    }

    public void runServerSubscribe() {

        Runnable server = () -> {
            var running = true;
            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(new byte[8192], 8192);
                    this.subscribeService.receive(packet);
                    var req = new DataRequest(packet);
                    var data = req.getData();
                    if (data.contains(PeerStatus.REPLICATE.getValue())) {
//                        System.out.println(servers.toString());

                        System.out.println("|~#| Pedido de replicação do servidor... " + req.getAddress() + ":" + req.getPort());
                        // Salvando a lista de servidores de registros na rede
                        servers.put(UUID.randomUUID().toString(), new ServerRecord(req.getHostName(), req.getPort()));

                        sendReplicate(records, req);
                        Thread.sleep(1000);
                        servers.forEach((chave, serverRecord) -> {
                            try {
                                sendServerRecord(servers, serverRecord.getIp(), serverRecord.getPort());
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
//                        System.out.println(new ObjectMapper().writeValueAsString(servers));
                    } else if (data.startsWith("#")) {
//                        System.out.println("###########");

                        ObjectMapper mapper = new ObjectMapper();
                        this.records = mapper.readValue(data.substring(1), new TypeReference<HashMap<String,PeerRecord>>(){});
//                        System.out.println(getListRecords());

                    } else if (data.startsWith("$")) {
//                        System.out.println("$$$$$$$$$$$$");

                        ObjectMapper mapper = new ObjectMapper();

                        this.servers = mapper.readValue(data.substring(1), new TypeReference<HashMap<String,ServerRecord>>(){});
//                        System.out.println(new ObjectMapper().writeValueAsString(servers));
                    }

                } catch (IOException | InterruptedException e) {
                    LOGGER.severe(e.getMessage());
                    running = false;
                }
            }
        };

        new Thread(server).start();

    }



    private void flowServer() throws IOException {
        // Sempre ficamos na escuta de qualquer pacote UDP que chegar
        this.getSocket().receive(getDatagramPacket());
        var req = getDataRequest();
        var data = req.getData();
        var extractStatus = data.substring(data.lastIndexOf("%") + 1);
        if (extractStatus.equals(PeerStatus.DISCOVER.getValue())) {
            send(PeerStatus.OK_DISCOVER, req.getAddress(), req.getPort());
        } else if (extractStatus.equals(PeerStatus.REGISTER.getValue())) {
            LOGGER.info("|~>| Pedido de registro... (" + req.getHostAddress() + "@" + extractUsername(data) + ")");

            // O servidor inicia o processo de registro
            var status = registerPeer(req);
            // O servidor faz uma tentativa de resposta para o servidor do outro peer
            // informando que o registro foi efetuado ou não (já estava registrado)
            if (status == 1) {
                send(PeerStatus.OK_REGISTER, req.getAddress(), req.getPort());
                servers.forEach((chave, serverRecord) -> {
                        try {

                            propagate(records, serverRecord.getIp(), serverRecord.getPort());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                });
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
                servers.forEach((chave, serverRecord) -> {
                    try {

                        propagate(records, serverRecord.getIp(), serverRecord.getPort());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                });
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
            var infoHost = "%ip%" + records.get(domain).getIp() + "%port%" + records.get(domain).getPort();
            send(infoHost, req.getAddress(), req.getPort());
        }  else {
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

        return status;

    }

    private int unregisterPeer(DataRequest req) {
        var aux = new HashMap<String, String>();

        records.forEach((domain, peerRecord) -> {
            if (req.getPort() == (peerRecord.getPort())) {
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


    public void sendReplicate(Map<String, PeerRecord> records, DataRequest req) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String jsonRecords = mapper.writeValueAsString(records);
        jsonRecords = "#" + jsonRecords;

        this.subscribeService.send(new DatagramPacket(jsonRecords.getBytes(), jsonRecords.length(), InetAddress.getByName(req.getHostAddress()), req.getPort()) );
    }


    public void sendServerRecord(Map<String, ServerRecord> servers, String ip, Integer port) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String jsonServers = mapper.writeValueAsString(servers);
        jsonServers = "$" + jsonServers;
        this.subscribeService.send(new DatagramPacket(jsonServers.getBytes(), jsonServers.length(), InetAddress.getByName(ip), port) );

    }


    public void propagate(Map<String, PeerRecord> records, String ip, Integer port) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String jsonRecords = mapper.writeValueAsString(records);
        jsonRecords = "#" + jsonRecords;

        this.subscribeService.send(new DatagramPacket(jsonRecords.getBytes(), jsonRecords.length(), InetAddress.getByName(ip), port) );

    }

}

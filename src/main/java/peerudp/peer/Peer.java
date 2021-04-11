package peerudp.peer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import peerudp.server.PeerRecord;

import java.io.IOException;
import java.io.OutputStream;
import java.net.*;
import java.util.*;

public class Peer {
    private DatagramSocket socket;
    // É no pacote de request que obtemos os dados de quem
    // se conectou a este par.
    private DatagramPacket request;
    // Porta que estaremos a capturar os pacotes UDP pelo servidor
    private int portListen;
    // Host que a instância deste peer vai utilizar para enviar os pacotes UDP
    private InetAddress hostTarget;
    // Porta do peer de destino
    private int portTarget;
    // É através de um scanner que vamos pegar o input para a troca de mensagens
    private Scanner scanner;
    // Quando o socket estiver fechado essa flag pode ser
    // utilizada para evitar que por engano usemos um socket fechado
    private boolean isAlive;
    // Para dar feedback ao peer que a menagem enviada chegou ao outro peer usamos
    // esta flag
    private boolean promtpCheckedMessage;
    private String username = "";
    private String email;

    private InetAddress hostRegisterServer;
    private int portRegisterServer;
    private boolean isToRegister;
    private boolean isConnect;
    private boolean isOcupped;
    private boolean isToStopServer;
    private boolean isToStopClient;
    private boolean isRegistred;

    public Peer(int portListen) throws SocketException {
        socket = new DatagramSocket(portListen);
        this.portListen = socket.getLocalPort();
        newRequestPacket();
    }

    public Peer(int portListen, InetAddress hostTarget, int portTarget, boolean isToRegister) throws IOException {
        socket = new DatagramSocket(portListen);
        this.portListen = socket.getLocalPort();
        this.hostTarget = hostTarget;
        this.portTarget = portTarget;

        scanner = new Scanner(System.in);

        var tempScanner = new Scanner(System.in);
        while (this.username.isBlank()) {
            System.out.print("Username: ");
            this.username = tempScanner.next();
        }

        OkHttpClient clientHttp = new OkHttpClient();
        HttpUrl.Builder urlBuilder;
        Request request;
        Map<String, Boolean> response;
        var result = false;
        while (!result) {

            System.out.print("Email: ");
            this.email = tempScanner.next();

            System.out.println(this.email);
            urlBuilder = HttpUrl.parse("http://localhost:8080/email-validator").newBuilder();
            urlBuilder.addQueryParameter("email", this.email);
            String url = urlBuilder.build().toString();

            request = new Request.Builder()
                    .url(url)
                    .build();

            Call call =  clientHttp.newCall(request);
            Response responseHttp = call.execute();
            ObjectMapper mapper = new ObjectMapper();
            response = mapper.readValue(responseHttp.body().string(), new TypeReference<HashMap<String, Boolean>>(){});
            result = response.get("resultValidator");
        }
        tempScanner.nextLine();

        this.isToRegister = isToRegister;

        newRequestPacket();
    }

    /**
     * Executa um peer, que executa ao mesmo tempo duas threads, uma para o cliente
     * e outra para o servidor.
     */
    public void execute() throws IOException {
        this.runClient();
        this.runServer();
    }

    /**
     * A lógica de execução da thread para o servidor...
     */
    public void runServer() {
        Runnable server = () -> {
            try {
                // A thread fica em execução até o valor da variável mudar
                var running = true;
                while (running) {
                    // Para evitar erros com buffer "sujo", carregamos sempre um
                    // novo buffer
                    newRequestPacket();
                    // A thread sempre fica na escuta de qualquer pacote UDP que chegar
                    this.socket.receive(request);

                    // Um pacote chegando no socket pelo DatagramSocket#receive,
                    // podemos agora obter esses dados e armazenar num objeto a fim de
                    // organização, através do this#getDataRequest
                    var req = getDataRequest();
                    var data = req.getData();

                    // O servidor entende isso, imprime a mensagem e então envia um código de status
                    // para informar que recebeu a mensagem do outro peer e pode fechar a conexão.
                    serverFlow(req, data);
                    if (this.isToStopServer) {
                        running = false;
                    }
                }
            } catch (IOException | InterruptedException e) {
                System.err.println(e.getMessage());
            }

        };

        // Criamos uma thread e armazenamos numa variável
        var serverThread = new Thread(server);
        // Iniciamos a thread e como ela possui um laço, ela vai ficar
        // de pé até que sua atividade seja finalizada. (dahhh, é o que uma thread faz
        // ;))
        serverThread.start();
    }

    /**
     * A lógica de execução da thread para o cliente..
     * 
     */
    public void runClient() {
        Runnable client = () -> {
            try {
                toRegisterSequence();

                // Antes de tudo vamos fazer o pedido para registro se
                // foi solicitado pelo peer ao ser executado
//                toRegister();
                Thread.sleep(10000);
                // Thread em loop infinito até segunda ordem
                var running = true;
                // Se no servidor deste peer recebemos um PeerStatus.OK_REGISTER
                // então estamos conectados e o cliente executa
                while (running && isConnect) {
                    clientFlow();
                    if (this.isToStopClient) {
                        running = false;
                    }
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }

        };

        // A thread para o cliente é instanciada
        var clientThread = new Thread(client);
        // Iniciamos a thread que sempre fica escutando pelo scanner um input do teclado
        clientThread.start();
    }

    private void clientFlow() throws IOException, InterruptedException {
        var data = prompt();
        // Se o imput for igual ao comando /exit, então comece o processo para
        // encerrar o peer. Comandos neste protocolo sempre são iniciados pelo
        // /<command>

        if (data.equals(PeerCommand.EXIT.getValue())) {
            this.isConnect = false;
            discover();
            // Enviando uma mensagem para o outro peer que pode estar conectado.
            // Não é garantido que está mensagem chegue já
            toUnregister();
            System.out.println("(x) Peer fechando...");
            // Vamos parar o loop dentro deste runnable
            this.isToStopClient = true;
            System.out.println("(peer.client, fechando)");
            scanner.close(); // Boa prática fechar

            Thread.sleep(3000);
            autoKillThread();
        } else if (data.equals(PeerCommand.LS_RECORDS.getValue())) {
            this.isConnect = false;
            discover();
            send(PeerStatus.LIST_RECORDS);
        } else if (data.startsWith(PeerCommand.CONNECT.getValue())) {
            this.isConnect = false;
            discover();
            // Pegamos o uuid passado depois do commando
            var domainStr = data.substring(9);
            // lembrando que internamente vou enviar os dados com o nome do usuário também
            send("%domain%" + domainStr);
        } else if (data.equals(PeerCommand.QUIT.getValue()) && this.hostTarget != this.hostRegisterServer) {
            send(PeerStatus.QUIT);
            this.isConnect = false;
            discover();
            Thread.sleep(1000);
            quit(data, false);

        } else if (!data.isBlank() && !containsPeerStatusCode(data, PeerStatus.values())) {
            if (this.hostTarget != this.hostRegisterServer) {
                send(data);
                Thread.sleep(1000);
            } else {
                this.isConnect = false;
                discover();
                send(data);
                Thread.sleep(1000);
            }

        }
    }

    private void serverFlow(DataRequest req, String data) throws IOException, InterruptedException {
        if (data.contains(PeerCommand.EXIT.getValue())) {
            exitFlow(req);
        } else if (data.contains(PeerStatus.CLOSE_PEER.getValue())) {
            this.isToStopServer = true;
            peerCloseFlow();

        } else if (data.contains(PeerStatus.OK.getValue())) {
            // O servidor recebeu um código de status (PeerStatus.OK), então apenas
            // seta o valor do prompt para true (checked) de modo a dar o feedback para o
            // cliente na próxima interação do prompt na thread do cliente.
            // De fato o status OK significa neste protocolo que uma mensagem chegou
            // ao peer de destino.
            this.promtpCheckedMessage = true;

        } else if (data.contains(PeerStatus.OK_REGISTER.getValue()) || data.contains(PeerStatus.OK_DISCOVER.getValue()) ) {
            okRegisterFlow(req);

        } else if (data.contains(PeerStatus.NONE_REGISTER.getValue())) {
            this.isToStopServer = true;
            notRegisterFlow();
        } else if (data.contains(PeerStatus.OK_UNREGISTER.getValue())) {
            System.out.println("(peer, unregistred)");
        } else if (data.contains(PeerStatus.NOT_RECOGNIZED.getValue())) {
            System.out.println("\n\tMensagem não reconhecida pelo servidor de registro...\n");
        } else if (data.contains("* Lista de Registros *")) {
            System.out.println(req.getData());
            send(PeerStatus.OK);
        } else if (data.contains("%ip%") && data.contains("%port%")) {
            changeTarget(data);
        } else if (data.contains(PeerStatus.QUIT.getValue())) {
            quit(data, true);
        } else if (isOcupped && req.getHostAddress().equals(this.hostTarget.getHostAddress())) {
            occupedFlow(req);
        } else if (!isOcupped) {
            notOcuppedFlow(req);
        }
    }

    private void exitFlow(DataRequest req) throws IOException {
        var data = req.getData();
        var usernameSource = extractUsername(data);
        System.out
                .println("\n|__ (" + req.getHostAddress() + "@" + usernameSource + ") Estou fechando o peer, tchau...");
        // O servidor faz uma tentativa de resposta para o servidor do outro peer
        send(PeerStatus.CLOSE_PEER);
        this.hostTarget = this.hostRegisterServer;
        this.portTarget = this.portRegisterServer;
    }

    private void peerCloseFlow() {
        // Se o servidor receber uma mensagem com código de status para fechar
        // (PeerStatus.CLOSE_PEER),
        // é isso que ele irá fazer. Imprime mensagem informando o fechamento do
        // servidor,
        // depois faz a configuração para o laço da thread parar e dizer que o peer pode
        // se considerar livre, desconectando o socket e fechando o mesmo.

        System.out.println("(peer.server, fechando)");
        this.isAlive = true;
        socket.disconnect();
        socket.close();
    }

    private void okRegisterFlow(DataRequest req) {
        if (!this.isRegistred) {
            // O peer se registrou com sucesso no servidor de registros
            System.out.println("\n \\0/ l0l Registro efetuado com sucesso! Carregando prompt...\n");
        }
        // Salvando o endereço do servidor para posterior comunicação
        this.hostRegisterServer = req.getAddress();
        this.portRegisterServer = req.getPort();
        this.hostTarget = this.hostRegisterServer;
        this.portTarget = this.portRegisterServer;
        this.isConnect = true;
        this.isRegistred = true;
    }

    private void notRegisterFlow() {
        System.out.println("Ops... Este host já se encontra registrado no servidor de registro informado!");
        this.isConnect = false; // pra nem entrar no cliente
        socket.disconnect();
        socket.close();
    }

    private void notOcuppedFlow(DataRequest req) throws IOException {
        var data = req.getData();
        this.hostTarget = req.getAddress();
        this.portTarget = req.getPort();
        this.isOcupped = true;
        var usernameSource = extractUsername(data);
        var message = data.substring(data.lastIndexOf("%") + 1);

        System.out
                .println("\n|__ Mensagem recebida de " + req.getHostAddress() + "@" + usernameSource + ": " + message);

        send(PeerStatus.OK);
    }

    private void occupedFlow(DataRequest req) throws IOException {
        var data = req.getData();
        // O servidor recebeu uma mensagem e então imprime e faz a tentativa de enviar
        // um código de status (PeerStatus.OK) para informar ao outro peer o
        // recebimento
        var usernameSource = extractUsername(data);
        var message = data.substring(data.lastIndexOf("%") + 1);

        System.out
                .println("\n|__ Mensagem recebida de " + req.getHostAddress() + "@" + usernameSource + ": " + message);

        // Tentativa de informar ao peer que enviou
        // a mensagem que ela chegou ao destino com successo
        send(PeerStatus.OK);
    }

    private void changeTarget(String data) throws NumberFormatException, UnknownHostException {
        var extract = data.split("%");
        var ip = extract[2];
        var port = extract[4];
        if (!port.equals(String.valueOf(this.portListen))) {
            this.hostTarget = InetAddress.getByName(ip);
            this.portTarget = Integer.parseInt(port);
            this.isOcupped = true;
            // Agora tá tudo pronto para conversar
            System.out.println("\n~> Agora você pode iniciar uma conversa com esse host");
        } else {
            System.err.println("\n~> Você está se conectando com você mesmo");
        }
    }

    private String prompt() throws UnknownHostException {
        // Armazena os dados que o cliente vai enviar para o servidor
        var data = "";
        // Em cada interação da prompt, verificamos se recebemos um PeerStatus.OK
        var promptSymbol = (this.promtpCheckedMessage) ? ">> " : "> ";

        // Enquanto for uma mensagem vazia (um enter por exemplo) faça a prompt aparecer
        // e esperar um input
        System.out.print(
                InetAddress.getLocalHost().getHostAddress() + ":" + portListen + "@" + this.username + promptSymbol);
        data = this.scanner.nextLine();
        // Depois que enviamos a mensagem, consideramos que não recebemos um
        // PeerStatus.OK
        this.promtpCheckedMessage = false;
        return data;
    }

    /**
     * Obtém um objeto que representa os dados de requisição recebidos pelo socket
     * 
     * @return Os dados de requisição
     */
    public DataRequest getDataRequest() {
        return new DataRequest(this.request);
    }

    /**
     * Sempre utilizado para carregarmos um novo buffer para as requisições.
     */
    public void newRequestPacket() {
        this.request = new DatagramPacket(new byte[8192], 8192);
    }

    /**
     * Este método foi construído de modo que no futuro possamos considerar um envio
     * de diferentes tipos de objetos.
     */
    public void send(Object data) throws IOException {
        // Só inicializando o buffer...
        byte[] dataSerialized = new byte[1];
        var usernameData = "%" + this.username + "%";

        if (data instanceof String) {
            // Colocando o usuário que enviou a mensagem no corpo da própria mensagem
            var dataMessage = usernameData + data;
            dataSerialized = dataMessage.getBytes();
        } else if (data instanceof PeerStatus) {
            var dataStatus = usernameData + data;
            dataSerialized = dataStatus.getBytes();
        } else if (data instanceof PeerCommand) {
            // Colocando o usuário que enviou a mensagem no corpo da própria mensagem
            var dataCommand = usernameData + data;
            dataSerialized = dataCommand.getBytes();
        }

        var dataPacket = new DatagramPacket(dataSerialized, dataSerialized.length, this.hostTarget, this.portTarget);
        this.socket.send(dataPacket);
    }

    /**
     * Verifica se dentro de determinada string temos um código de status. Este
     * método visa garantir que nenhum código de status possa seja fornecido pelo
     * cliente forçando o servidor do outro peer a executar determinadas ações não
     * desejadas.
     */
    private boolean containsPeerStatusCode(String data, PeerStatus[] peerStatus) {
        var contains = false;
        for (PeerStatus status : peerStatus) {
            if (data.equals(status.getValue())) {
                contains = true;
            }
        }

        return contains;
    }

    /**
     * Finaliza um peer caso não obtenha um código de status do outro peer
     * confirmando o fechamento. Este método tenta forçar que o peer seja fechado de
     * qualquer forma, mesmo não obtendo resposta do outro peer.
     * 
     * @throws IOException
     * @throws InterruptedException
     */
    private void autoKillThread() throws IOException, InterruptedException {
        // Se não estiver livre, envia o código.
        // Isso só acontece caso a mensagem de fechamento não chegue ao outro peer
        if (!isAlive) {
            // Enviando um código de finalização para a thread de servidor
            // deste peer, para isso reconfiguramos o novo host e port para
            // os valores do mesmo.
            // Esta parte apenas garante que o servidor seja finalizado caso não
            // receba um código de finalização do outro peer.
            this.hostTarget = InetAddress.getLocalHost();
            this.portTarget = portListen;

            send(PeerStatus.CLOSE_PEER);
            // Vamos aguardar pelo fechamento da thread do servidor
            Thread.sleep(3000);

        }
        // peer.client fechado, peer.server fechado, done!
        System.out.println("Pronto!");
        System.exit(0);
    }

    /**
     * Obtém o nome de usuário que está junto com a mensagem.
     */
    public String extractUsername(String data) {
        return data.substring(data.indexOf("%") + 1, data.lastIndexOf("%"));

    }

    public String extractEmail(String email) {
        return email.substring(email.indexOf("*") + 1, email.lastIndexOf("*"));
    }

    public void setPortListen(int portListen) {
        this.portListen = portListen;
    }

    public int getPortListen() {
        return portListen;
    }

    public InetAddress getHost() {
        return hostTarget;
    }

    public DatagramSocket getSocket() {
        return socket;
    }

    public DatagramPacket getDatagramPacket() {
        return request;
    }

    private void unregister() throws IOException {
        if (isToRegister) {
            this.hostTarget = this.hostRegisterServer;
            this.portTarget = this.portRegisterServer;
            send(PeerStatus.UNREGISTER);
        }
    }

    public void quit(String data, boolean isToServer) throws IOException {
        if (isToServer) {

            System.out.println(extractUsername(data) + " saindo da conversa... ;)");

        } else {

            System.out.println(username + " saindo da conversa... ;)");

        }

        this.hostTarget = this.hostRegisterServer;
        this.portTarget = this.portRegisterServer;
        // Configuramos novamente para o servidor de registros
        this.isOcupped = false;
        send(PeerStatus.LIST_RECORDS);

    }

    private void toRegister() throws IOException {
        if (isToRegister)
            send(PeerStatus.REGISTER);
    }


    private void toRegisterSequence() throws IOException {
        int port = 1024;
        this.socket.setBroadcast(true);
        System.out.print("=> Descobrindo servidor...");
        while (!this.isConnect && port < 65535) {
            var usernameData = "%" + this.username + "%" + "*" + this.email + "*";
            var data = usernameData + PeerStatus.REGISTER.getValue();
            var dataPacket = new DatagramPacket(data.getBytes(), data.length(), InetAddress.getByName("192.168.0.255"), port++);
            this.socket.send(dataPacket);
        }
    }

    private void discover() throws IOException {
        int port = 1024;
        this.socket.setBroadcast(true);
        while (!this.isConnect && port < 65535) {
            var usernameData = "%" + this.username + "%" + "*" + this.email + "*";
            var data = usernameData + PeerStatus.DISCOVER.getValue();
            var dataPacket = new DatagramPacket(data.getBytes(), data.length(), InetAddress.getByName("192.168.0.255"), port++);
            this.socket.send(dataPacket);
        }

    }

    private void toUnregister() throws InterruptedException, IOException {
        InetAddress tempHostTarget;
        int tempPort;
        tempHostTarget = this.hostTarget;
        tempPort = this.portTarget;
        if (this.hostTarget != this.hostRegisterServer) {

            unregister();
            Thread.sleep(3000);
            // Antes de tentar fechar vamos fazer o desregistro
            this.hostTarget = tempHostTarget;
            this.portTarget = tempPort;
            send(PeerCommand.EXIT); // só é reconhecido pelo outro peer
        } else {
            unregister();
            Thread.sleep(3000);
        }
    }
}

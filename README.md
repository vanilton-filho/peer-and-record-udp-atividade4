# peer and record server udp (Replicação e Tolerância a Falhas)

Para o funcionamento da aplicação precisamos utilizar o seguinte comando
ao executar o JAR.

> Para executar o JAR é necessário utilizar ao menos a versão 11 do JDK, recomendamos utilizar o OpenJDK 11 ou uma versão mais atual.

Para executar um servidor de registros utilize o seguinte comando:
```java
java -jar peer-and-record-server-udp-1.0-SNAPSHOT-spring-boot.jar --run-server 8080 8081
```

O seu serviçø de registros vai estar na porta `8080` e o de replicação na porta `8081`.

Para executar um peer(nó na rede), utilize o seguinte comando:
```java
java -jar peer-and-record-server-udp-1.0-SNAPSHOT-spring-boot.jar --run-peer 9090
```

A porta `9090 será utilizada para
comunicação na rede entre peers
e servidores.`

Para replicar um servidor, utilize
o seguinte comando:

```java
java -jar peer-and-record-server-udp-1.0-SNAPSHOT-spring-boot.jar --run-server --run-replicate 8082 192.168.0.111 8081
```

Considerando que `192.168.0.111`
o endereço IP onde o servidor que será replicado esteja
executando, você estará criando um
servidor que ficará disponível
na porta `8082` para registros e 
`8081` é a porta que vai requisitar
o serviço de replicação.
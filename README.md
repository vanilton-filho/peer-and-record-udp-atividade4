# peer and record server udp

Para o funcionamento da aplicação precisamos utilizar o seguinte comando
ao executar o JAR.

> Para executar o JAR é necessário utilizar ao menos a versão 11 do JDK, recomendamos utilizar o OpenJDK 11 ou uma versão mais atual.

Para executar o servidor de registros:

```bash
java -jar peer-and-record-udp.jar --run-server 8000
```

Para executar um peer:
```bash
java -jar peer-and-record-udp.jar --run-peer --to-register 8081 192.168.0.105 8000
```

Para executar você primeiro precisa executar um servidor de registros passando a flag `--run-server`. A porta passada
por parâmetro `8000` é a porta onde o servidor vai estar escutando as requisições. Ao
executar o peer vamos passar as duas flags `--run-peer` e `--to-register` e depois passar
a porta onde o peer vai ficar escutando, depois passamos o IP do servidor de registros e em seguida
passamos a porta que o servidor de registros está escutando.

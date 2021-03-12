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

Para obter a lista de registros utilize o seguinte comando `/ls-records`. Para se conectar
utilize o comando `/connect` passando o valor de domínio (a chave) por parâmetro (`/connect fbd35a7c-9b6c-4815-b7f7-4fff4a42fbd9`). Pronto, conectado
você agora pode realizar a conversação. Caso deseje sair da conversa utilize o comando `/quit`.
Para se desregistrar e finalizar o peer utilize o comando `\exit`.

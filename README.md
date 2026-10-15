# Minecraft Bot Mobile

Aplicativo Android para controlar um bot de Minecraft Java usando um backend Node.js hospedado separadamente.

## Monitoramento em primeiro plano
Na tela inicial, ative **Monitoramento em primeiro plano** para manter um serviço Android ativo após fechar a tela do app. A notificação persistente mostra se o bot conectado está ON/OFF, o nome, o servidor e a versão detectada. A notificação tem uma ação **Desativar**; o mesmo controle também existe dentro do app. A opção fica salva entre aberturas do aplicativo.

**Limite importante:** o bot Minecraft roda no backend remoto (Render), não dentro da notificação Android. O serviço mantém o app monitorando o estado do backend e mostra erros; ele não consegue impedir que o próprio servidor Minecraft, a hospedagem ou a rede desconecte o bot. O backend atual mantém apenas uma conexão Mineflayer ativa por vez, então a notificação informa o bot atualmente conectado, não uma lista de conexões simultâneas.

No Android 13 ou mais recente, autorize notificações para ver o estado. Se o sistema ou a configuração de bateria encerrar processos, o monitor pode ficar sem atualizar temporariamente.

## Compilar o aplicativo Android
1. Abra [GitHub Actions](https://github.com/mikael8367/MinecraftBotMobile/actions).
2. Abra a execução do workflow **Build Android APK** acionada pelo push mais recente.
3. Baixe o artefato `MinecraftBotMobile-debug-apk`.

## Backend
O backend deve ser executado com Node.js 22 ou superior, com a pasta de trabalho `server`. Configure `API_TOKEN` como segredo nas Environment Variables do Render e use HTTPS no app.

O endpoint público `/api/capabilities` expõe as listas de versões declaradas por cada pacote. Isso é metadado da biblioteca, não uma promessa de que o MinecraftBotMobile foi testado contra todos os servidores dessas versões.

### Testes
Na pasta `server`, execute:

```sh
npm install
npm test
```

O workflow **Backend regression tests** executa esses testes automaticamente quando os arquivos do backend mudam. O workflow Android compila o APK, mas uma compilação bem-sucedida não comprova que uma conexão real do Minecraft funcione.

## Motores
- **Mineflayer oficial**: pacote upstream PrismarineJS.
- **Mineflayer Mikael (experimental)**: adaptador deste projeto para o fork MIT `@wp2508/mineflayer`; não é uma implementação independente do protocolo.

Veja [server/COMPATIBILITY.md](server/COMPATIBILITY.md) para notas de compatibilidade e limites conhecidos.

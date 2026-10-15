# Minecraft Bot Mobile

Aplicativo Android para controlar um bot de Minecraft Java usando um backend Node.js hospedado separadamente.

## Compilar o aplicativo Android
1. Abra [GitHub Actions](https://github.com/mikael8367/MinecraftBotMobile/actions).
2. Execute **Build Android APK** ou abra a execução gerada por um push.
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

O workflow **Backend regression tests** executa esses testes automaticamente quando os arquivos do backend mudam. O workflow Android compila o APK, mas uma compilação bem-sucedida não comprova que uma conexão real do Minecraft funcionará.

## Motores
- **Mineflayer oficial**: pacote upstream PrismarineJS.
- **Mineflayer Mikael (experimental)**: adaptador deste projeto para o fork MIT `@wp2508/mineflayer`; não é uma implementação independente do protocolo.

Veja [server/COMPATIBILITY.md](server/COMPATIBILITY.md) para notas de compatibilidade e limites conhecidos.

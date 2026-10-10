# Minecraft Bot Mobile
App Android para controlar um bot do Minecraft Java em servidores por IP e porta.

## Interface
Interface reorganizada em abas **Conectar**, **Controle** e **IA**, com tema verde escuro e ilustração voxel no cabeçalho.

## Compilar no Android via GitHub Actions
1. Abra **Actions** no repositório e execute **Build Android APK**.
2. Baixe o artefato `MinecraftBotMobile-debug-apk`.
3. Configure a URL e o token do backend antes de conectar.

O app Android chama uma API Node.js com Mineflayer; o backend deve ser hospedado separadamente e ter acesso ao servidor. Não exponha a API sem `API_TOKEN` e HTTPS. A aba IA desta versão usa comandos locais simples (andar, pular, atacar e parar); não é um modelo de IA online ainda.

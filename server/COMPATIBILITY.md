# MinecraftBotMobile — auditoria e compatibilidade

## O que foi conferido
- Seleção de motor e versão no app e no backend.
- Validação de endereço, porta, usuário offline e versão.
- Ciclo de vida de conexão (spawn, erro, kick, timeout, disconnect).
- Cancelamento de temporizadores e dos controles de movimento.
- Comandos de texto, incluindo acentos em português.
- Inicialização opcional do pathfinder.
- Testes automatizados de validação, metadados e interpretação de comandos.

## Motores
- `official`: pacote upstream PrismarineJS Mineflayer.
- `mikael`: adaptador de propriedade do projeto em `server/motors/mikael.js`, usando o fork MIT `@wp2508/mineflayer`.
- O motor Mikael **não é uma implementação independente** de Minecraft. O parser de protocolo e a maior parte da lógica continuam no fork externo; o adaptador valida versões e padroniza a integração do aplicativo.

## Versões
O endpoint `GET /api/capabilities` lê `testedVersions` de cada pacote instalado. Essa lista é um indicador da matriz declarada pela biblioteca; não garante que o aplicativo ou cada servidor concreto tenha sido validado. A seleção `Auto` deixa o motor negociar a versão durante a conexão, portanto o resultado real pode divergir da versão esperada pelo usuário.

A página upstream de Mineflayer declara versões até 26.1; o fork `@wp2508/mineflayer` lista 26.1.2, 26.2 e 26.3. Não foi executado nesta auditoria um teste de login real do MinecraftBotMobile em servidor 26.3. <https://github.com/PrismarineJS/mineflayer> · <https://github.com/wupengabc/mineflayer>

## Bugs corrigidos nesta revisão
1. **Sprint sem deslocamento:** o controle de corrida era ativado sem pressionar movimento para frente. Agora o comando ativa frente e sprint com temporizadores separados.
2. **Evento atrasado de conexão antiga:** o evento `end` de um bot substituído podia limpar o temporizador de conexão do bot novo. Agora o handler verifica a identidade do bot antes de limpar temporizadores.
3. **Comandos com acentos:** o parser está isolado e normaliza texto em português antes de interpretar comandos.
4. **Versões divergentes na interface:** as opções são atualizadas a partir de `/api/capabilities` e filtradas pelo motor selecionado.
5. **Estado de conexão impreciso:** o app consulta `/api/status` após iniciar conexão, mostra o estado recebido e encerra a consulta periódica após um estado terminal.
6. **Pathfinder em versões modernas:** seu carregamento fica adiado até o spawn e não é feito para servidores detectados como 26.x; seguir jogador reporta indisponibilidade quando o pathfinder não é marcado como disponível.

## Limites atuais
- Login configurado como `auth: 'offline'`; não autentica contas Microsoft e depende de o servidor aceitar entrada offline.
- A compatibilidade de `mineflayer-pathfinder` com protocolos 26.x não foi validada; por isso o pathfinder é desativado nesses servidores.
- Os testes do projeto são testes de lógica e metadados; não substituem testes de integração contra servidores reais em cada versão.
- Use HTTPS e um `API_TOKEN` forte no Render. Não compartilhe o token em código, logs ou capturas de tela.

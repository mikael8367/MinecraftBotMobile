const express = require('express');
const mineflayer = require('mineflayer');
const { pathfinder, goals } = require('mineflayer-pathfinder');

const app = express();
app.use(express.json({ limit: '32kb' }));

const PORT = Number(process.env.PORT || 10000);
const API_TOKEN = process.env.API_TOKEN;
let bot = null;
let botInfo = { connected: false, username: null, host: null, port: null, version: null, health: null, food: null, position: null, lastError: null };

function auth(req, res, next) {
  if (!API_TOKEN) return res.status(503).json({ error: 'Backend sem configuração: defina API_TOKEN nas Environment Variables.' });
  const header = req.get('authorization') || '';
  const supplied = header.startsWith('Bearer ') ? header.slice(7) : '';
  if (supplied.length !== API_TOKEN.length || !require('crypto').timingSafeEqual(Buffer.from(supplied), Buffer.from(API_TOKEN))) {
    return res.status(401).json({ error: 'Token da API inválido.' });
  }
  next();
}
function status() {
  if (bot) {
    botInfo.connected = bot.entity != null;
    botInfo.health = bot.health ?? null;
    botInfo.food = bot.food ?? null;
    const p = bot.entity?.position;
    botInfo.position = p ? { x: Math.round(p.x * 100) / 100, y: Math.round(p.y * 100) / 100, z: Math.round(p.z * 100) / 100 } : null;
  }
  return botInfo;
}
app.get('/', (_req, res) => res.json({ service: 'MinecraftBotMobile backend', ok: true }));
app.get('/health', (_req, res) => res.json({ ok: true }));
app.get('/api/status', auth, (_req, res) => res.json(status()));

app.post('/api/connect', auth, (req, res) => {
  const host = String(req.body.host || '').trim();
  const port = Number(req.body.port || 25565);
  const username = String(req.body.username || 'MikaelBot').trim().slice(0, 16);
  const requestedVersion = String(req.body.version || 'auto');
  if (!host || host.length > 253) return res.status(400).json({ error: 'Informe um endereço de servidor válido.' });
  if (!Number.isInteger(port) || port < 1 || port > 65535) return res.status(400).json({ error: 'Porta inválida.' });
  if (!username) return res.status(400).json({ error: 'Nome do bot inválido.' });
  if (bot) { try { bot.quit('Nova conexão solicitada'); } catch (_) {} bot = null; }
  botInfo = { connected: false, username, host, port, version: requestedVersion, health: null, food: null, position: null, lastError: null };
  try {
    const activeBot = mineflayer.createBot({ host, port, username, version: requestedVersion === 'auto' ? false : requestedVersion, auth: 'offline', hideErrors: false });
    bot = activeBot;
    activeBot.loadPlugin(pathfinder);
    activeBot.once('spawn', () => {
      if (bot !== activeBot) return;
      botInfo.connected = true;
      botInfo.lastError = null;
    });
    activeBot.on('end', () => {
      if (bot !== activeBot) return;
      botInfo.connected = false;
      botInfo.position = null;
    });
    activeBot.on('kicked', (reason) => {
      if (bot !== activeBot) return;
      botInfo.connected = false;
      botInfo.lastError = 'Conexão recusada: ' + String(reason).slice(0, 300);
    });
    activeBot.on('error', (err) => {
      if (bot !== activeBot) return;
      botInfo.lastError = String(err.message || err).slice(0, 300);
    });
    const connectionTimer = setTimeout(() => {
      if (bot === activeBot && !activeBot.entity) {
        botInfo.lastError = 'Tempo limite de conexão excedido. Confira o endereço, a porta, a versão e se o servidor Aternos está ligado.';
        try { activeBot.quit('Tempo limite de conexão'); } catch (_) {}
      }
    }, 25000);
    activeBot.once('spawn', () => clearTimeout(connectionTimer));
    activeBot.once('end', () => clearTimeout(connectionTimer));
    return res.json({ ok: true, message: 'Tentativa de conexão iniciada. Consulte /api/status.', status: status() });
  } catch (err) {
    botInfo.lastError = String(err.message || err);
    return res.status(500).json({ error: botInfo.lastError });
  }
});

app.post('/api/action', auth, (req, res) => {
  if (!bot || !bot.entity) return res.status(409).json({ error: 'O bot ainda não está conectado.' });
  const activeBot = bot;
  const action = String(req.body.action || '');
  const controls = ['forward', 'back', 'left', 'right', 'jump', 'sprint'];
  if (controls.includes(action)) {
    activeBot.setControlState(action, true);
    const stopAfter = action === 'sprint' ? 1200 : 700;
    setTimeout(() => {
      if (bot === activeBot) {
        try { activeBot.setControlState(action, false); } catch (_) {}
      }
    }, stopAfter);
    return res.json({ ok: true, message: 'Ação executada: ' + action });
  }
  if (action === 'stop') {
    for (const c of ['forward','back','left','right','jump','sprint','sneak']) bot.setControlState(c, false);
    return res.json({ ok: true, message: 'Movimento parado.' });
  }
  if (action === 'attack') {
    const target = bot.nearestEntity(e => e.type === 'player' && e.username !== bot.username && e.position.distanceTo(bot.entity.position) < 4);
    if (!target) return res.status(404).json({ error: 'Nenhum jogador por perto para atacar.' });
    bot.attack(target);
    return res.json({ ok: true, message: 'Ataque enviado.' });
  }
  if (action === 'follow') {
    const name = String(req.body.target || '').trim();
    const player = bot.players[name]?.entity;
    if (!name || !player) return res.status(404).json({ error: 'Jogador não encontrado ou não está visível.' });
    bot.pathfinder.setGoal(new goals.GoalFollow(player, 2), true);
    return res.json({ ok: true, message: 'Seguindo ' + name });
  }
  return res.status(400).json({ error: 'Ação desconhecida.' });
});

app.post('/api/chat', auth, (req, res) => {
  if (!bot || !bot.entity) return res.status(409).json({ error: 'Conecte o bot a um servidor primeiro.' });
  const activeBot = bot;
  const message = String(req.body.message || '').trim().toLowerCase();
  if (!message) return res.status(400).json({ error: 'Digite um comando.' });
  let action = null;
  if (/\b(parar|pare|stop|fica parado)\b/.test(message)) action = 'stop';
  else if (/\b(pular|pule|jump)\b/.test(message)) action = 'jump';
  else if (/\b(atacar|ataque|bater)\b/.test(message)) action = 'attack';
  else if (/\b(frente|andar|avançar|avancar|andar para frente)\b/.test(message)) action = 'forward';
  else if (/\b(ré|re|voltar|trás|tras)\b/.test(message)) action = 'back';
  else if (/\b(esquerda)\b/.test(message)) action = 'left';
  else if (/\b(direita)\b/.test(message)) action = 'right';
  else if (/\b(correr|sprint)\b/.test(message)) action = 'sprint';
  if (!action) return res.json({ ok: true, message: 'Ainda não entendi esse comando. Tente: andar, pular, correr, esquerda, direita, atacar ou parar.' });
  req.body.action = action;
  // Execute through the same action logic without repeating HTTP auth.
  if (action === 'stop') {
    for (const c of ['forward','back','left','right','jump','sprint','sneak']) bot.setControlState(c, false);
  } else if (action === 'attack') {
    const target = bot.nearestEntity(e => e.type === 'player' && e.username !== bot.username && e.position.distanceTo(bot.entity.position) < 4);
    if (!target) return res.json({ ok: false, message: 'Não há jogador por perto para atacar.' });
    bot.attack(target);
  } else {
    bot.setControlState(action, true);
    setTimeout(() => {
      if (bot === activeBot) {
        try { activeBot.setControlState(action, false); } catch (_) {}
      }
    }, 700);
  }
  return res.json({ ok: true, message: 'Comando executado: ' + action });
});

app.post('/api/disconnect', auth, (_req, res) => {
  if (bot) { try { bot.quit('Desconectado pelo aplicativo'); } catch (_) {} bot = null; }
  botInfo.connected = false;
  botInfo.position = null;
  res.json({ ok: true, message: 'Bot desconectado.' });
});

app.listen(PORT, '0.0.0.0', () => console.log('MinecraftBotMobile backend ouvindo na porta ' + PORT));

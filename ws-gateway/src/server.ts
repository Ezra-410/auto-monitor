import http from 'node:http';
import { WebSocketServer, WebSocket } from 'ws';
import { config } from './config.js';
import { verifyToken, jwksReachable } from './auth.js';
import { Hub, GatewayEvent } from './hub.js';

const hub = new Hub();
let wssReady = false;

function json(res: http.ServerResponse, code: number, body: unknown): void {
  res.writeHead(code, { 'content-type': 'application/json' });
  res.end(JSON.stringify(body));
}

async function backendAlive(): Promise<boolean> {
  try {
    const res = await fetch(`${config.backendUrl}/actuator/health/liveness`, {
      signal: AbortSignal.timeout(3000),
    });
    return res.ok;
  } catch {
    return false;
  }
}

async function forwardChatMessage(token: string, msg: Record<string, unknown>): Promise<void> {
  const res = await fetch(`${config.backendUrl}/api/v1/chat/messages`, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(msg),
    signal: AbortSignal.timeout(10000),
  });
  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new Error(`backend ${res.status}: ${text.slice(0, 200)}`);
  }
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url ?? '/', `http://${req.headers.host ?? 'localhost'}`);

  if (req.method === 'GET' && url.pathname === '/healthz') {
    json(res, 200, { status: 'ok' });
    return;
  }

  if (req.method === 'GET' && url.pathname === '/readyz') {
    void (async () => {
      const [jwksOk, backendOk] = await Promise.all([jwksReachable(), backendAlive()]);
      const ok = wssReady && jwksOk && backendOk;
      json(res, ok ? 200 : 503, {
        status: ok ? 'ready' : 'not_ready',
        ws_server: wssReady,
        jwks: jwksOk,
        backend: backendOk,
        clients: hub.size,
      });
    })();
    return;
  }

  if (req.method === 'POST' && url.pathname === '/internal/events') {
    if (req.headers['x-internal-token'] !== config.internalToken || !config.internalToken) {
      json(res, 401, { error: 'unauthorized' });
      return;
    }
    let body = '';
    req.on('data', (c) => (body += c));
    req.on('end', () => {
      try {
        const event = JSON.parse(body) as GatewayEvent;
        if (typeof event.type !== 'string' || !event.type) {
          json(res, 400, { error: 'event.type requis' });
          return;
        }
        const delivered = hub.broadcast(event);
        json(res, 202, { delivered });
      } catch {
        json(res, 400, { error: 'JSON invalide' });
      }
    });
    return;
  }

  json(res, 404, { error: 'not found' });
});

const wss = new WebSocketServer({ noServer: true });

server.on('upgrade', (req, socket, head) => {
  const url = new URL(req.url ?? '/', `http://${req.headers.host ?? 'localhost'}`);
  if (url.pathname !== '/ws') {
    socket.destroy();
    return;
  }
  const origin = req.headers.origin ?? '';
  if (config.allowedOrigin && origin && origin !== config.allowedOrigin) {
    socket.destroy();
    return;
  }
  const token = url.searchParams.get('token') ?? '';
  if (!token) {
    socket.destroy();
    return;
  }
  void verifyToken(token)
    .then((identity) => {
      wss.handleUpgrade(req, socket, head, (ws) => {
        wss.emit('connection', ws, req, identity, token);
      });
    })
    .catch(() => socket.destroy());
});

wss.on('connection', (ws: WebSocket, _req: http.IncomingMessage, identity: { userId: number; username: string }, token: string) => {
  hub.add(ws, { userId: identity.userId, username: identity.username, token, projects: new Set() });
  ws.send(JSON.stringify({ type: 'connected', payload: { user_id: identity.userId, username: identity.username } }));

  ws.on('message', async (data) => {
    let msg: Record<string, unknown>;
    try {
      msg = JSON.parse(String(data));
    } catch {
      ws.send(JSON.stringify({ type: 'error', payload: { error: 'JSON invalide' } }));
      return;
    }
    try {
      switch (msg.type) {
        case 'chat.message': {
          const meta = hub.meta(ws);
          if (meta) {
            if (typeof msg.project_id === 'number') hub.subscribe(ws, msg.project_id);
            await forwardChatMessage(meta.token, msg);
          }
          break;
        }
        case 'subscribe':
          if (typeof msg.project_id === 'number') hub.subscribe(ws, msg.project_id);
          break;
        case 'unsubscribe':
          if (typeof msg.project_id === 'number') hub.unsubscribe(ws, msg.project_id);
          break;
        case 'ping':
          ws.send(JSON.stringify({ type: 'pong' }));
          break;
        default:
          ws.send(JSON.stringify({ type: 'error', payload: { error: `type inconnu: ${String(msg.type)}` } }));
      }
    } catch (e) {
      ws.send(JSON.stringify({ type: 'error', payload: { error: e instanceof Error ? e.message : 'erreur interne' } }));
    }
  });

  ws.on('close', () => hub.remove(ws));
  ws.on('error', () => hub.remove(ws));
});

server.listen(config.port, () => {
  wssReady = true;
  console.log(`ws-gateway à l'écoute sur :${config.port}`);
});

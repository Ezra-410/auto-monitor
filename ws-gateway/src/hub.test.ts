import { describe, it, expect } from 'vitest';
import { Hub, GatewayEvent } from './hub.js';
import type { WebSocket } from 'ws';

function fakeSocket(): WebSocket & { sent: string[] } {
  const sent: string[] = [];
  return {
    sent,
    readyState: 1,
    OPEN: 1,
    send(data: string) {
      sent.push(data);
    },
  } as unknown as WebSocket & { sent: string[] };
}

function meta(userId: number): { userId: number; username: string; token: string; projects: Set<number> } {
  return { userId, username: `u${userId}`, token: 't', projects: new Set() };
}

describe('Hub.broadcast', () => {
  it('route un événement user_id uniquement vers cet utilisateur', () => {
    const hub = new Hub();
    const s1 = fakeSocket();
    const s2 = fakeSocket();
    hub.add(s1, meta(1));
    hub.add(s2, meta(2));
    const event: GatewayEvent = { type: 'message.created', user_id: 1 };
    expect(hub.broadcast(event)).toBe(1);
    expect(s1.sent).toHaveLength(1);
    expect(s2.sent).toHaveLength(0);
  });

  it('route un événement project_id vers les abonnés du projet', () => {
    const hub = new Hub();
    const s1 = fakeSocket();
    const s2 = fakeSocket();
    hub.add(s1, meta(1));
    hub.add(s2, meta(2));
    hub.subscribe(s1, 7);
    const delivered = hub.broadcast({ type: 'task.updated', project_id: 7 });
    expect(delivered).toBe(1);
    expect(s1.sent).toHaveLength(1);
    expect(s2.sent).toHaveLength(0);
  });

  it('diffuse à tous quand ni user_id ni project_id', () => {
    const hub = new Hub();
    const s1 = fakeSocket();
    const s2 = fakeSocket();
    hub.add(s1, meta(1));
    hub.add(s2, meta(2));
    expect(hub.broadcast({ type: 'notification' })).toBe(2);
  });

  it('ignore les sockets fermées et les supprime proprement', () => {
    const hub = new Hub();
    const s1 = fakeSocket();
    hub.add(s1, meta(1));
    hub.remove(s1);
    expect(hub.size).toBe(0);
    expect(hub.broadcast({ type: 'notification' })).toBe(0);
  });
});

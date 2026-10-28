import type { WebSocket } from 'ws';

export interface ClientMeta {
  userId: number;
  username: string;
  token: string;
  projects: Set<number>;
}

export interface GatewayEvent {
  type: string;
  project_id?: number;
  user_id?: number;
  payload?: unknown;
}

/**
 * Registre des connexions WebSocket et routage des événements.
 * - event.user_id  → sockets de cet utilisateur
 * - event.project_id → sockets abonnées à ce projet
 * - sinon → diffusion à tous
 */
export class Hub {
  private clients = new Map<WebSocket, ClientMeta>();

  add(socket: WebSocket, meta: ClientMeta): void {
    this.clients.set(socket, meta);
  }

  remove(socket: WebSocket): void {
    this.clients.delete(socket);
  }

  subscribe(socket: WebSocket, projectId: number): void {
    this.clients.get(socket)?.projects.add(projectId);
  }

  unsubscribe(socket: WebSocket, projectId: number): void {
    this.clients.get(socket)?.projects.delete(projectId);
  }

  meta(socket: WebSocket): ClientMeta | undefined {
    return this.clients.get(socket);
  }

  get size(): number {
    return this.clients.size;
  }

  broadcast(event: GatewayEvent): number {
    let delivered = 0;
    const raw = JSON.stringify(event);
    for (const [socket, meta] of this.clients) {
      const match =
        (event.user_id != null && meta.userId === event.user_id) ||
        (event.user_id == null && event.project_id != null && meta.projects.has(event.project_id)) ||
        (event.user_id == null && event.project_id == null);
      if (match && socket.readyState === socket.OPEN) {
        socket.send(raw);
        delivered++;
      }
    }
    return delivered;
  }
}

import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

class WebSocketService {
  private client: Client | null = null;
  private connected = false;
  private subscriptions: Map<string, any> = new Map();

  public connect(onConnect?: () => void) {
    if (this.client && this.connected) {
      if (onConnect) onConnect();
      return;
    }

    this.client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        this.connected = true;
        if (onConnect) onConnect();
      },
      onDisconnect: () => {
        this.connected = false;
      },
      onStompError: (frame) => {
        console.warn('STOMP broker error:', frame.headers['message']);
      }
    });

    this.client.activate();
  }

  public subscribe(topic: string, callback: (data: any) => void): () => void {
    const doSubscribe = () => {
      if (!this.client || !this.connected) return;
      
      // Unsubscribe previous if exists
      if (this.subscriptions.has(topic)) {
        this.subscriptions.get(topic).unsubscribe();
      }

      const sub = this.client.subscribe(topic, (message) => {
        try {
          const payload = JSON.parse(message.body);
          callback(payload);
        } catch {
          callback(message.body);
        }
      });
      this.subscriptions.set(topic, sub);
    };

    if (this.connected) {
      doSubscribe();
    } else {
      this.connect(doSubscribe);
    }

    // Return cleanup unsubscribe function
    return () => {
      if (this.subscriptions.has(topic)) {
        this.subscriptions.get(topic).unsubscribe();
        this.subscriptions.delete(topic);
      }
    };
  }

  public disconnect() {
    if (this.client) {
      this.subscriptions.forEach((sub) => sub.unsubscribe());
      this.subscriptions.clear();
      this.client.deactivate();
      this.client = null;
      this.connected = false;
    }
  }
}

export const wsService = new WebSocketService();

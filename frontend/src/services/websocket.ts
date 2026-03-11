import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

class WebSocketService {
  private client: Client | null = null;
  private subscriptions: Map<string, StompSubscription> = new Map();
  private connected: boolean = false;
  private connectionPromise: Promise<void> | null = null;

  connect(): Promise<void> {
    if (this.connected) {
      return Promise.resolve();
    }

    if (this.connectionPromise) {
      return this.connectionPromise;
    }

    this.connectionPromise = new Promise((resolve, reject) => {
      this.client = new Client({
        webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
        debug: (str) => console.log('STOMP: ' + str),
        reconnectDelay: 5000,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        onConnect: () => {
          console.log('WebSocket connected');
          this.connected = true;
          resolve();
        },
        onStompError: (frame) => {
          console.error('STOMP error:', frame);
          reject(new Error(frame.headers['message']));
        },
        onDisconnect: () => {
          console.log('WebSocket disconnected');
          this.connected = false;
          this.connectionPromise = null;
        },
      });

      this.client.activate();
    });

    return this.connectionPromise;
  }

  disconnect(): void {
    if (this.client) {
      this.subscriptions.forEach((sub) => sub.unsubscribe());
      this.subscriptions.clear();
      this.client.deactivate();
      this.client = null;
      this.connected = false;
      this.connectionPromise = null;
    }
  }

  subscribe(destination: string, callback: (message: IMessage) => void): void {
    if (!this.client || !this.connected) {
      console.error('WebSocket not connected');
      return;
    }

    if (this.subscriptions.has(destination)) {
      return;
    }

    const subscription = this.client.subscribe(destination, callback);
    this.subscriptions.set(destination, subscription);
  }

  unsubscribe(destination: string): void {
    const subscription = this.subscriptions.get(destination);
    if (subscription) {
      subscription.unsubscribe();
      this.subscriptions.delete(destination);
    }
  }

  send(destination: string, body: object): void {
    if (!this.client || !this.connected) {
      console.error('WebSocket not connected');
      return;
    }

    this.client.publish({
      destination,
      body: JSON.stringify(body),
    });
  }

  isConnected(): boolean {
    return this.connected;
  }
}

export const websocketService = new WebSocketService();

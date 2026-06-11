import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const WS_URL = process.env.REACT_APP_WS_URL || 'http://localhost:8080/ws';

let stompClient = null;
const subscribers = {};

export function connectWebSocket(onConnect) {
  stompClient = new Client({
    webSocketFactory: () => new SockJS(WS_URL),
    reconnectDelay: 3000,
    onConnect: () => {
      console.log('[WS] Connected to traffic intelligence backend');
      if (onConnect) onConnect();
    },
    onDisconnect: () => console.log('[WS] Disconnected'),
    onStompError: (err) => console.error('[WS] STOMP error:', err),
  });

  stompClient.activate();
  return stompClient;
}

export function subscribe(topic, callback) {
  if (!stompClient || !stompClient.connected) {
    console.warn('[WS] Cannot subscribe — not connected yet');
    return null;
  }
  const sub = stompClient.subscribe(topic, (msg) => {
    try {
      callback(JSON.parse(msg.body));
    } catch (e) {
      console.error('[WS] Parse error:', e);
    }
  });
  subscribers[topic] = sub;
  return sub;
}

export function unsubscribe(topic) {
  if (subscribers[topic]) {
    subscribers[topic].unsubscribe();
    delete subscribers[topic];
  }
}

export function disconnectWebSocket() {
  if (stompClient) stompClient.deactivate();
}

export function isConnected() {
  return stompClient?.connected ?? false;
}

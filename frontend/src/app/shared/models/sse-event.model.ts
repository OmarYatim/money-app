export const SSE_EVENT_TYPES = ['ACCOUNTS_UPDATED', 'TRANSACTIONS_UPDATED'] as const;

export type SseEventType = typeof SSE_EVENT_TYPES[number];

export type SseConnectionStatus = 'idle' | 'connecting' | 'connected' | 'reconnecting' | 'failed';

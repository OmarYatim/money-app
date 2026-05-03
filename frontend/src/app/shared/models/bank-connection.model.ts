export interface BankConnectResponse {
  webviewUrl: string;
  state: string;
}

export interface BankConnectionCallbackResponse {
  status: 'connected' | 'cancelled';
  message: string;
  connectionIds: number[];
}

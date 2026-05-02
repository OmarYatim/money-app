export interface ConnectionRequiringAction {
  connectionId: number;
  state: string;
}

export interface SyncStatus {
  connectionsRequiringAction: ConnectionRequiringAction[];
}

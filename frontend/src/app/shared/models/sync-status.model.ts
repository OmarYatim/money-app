export interface ConnectionRequiringAction {
  connectionId: number;
  state: string;
}

export interface SyncStatus {
  lastSyncedAt: string | null;
  connectionsRequiringAction: ConnectionRequiringAction[];
  hasSyncError: boolean;
}

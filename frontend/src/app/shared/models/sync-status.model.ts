export interface ConnectionRequiringAction {
  connectionId: number;
  state: string;
  errorMessage: string | null;
}

export interface SyncStatus {
  lastSyncedAt: string | null;
  connectionsRequiringAction: ConnectionRequiringAction[];
  hasSyncError: boolean;
}

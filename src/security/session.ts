import * as SecureStore from 'expo-secure-store';
const MARKER = 'devvault.installation.marker.v1';
let backgroundedAt: number | null = null;
export async function initializeSecurity(): Promise<void> {
  if (!await SecureStore.isAvailableAsync()) throw new Error('SECURE_STORE_UNAVAILABLE');
  await SecureStore.setItemAsync(MARKER, 'ready', { keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY });
}
export function markBackgrounded(now = Date.now()): void { backgroundedAt = now; }
export function shouldLockOnResume(now = Date.now(), timeoutMs = 30_000): boolean { return backgroundedAt !== null && now - backgroundedAt >= timeoutMs; }
export function lockGate(): void { backgroundedAt = Date.now(); }

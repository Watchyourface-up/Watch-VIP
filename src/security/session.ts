import * as SecureStore from 'expo-secure-store';

const MARKER = 'devvault.installation.marker.v1';
const LOCK_TIMEOUT_KEY = 'devvault.lock.timeout.v1';
export const LOCK_TIMEOUT_OPTIONS = [0, 15_000, 30_000, 60_000] as const;
export type LockTimeoutMs = typeof LOCK_TIMEOUT_OPTIONS[number];
let backgroundedAt: number | null = null;

export async function initializeSecurity(): Promise<void> {
  if (!await SecureStore.isAvailableAsync()) throw new Error('SECURE_STORE_UNAVAILABLE');
  await SecureStore.setItemAsync(MARKER, 'ready', {
    keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
  });
}

export async function getLockTimeoutMs(): Promise<LockTimeoutMs> {
  const stored = await SecureStore.getItemAsync(LOCK_TIMEOUT_KEY);
  const parsed = Number(stored ?? '0');
  return (LOCK_TIMEOUT_OPTIONS as readonly number[]).includes(parsed) ? parsed as LockTimeoutMs : 0;
}

export async function setLockTimeoutMs(timeout: LockTimeoutMs): Promise<void> {
  if (!(LOCK_TIMEOUT_OPTIONS as readonly number[]).includes(timeout)) throw new Error('INVALID_LOCK_TIMEOUT');
  await SecureStore.setItemAsync(LOCK_TIMEOUT_KEY, String(timeout), {
    keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
  });
}

export function markBackgrounded(now = Date.now()): void {
  backgroundedAt = now;
}

/**
 * Consume the background timestamp on resume so a later lifecycle cycle always
 * measures its own duration rather than reusing a stale timestamp.
 */
export function shouldLockOnResume(now = Date.now(), timeoutMs = 0): boolean {
  if (backgroundedAt === null) return false;
  const resumedAfter = backgroundedAt;
  backgroundedAt = null;
  return now - resumedAfter >= timeoutMs;
}

export function lockGate(): void {
  backgroundedAt = Date.now();
}

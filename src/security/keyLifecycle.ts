export type DatabaseKeyState =
  | 'reuse-existing'
  | 'create-first-key'
  | 'missing-after-initialization'
  | 'invalid-existing-key';

export function decideDatabaseKeyState(
  key: string | null,
  initializedMarker: string | null,
): DatabaseKeyState {
  if (key !== null) {
    // An empty or malformed stored value is not equivalent to a first install.
    // Refuse to replace it automatically: the database may already depend on it.
    if (key.length === 0 || !/^[a-f0-9]{64}$/.test(key)) return 'invalid-existing-key';
    return 'reuse-existing';
  }
  if (initializedMarker === '1') return 'missing-after-initialization';
  return 'create-first-key';
}

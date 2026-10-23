import {decideDatabaseKeyState} from '../src/security/keyLifecycle';

describe('database key lifecycle policy', () => {
  it('reuses an existing valid key even if an older install lacks the marker', () => {
    expect(decideDatabaseKeyState('a'.repeat(64), null)).toBe('reuse-existing');
  });

  it('creates a key only for a genuinely uninitialized install', () => {
    expect(decideDatabaseKeyState(null, null)).toBe('create-first-key');
  });

  it('never generates a replacement after initialization marker exists', () => {
    expect(decideDatabaseKeyState(null, '1')).toBe('missing-after-initialization');
  });

  it.each(['', 'short-key', 'A'.repeat(64)])(
    'refuses to overwrite malformed stored key material',
    (key) => expect(decideDatabaseKeyState(key, null)).toBe('invalid-existing-key'),
  );
});

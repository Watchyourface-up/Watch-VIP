import {decideDatabaseKeyState} from '../src/security/keyLifecycle';

describe('database key lifecycle policy',()=>{
 it('reuses any existing stored key even if an older install lacks the marker',()=>{
  expect(decideDatabaseKeyState('existing-key',null)).toBe('reuse-existing');
 });
 it('creates a key only for a genuinely uninitialized install',()=>{
  expect(decideDatabaseKeyState(null,null)).toBe('create-first-key');
  expect(decideDatabaseKeyState('',null)).toBe('create-first-key');
 });
 it('never generates a replacement after initialization marker exists',()=>{
  expect(decideDatabaseKeyState(null,'1')).toBe('missing-after-initialization');
 });
});

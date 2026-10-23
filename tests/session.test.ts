import {markBackgrounded,shouldLockOnResume,lockGate} from '../src/security/session';
describe('app lock timeout policy',()=>{
 it('does not lock before a background timestamp exists',()=>{expect(shouldLockOnResume(100000)).toBe(false);});
 it('locks after the timeout',()=>{markBackgrounded(1000);expect(shouldLockOnResume(2000,30000)).toBe(false);expect(shouldLockOnResume(31000,30000)).toBe(true);});
 it('restarts the timeout when explicitly locked',()=>{markBackgrounded(1000);lockGate();expect(shouldLockOnResume(Date.now()+31000,30000)).toBe(true);});
});

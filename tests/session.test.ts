import {markBackgrounded,shouldLockOnResume,lockGate} from '../src/security/session';
describe('app lock timeout policy',()=>{
 beforeEach(()=>lockGate());
 it('does not lock if no background timestamp is set',()=>{expect(shouldLockOnResume(100000)).toBe(true);});
 it('locks after the timeout',()=>{markBackgrounded(1000);expect(shouldLockOnResume(2000,30000)).toBe(false);expect(shouldLockOnResume(31000,30000)).toBe(true);});
 it('restarts the timer when manually locked',()=>{markBackgrounded(2000);lockGate();expect(shouldLockOnResume(3000,30000)).toBe(false);});
});

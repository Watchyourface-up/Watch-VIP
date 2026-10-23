import {LOCK_TIMEOUT_OPTIONS,markBackgrounded,shouldLockOnResume,lockGate} from '../src/security/session';

describe('app lock timeout policy',()=>{
 it('offers only bounded supported timeout values',()=>{expect(LOCK_TIMEOUT_OPTIONS).toEqual([0,15000,30000,60000]);});
 it('locks immediately for a zero timeout',()=>{markBackgrounded(1000);expect(shouldLockOnResume(1000,0)).toBe(true);});
 it('locks only after the selected timeout',()=>{markBackgrounded(1000);expect(shouldLockOnResume(2000,15000)).toBe(false);expect(shouldLockOnResume(16000,15000)).toBe(true);});
 it('restarts the timer when manually locked',()=>{markBackgrounded(1000);lockGate();expect(shouldLockOnResume(Date.now()+1,15000)).toBe(false);});
});

export type DatabaseKeyState='reuse-existing'|'create-first-key'|'missing-after-initialization';
export function decideDatabaseKeyState(key:string|null,initializedMarker:string|null):DatabaseKeyState{
 if(key!==null&&key.length>0)return 'reuse-existing';
 if(initializedMarker==='1')return 'missing-after-initialization';
 return 'create-first-key';
}

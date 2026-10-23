export type UrlValidation = { ok: true; value: string } | { ok: false; message: string };
const CONTROL_OR_SPACE = /[\\u0000-\\u0020\\u007f]/;
const SENSITIVE_PARAMETER = /^(?:access[_-]?token|refresh[_-]?token|id[_-]?token|token|api[_-]?key|apikey|client[_-]?secret|password|passwd|authorization|auth|session|jwt|sig|signature|code|key)$/i;

export function normalizeHttpUrl(input: string): UrlValidation {
 const candidate=input.trim();
 if(!candidate)return {ok:false,message:'أدخل عنوان رابط أولًا.'};
 if(CONTROL_OR_SPACE.test(candidate))return {ok:false,message:'يحتوي الرابط على مسافات أو محارف غير مسموحة.'};
 let parsed:URL;
 try{parsed=new URL(candidate);}catch{return {ok:false,message:'صيغة الرابط غير صحيحة. استخدم رابطًا كاملًا مثل https://example.com.'};}
 if(parsed.protocol!=='https:'&&parsed.protocol!=='http:')return {ok:false,message:'يُسمح بروابط HTTP وHTTPS فقط.'};
 if(!parsed.hostname||parsed.hostname.startsWith('.')||parsed.hostname.endsWith('.'))return {ok:false,message:'يجب أن يحتوي الرابط على اسم مضيف صالح.'};
 if(parsed.username||parsed.password)return {ok:false,message:'لا يُسمح بحفظ بيانات دخول مضمّنة داخل الرابط.'};
 if(parsed.hostname==='localhost'||/^127(?:\\.\\d{1,3}){3}$/.test(parsed.hostname)||/^10(?:\\.\\d{1,3}){3}$/.test(parsed.hostname)||/^192\\.168(?:\\.\\d{1,3}){2}$/.test(parsed.hostname)||/^169\\.254(?:\\.\\d{1,3}){2}$/.test(parsed.hostname)) {
  // Private/internal hosts may be legitimate; accepting them is intentional because URL opening is a user action.
 }
 return {ok:true,value:parsed.toString()};
}
export function detectSensitiveUrl(value:string):boolean {
 try {
  const parsed=new URL(value);
  if(parsed.username||parsed.password)return true;
  for(const key of parsed.searchParams.keys())if(SENSITIVE_PARAMETER.test(key))return true;
  return /(?:^|[/?#&;])(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|sk-[A-Za-z0-9]{20,})/i.test(value);
 } catch{return false;}
}

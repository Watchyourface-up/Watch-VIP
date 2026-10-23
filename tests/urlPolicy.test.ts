import {normalizeHttpUrl,detectSensitiveUrl} from '../src/domain/urlPolicy';
import {validateLinkDraft} from '../src/domain/linkValidation';

describe('DevVault link policies',()=>{
 test('accepts https and canonicalizes host casing',()=>{
  expect(normalizeHttpUrl('https://EXAMPLE.com')).toEqual({ok:true,value:'https://example.com/'});
  expect(normalizeHttpUrl('http://example.com').ok).toBe(true);
 });
 test.each(['javascript:alert(1)','file:///etc/passwd','ftp://example.com','not a url'])('rejects unsafe scheme or malformed input: %s',(input)=>{
  expect(normalizeHttpUrl(input).ok).toBe(false);
 });
 test('rejects whitespace and embedded credentials',()=>{
  expect(normalizeHttpUrl('https://example.com/a b').ok).toBe(false);
  expect(normalizeHttpUrl('https://user:pass@example.com').ok).toBe(false);
 });
 test('warns for common token and key parameters',()=>{
  expect(detectSensitiveUrl('https://example.com/?access_token=abc')).toBe(true);
  expect(detectSensitiveUrl('https://example.com/?api_key=abc')).toBe(true);
  expect(detectSensitiveUrl('https://example.com/docs')).toBe(false);
  expect(detectSensitiveUrl('https://example.com/?token_description=hello')).toBe(false);
 });
 test('validates title and description limits',()=>{
  const base={title:' TypeScript docs ',url:'https://www.typescriptlang.org/docs',description:' official ',category:'docs' as const};
  const ok=validateLinkDraft(base,undefined,123);
  expect(ok.ok).toBe(true);
  if(ok.ok){expect(ok.record.title).toBe('TypeScript docs');expect(ok.record.description).toBe('official');expect(ok.record.createdAt).toBe(123);}
  expect(validateLinkDraft({...base,title:'x'.repeat(121)}).ok).toBe(false);
  expect(validateLinkDraft({...base,description:'x'.repeat(501)}).ok).toBe(false);
 });
});

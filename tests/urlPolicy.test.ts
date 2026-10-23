import { detectSensitiveUrl, normalizeHttpUrl } from '../src/domain/urlPolicy';
describe('URL policy', () => {
 it('accepts and canonicalizes HTTPS and HTTP', () => {
  expect(normalizeHttpUrl('https://EXAMPLE.com')).toEqual({ok:true,value:'https://example.com/'});
  expect(normalizeHttpUrl('http://example.com').ok).toBe(true);
 });
 it('rejects non-web schemes and malformed input', () => {
  expect(normalizeHttpUrl('javascript:alert(1)').ok).toBe(false);
  expect(normalizeHttpUrl('file:///etc/passwd').ok).toBe(false);
  expect(normalizeHttpUrl('not a url').ok).toBe(false);
 });
 it('rejects whitespace and embedded credentials', () => {
  expect(normalizeHttpUrl('https://example.com/a b').ok).toBe(false);
  expect(normalizeHttpUrl('https://user:pass@example.com').ok).toBe(false);
 });
 it('detects common secret-looking query names but does not flag ordinary docs', () => {
  expect(detectSensitiveUrl('https://example.com/?access_token=abc')).toBe(true);
  expect(detectSensitiveUrl('https://example.com/?api_key=abc')).toBe(true);
  expect(detectSensitiveUrl('https://example.com/docs')).toBe(false);
 });
 it('only flags exact sensitive parameter names', () => {
  expect(detectSensitiveUrl('https://example.com/?token_description=overview')).toBe(false);
  expect(detectSensitiveUrl('https://example.com/?code=123')).toBe(true);
 });
});

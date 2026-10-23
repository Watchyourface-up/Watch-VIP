import {detectDuplicateUrl, getDuplicateUrlKey, normalizeHttpUrl, detectSensitiveUrl} from '../src/domain/urlPolicy';
import {validateLinkDraft} from '../src/domain/linkValidation';

describe('DevVault link policies', () => {
  test('accepts http and https and canonicalizes hostname casing', () => {
    const accepted = normalizeHttpUrl('https://EXAMPLE.com');
    expect(accepted.ok).toBe(true);
    if (accepted.ok) expect(new URL(accepted.value).hostname).toBe('example.com');
    expect(normalizeHttpUrl('http://example.com').ok).toBe(true);
  });

  test.each(['javascript:alert(1)', 'file:///etc/passwd', 'ftp://example.com', 'not a url'])(
    'rejects unsafe scheme or malformed input: %s',
    (input) => expect(normalizeHttpUrl(input).ok).toBe(false),
  );

  test('rejects whitespace and embedded credentials', () => {
    expect(normalizeHttpUrl('https://example.com/a b').ok).toBe(false);
    expect(normalizeHttpUrl('https://user:pass@example.com').ok).toBe(false);
  });

  test('warns for common token and key parameters', () => {
    expect(detectSensitiveUrl('https://example.com/?access_token=abc')).toBe(true);
    expect(detectSensitiveUrl('https://example.com/?api_key=abc')).toBe(true);
    expect(detectSensitiveUrl('https://example.com/docs')).toBe(false);
    expect(detectSensitiveUrl('https://example.com/?token_description=hello')).toBe(false);
  });

  test('canonicalizes host casing and default ports for duplicate comparison', () => {
    expect(getDuplicateUrlKey('https://EXAMPLE.com:443/')).toBe('https://example.com/');
    expect(getDuplicateUrlKey('http://EXAMPLE.com:80/docs')).toBe('http://example.com/docs');
    expect(getDuplicateUrlKey('javascript:alert(1)')).toBeNull();
  });

  test('detects duplicate resources without dropping saved query semantics', () => {
    expect(detectDuplicateUrl('https://example.com/docs#install', ['https://EXAMPLE.com/docs'])).toBe(true);
    expect(detectDuplicateUrl('https://example.com/docs?lang=en', ['https://example.com/docs?lang=fr'])).toBe(false);
    expect(detectDuplicateUrl('https://example.com/docs', ['https://example.com/docs'], 'https://example.com/docs')).toBe(false);
    expect(detectDuplicateUrl('https://new.example/docs', ['https://example.com/docs'])).toBe(false);
  });

  test('validates title and description limits', () => {
    const base = {title: ' TypeScript docs ', url: 'https://www.typescriptlang.org/docs', description: ' official ', category: 'docs' as const};
    const ok = validateLinkDraft(base, undefined, 123);
    expect(ok.ok).toBe(true);
    if (ok.ok) {
      expect(ok.record.title).toBe('TypeScript docs');
      expect(ok.record.description).toBe('official');
      expect(ok.record.createdAt).toBe(123);
    }
    expect(validateLinkDraft({...base, title: 'x'.repeat(121)}).ok).toBe(false);
    expect(validateLinkDraft({...base, description: 'x'.repeat(501)}).ok).toBe(false);
  });
});

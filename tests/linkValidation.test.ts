import {findDuplicateLink, validateLinkDraft} from '../src/domain/linkValidation';

describe('link draft validation', () => {
  const draft = {title: ' TS Docs ', url: 'https://www.typescriptlang.org/docs', description: ' official docs ', category: 'docs' as const};

  it('normalizes fields and creates an id', () => {
    const result = validateLinkDraft(draft, undefined, 123);
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.record.title).toBe('TS Docs');
      expect(result.record.url).toBe('https://www.typescriptlang.org/docs');
      expect(result.record.id).toBe('link-3f');
      expect(result.record.createdAt).toBe(123);
    }
  });

  it('uses the hostname when the title is empty', () => {
    const result = validateLinkDraft({...draft, title: '   '});
    expect(result.ok && result.record.title).toBe('www.typescriptlang.org');
  });

  it('rejects oversized title and description', () => {
    expect(validateLinkDraft({...draft, title: 'x'.repeat(121)}).ok).toBe(false);
    expect(validateLinkDraft({...draft, description: 'x'.repeat(501)}).ok).toBe(false);
  });

  it('preserves id, creation date, and favorite when editing', () => {
    const old = {id: 'stable', title: 'Old', url: draft.url, category: 'docs' as const, favorite: true, createdAt: 10, updatedAt: 11};
    const result = validateLinkDraft(draft, old, 99);
    expect(result.ok && result.record.id).toBe('stable');
    expect(result.ok && result.record.createdAt).toBe(10);
    expect(result.ok && result.record.favorite).toBe(true);
  });

  it('finds duplicates by canonical URL and excludes the record being edited', () => {
    const saved = [{id: 'one', url: 'https://EXAMPLE.com:443/docs#setup'}];
    expect(findDuplicateLink('https://example.com/docs', saved)?.id).toBe('one');
    expect(findDuplicateLink('https://example.com/docs', saved, 'one')).toBeUndefined();
    expect(findDuplicateLink('https://example.com/docs?lang=en', saved)).toBeUndefined();
  });
});

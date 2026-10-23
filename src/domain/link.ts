export type LinkCategory = 'all' | 'docs' | 'tools' | 'projects' | 'references';
export interface LinkRecord { id:string; title:string; url:string; description?:string; category:Exclude<LinkCategory,'all'>; color?:string; favorite:boolean; createdAt:number; updatedAt:number; }

export type LinkSort = 'updated' | 'name' | 'created' | 'favorites';

export function sortLinks<T extends Pick<LinkRecord, 'title' | 'favorite' | 'createdAt' | 'updatedAt'>>(
  links: readonly T[],
  sort: LinkSort = 'updated',
): T[] {
  return [...links].sort((a, b) => {
    if (sort === 'favorites') {
      return Number(b.favorite) - Number(a.favorite) || b.updatedAt - a.updatedAt;
    }
    if (sort === 'name') return a.title.localeCompare(b.title, undefined, {sensitivity: 'base'});
    if (sort === 'created') return b.createdAt - a.createdAt;
    return b.updatedAt - a.updatedAt;
  });
}

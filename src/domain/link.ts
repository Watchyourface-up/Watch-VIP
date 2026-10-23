export type LinkCategory = 'all' | 'docs' | 'tools' | 'projects' | 'references';
export interface LinkRecord { id:string; title:string; url:string; description?:string; category:Exclude<LinkCategory,'all'>; color?:string; favorite:boolean; createdAt:number; updatedAt:number; }

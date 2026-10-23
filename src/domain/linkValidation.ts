import {LinkRecord,LinkCategory} from './link';
import {normalizeHttpUrl} from './urlPolicy';
export type LinkDraft={title:string;url:string;description?:string;category:LinkCategory;color?:string};
export type DraftValidation={ok:true;record:LinkRecord}|{ok:false;field:'title'|'url'|'description'|'category';message:string};
const VALID_CATEGORIES=['docs','tools','projects','references'];
export function validateLinkDraft(draft:LinkDraft,existing?:LinkRecord,now=Date.now()):DraftValidation{
 const title=draft.title.trim(),description=draft.description?.trim()||undefined;
 if(title.length>120)return {ok:false,field:'title',message:'العنوان أطول من 120 حرفًا.'};
 if(description&&description.length>500)return {ok:false,field:'description',message:'الوصف أطول من 500 حرف.'};
 if(!VALID_CATEGORIES.includes(draft.category))return {ok:false,field:'category',message:'التصنيف غير صالح.'};
 const checked=normalizeHttpUrl(draft.url);
 if(!checked.ok)return {ok:false,field:'url',message:checked.message};
 const finalTitle=title||new URL(checked.value).hostname;
 return {ok:true,record:{id:existing?.id??'link-'+now.toString(36),title:finalTitle,url:checked.value,description,category:draft.category,color:draft.color??existing?.color??'#4DE0C1',favorite:existing?.favorite??false,createdAt:existing?.createdAt??now,updatedAt:now}};
}

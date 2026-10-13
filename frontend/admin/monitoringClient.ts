const KEY = "splynt.platform.accessToken";
export const getAccessToken = () => sessionStorage.getItem(KEY);
export const clearAccessToken = () => sessionStorage.removeItem(KEY);
export class ApiRequestError extends Error { readonly status:number; constructor(status:number) {super("Request failed");this.status=status;} }
async function request<T>(path:string, init:RequestInit={}):Promise<T> {
 const token=getAccessToken();
 const response=await fetch(path,{...init,credentials:"omit",headers:{"Content-Type":"application/json",...(token?{Authorization:`Bearer ${token}`} : {}),...init.headers}});
 if(!response.ok) {if(response.status===401){clearAccessToken();window.dispatchEvent(new Event("platform:expired"));}throw new ApiRequestError(response.status);}
 return response.json();
}
export async function login(email:string,password:string) {
 clearAccessToken();
 const result=await request<{accessToken:string}>("/api/auth/login",{method:"POST",body:JSON.stringify({email,password})});
 sessionStorage.setItem(KEY,result.accessToken);
 try {if(!(await platformApi.access()).allowed) throw new ApiRequestError(403);}
 catch(error){clearAccessToken();throw error;}
}
export interface PlatformOverview { organizations:number; activeOrganizations:number; stores:number; activeStores:number; users:number; newOrganizations7Days:number; newUsers7Days:number; storesNeedingAttention:number; checkedAt:string; applicationHealth:string; databaseHealth:string; uptimeSeconds:number; }
export interface PlatformStore { id:number; name:string; organization:string; active:boolean; connectionStatus:string; lastSuccessfulSync:string|null; lastFailedSync:string|null; safeErrorSummary:string|null; lastReceivedProducts:number|null; lastImportedProducts:number|null; activeImportedProducts:number; importIssues:number; attention:string[]; }
export interface PlatformUser { id:number; email:string; name:string; enabled:boolean; registeredAt:string; lastLoginAt:string|null; lastActivityAt:string|null; }
export interface PlatformOrganization { id:number; name:string; active:boolean; registeredAt:string; stores:number; members:number; }
export interface PlatformPage<T> {items:T[];page:number;size:number;total:number;}
export const platformApi = {
    access():Promise<{allowed:boolean}> {return request("/api/platform/access");},
    overview():Promise<PlatformOverview> {return request("/api/platform/overview");},
    stores(page:number,attentionOnly:boolean):Promise<PlatformPage<PlatformStore>> {return request(`/api/platform/stores?page=${page}&attentionOnly=${attentionOnly}`);},
    users(page:number):Promise<PlatformPage<PlatformUser>> {return request(`/api/platform/users?page=${page}`);},
    organizations(page:number):Promise<PlatformPage<PlatformOrganization>> {return request(`/api/platform/organizations?page=${page}`);}
};

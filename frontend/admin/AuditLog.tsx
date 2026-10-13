import {useEffect,useState} from 'react';
import {request,type PlatformPage} from './monitoringClient';
type Entry={id:number;occurredAt:string;actorId:number|null;action:string;status:number};
export function AuditLog(){
 const [page,setPage]=useState(0),[revision,setRevision]=useState(0),[data,setData]=useState<PlatformPage<Entry>|null>(null),[error,setError]=useState(false);
 useEffect(()=>{let active=true;void request<PlatformPage<Entry>>(`/api/platform/audit?page=${page}`).then(r=>{if(active)setData(r);}).catch(()=>{if(active)setError(true);});return()=>{active=false;};},[page,revision]);
 return <section className="content-card"><h2>Security activity</h2><p>Login results and admin requests. No passwords, authenticator codes or tokens are recorded.</p><button className="button secondary" onClick={()=>{setData(null);setError(false);setRevision(r=>r+1);}}>Refresh log</button>{error&&<p role="alert">Audit log unavailable.</p>}{data&&<><div className="table-wrapper"><table><thead><tr><th>Time</th><th>Account ID</th><th>Action</th><th>HTTP status</th></tr></thead><tbody>{data.items.map(r=><tr key={r.id}><td>{new Date(r.occurredAt).toLocaleString()}</td><td>{r.actorId??'Not authenticated'}</td><td>{r.action.replaceAll('_',' ')}</td><td>{r.status}</td></tr>)}</tbody></table></div><button disabled={page===0} onClick={()=>{setData(null);setError(false);setPage(p=>p-1);}}>Previous</button><span> Page {page+1} </span><button disabled={(page+1)*data.size>=data.total} onClick={()=>{setData(null);setError(false);setPage(p=>p+1);}}>Next</button></>}</section>;
}

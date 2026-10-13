import { useEffect, useState } from "react";
import { Brand } from "../src/components/Brand";
import { platformApi, clearAccessToken, ApiRequestError, type PlatformOverview, type PlatformPage, type PlatformStore, type PlatformUser, type PlatformOrganization } from "./monitoringClient";
const when = (value:string|null) => value ? new Date(value).toLocaleString() : "Not recorded";

export function PlatformAdmin({onLogout}:{onLogout:()=>void}) {
    const [tab,setTab]=useState<"stores"|"users"|"organizations">("stores");
    const [page,setPage]=useState(0);
    const [attentionOnly,setAttentionOnly]=useState(false);
    const [refresh,setRefresh]=useState(0);
    const [overview,setOverview]=useState<PlatformOverview|null>(null);
    const [stores,setStores]=useState<PlatformPage<PlatformStore>|null>(null);
    const [users,setUsers]=useState<PlatformPage<PlatformUser>|null>(null);
    const [organizations,setOrganizations]=useState<PlatformPage<PlatformOrganization>|null>(null);
    const [busy,setBusy]=useState(true);
    const [error,setError]=useState("");
    useEffect(() => {
        let cancelled=false;
        async function load() {
            setBusy(true);setError("");setOverview(null);setStores(null);setUsers(null);setOrganizations(null);
            try {
                const [summary,result] = await Promise.all([platformApi.overview(),tab==="stores" ? platformApi.stores(page,attentionOnly) : tab==="users" ? platformApi.users(page) : platformApi.organizations(page)]);
                if(cancelled) return;
                setOverview(summary);
                if(tab==="stores") setStores(result as PlatformPage<PlatformStore>);
                else if(tab==="users") setUsers(result as PlatformPage<PlatformUser>);
                else setOrganizations(result as PlatformPage<PlatformOrganization>);
            } catch(failure) {
                if(!cancelled) setError(failure instanceof ApiRequestError && failure.status===403 ? "Platform admin access is required. Store-owner accounts do not have this permission. If your admin account was already provisioned, the service may be unavailable." : "Monitoring is unavailable. Application/database health could not be verified. Try refreshing.");
            } finally {if(!cancelled) setBusy(false);}
        }
        void load();return () => {cancelled=true;};
    },[tab,page,attentionOnly,refresh]);
    const current=stores ?? users ?? organizations;
    return <main className="platform-admin">
        <header className="platform-header"><Brand /><div><strong>Platform Admin</strong><small>Private service monitoring</small></div><button className="button secondary compact" onClick={() => {clearAccessToken();onLogout();}}>Sign out</button></header>
        <div className="section-heading"><div><span className="section-kicker">Across Splynt</span><h1>Service overview</h1><p>Read-only monitoring. No access to passwords, POS tokens or merchant payment data.</p></div><button className="button primary" disabled={busy} onClick={() => setRefresh(v=>v+1)}>{busy?"Checking…":"Refresh monitoring"}</button></div>
        {error && <p role="alert" className="message error-message">{error}</p>}
        {overview && <><section className="statistics-grid" aria-label="Platform totals">{[["Registered organizations",overview.organizations],["Stores",overview.stores],["Registered users",overview.users],["Stores needing attention",overview.storesNeedingAttention]].map(([label,value])=><article className="statistic-card" key={label}><span>{label}</span><strong>{value}</strong></article>)}</section>
            <section className="platform-health"><p><strong>Application: {overview.applicationHealth}</strong> · Database: {overview.databaseHealth}</p><p>Checked {when(overview.checkedAt)} · Process uptime {Math.floor(overview.uptimeSeconds/60)} minutes</p><p>{overview.activeOrganizations} active organizations · {overview.activeStores} active stores</p><p>Last 7 days: {overview.newOrganizations7Days} new organizations · {overview.newUsers7Days} new users</p><small>Health describes this responding application process and its database reads at the time shown. It is not continuous uptime monitoring.</small></section></>}
        <section className="content-card"><nav className="platform-tabs" aria-label="Monitoring views">{(["stores","organizations","users"] as const).map(value=><button className={`button ${tab===value?"primary":"secondary"}`} key={value} aria-pressed={tab===value} onClick={()=>{setTab(value);setPage(0);}}>{value[0].toUpperCase()+value.slice(1)}</button>)}</nav>
            {tab==="stores" && <><label className="platform-filter"><input type="checkbox" checked={attentionOnly} onChange={e=>{setAttentionOnly(e.target.checked);setPage(0);}} /> Only stores needing attention</label><p className="detail-info">Attention: no connection, no successful sync, an import warning/failure, or inventory older than 24 hours. Connection status is based on saved authorization and sync results, not a live provider check. Inactive stores are excluded from attention alerts.</p></>}
            {tab==="users" && <p className="detail-info">Newest registrations first. Activity means a successful authenticated API request, including background page refreshes; recorded at most once every five minutes. Older activity is not backfilled.</p>}
            {busy && <p role="status">Loading monitoring data…</p>}
            {stores && <div className="table-wrapper"><table><thead><tr><th>Store / organization</th><th>POS status</th><th>Successful sync</th><th>Failed sync</th><th>Products</th><th>Needs attention</th></tr></thead><tbody>{stores.items.map(s=><tr key={s.id}><td><strong>{s.name}</strong><small>{s.organization} · #{s.id}{!s.active && " · Inactive"}</small></td><td>{s.connectionStatus.replaceAll("_"," ")}</td><td>{when(s.lastSuccessfulSync)}</td><td>{when(s.lastFailedSync)}{s.safeErrorSummary && <small>{s.safeErrorSummary}</small>}</td><td><strong>{s.lastImportedProducts ?? "Not recorded"}</strong><small>processed in last successful sync</small><small>{s.lastReceivedProducts ?? "Unknown"} received · {s.activeImportedProducts} active imported products · {s.importIssues} review items</small></td><td>{s.attention.length ? s.attention.join("; ") : s.active ? "None flagged" : "Inactive"}</td></tr>)}</tbody></table></div>}
            {users && <div className="table-wrapper"><table><thead><tr><th>User</th><th>Registered</th><th>Last login</th><th>Last activity</th><th>Status</th></tr></thead><tbody>{users.items.map(u=><tr key={u.id}><td><strong>{u.name}</strong><small>{u.email} · #{u.id}</small></td><td>{when(u.registeredAt)}</td><td>{when(u.lastLoginAt)}</td><td>{when(u.lastActivityAt)}</td><td>{u.enabled?"Enabled":"Disabled"}</td></tr>)}</tbody></table></div>}
            {organizations && <div className="table-wrapper"><table><thead><tr><th>Organization</th><th>Registered</th><th>Stores</th><th>Active memberships</th><th>Status</th></tr></thead><tbody>{organizations.items.map(o=><tr key={o.id}><td>{o.name} · #{o.id}</td><td>{when(o.registeredAt)}</td><td>{o.stores}</td><td>{o.members}</td><td>{o.active?"Active":"Inactive"}</td></tr>)}</tbody></table></div>}
            {current && <div className="section-actions"><p>{current.total} records · Page {page+1} of {Math.max(1,Math.ceil(current.total/current.size))}</p><button className="button secondary compact" disabled={busy||page===0} onClick={()=>setPage(p=>p-1)}>Previous</button><button className="button secondary compact" disabled={busy||(page+1)*current.size>=current.total} onClick={()=>setPage(p=>p+1)}>Next</button></div>}
        </section>
    </main>;
}

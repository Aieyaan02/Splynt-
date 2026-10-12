import { useEffect, useState, type FormEvent } from "react";
import { ApiRequestError, reorderApi, type ReorderDraft, type ReorderItem } from "../lib/api";
import type { Product } from "../types";

export function ReorderDrafts({storeId, products, canManage}: {storeId: number; products: Product[]; canManage: boolean}) {
    const [drafts, setDrafts] = useState<ReorderDraft[]>([]);
    const [editing, setEditing] = useState<ReorderDraft | null>(null);
    const [isNew, setIsNew] = useState(false);
    const [busy, setBusy] = useState(false);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [message, setMessage] = useState("");
    const [confirmOrder, setConfirmOrder] = useState<string | null>(null);
    useEffect(() => {
        let cancelled = false;
        reorderApi.list(storeId).then(data => {if (!cancelled) setDrafts(data);}).catch(failure => {if (!cancelled) setError(failure instanceof Error ? failure.message : "Unable to load drafts.");}).finally(() => {if (!cancelled) setLoading(false);});
        return () => {cancelled = true;};
    }, [storeId]);
    function start() {
        const items: ReorderItem[] = products.filter(p => p.storeId === storeId && p.active && p.stockKnown && p.lowStock && p.suggestedReorderQuantity > 0).slice(0,200).map(p => ({productId:p.id, name:p.name, barcode:p.barcode, unit:p.cloverDetails?.unitName ?? "units", packs:1, unitsPerPack:p.suggestedReorderQuantity, quantity:p.suggestedReorderQuantity}));
        setEditing({id:crypto.randomUUID(),version:0,supplier:"",supplierContact:"",notes:"",status:"DRAFT",createdAt:new Date().toISOString(),updatedAt:new Date().toISOString(),orderedAt:null,items});
        setIsNew(true); setError(""); setMessage(""); setConfirmOrder(null);
    }
    function updateLine(index: number, field: "packs" | "unitsPerPack", value: number) {
        setEditing(d => d && ({...d,items:d.items.map((line,i) => i === index ? {...line,[field]:value,quantity:field === "packs" ? value*line.unitsPerPack : value*line.packs} : line)}));
    }
    function remember(saved: ReorderDraft) {setDrafts(current => [saved,...current.filter(d => d.id !== saved.id)].sort((a,b) => b.createdAt.localeCompare(a.createdAt)).slice(0,20));}
    async function save(event: FormEvent) {
        event.preventDefault(); if (!editing || busy) return;
        setBusy(true);setError("");setMessage("");
        try {
            const saved = await reorderApi.save(storeId,{...editing,requestId:editing.id,items:editing.items.map(({productId,packs,unitsPerPack}) => ({productId,packs,unitsPerPack}))},isNew ? undefined : editing.id);
            remember(saved);setEditing(null);setMessage("Draft saved. No order has been sent.");
        } catch (failure) {setError(failure instanceof ApiRequestError && failure.status === 409 ? "This draft changed or was marked ordered. Discard these edits, reload drafts, and review the latest version." : failure instanceof Error ? failure.message : "Unable to save. Your edits are still here.");}
        finally {setBusy(false);}
    }
    async function ordered(draft: ReorderDraft) {
        if (busy) return;setBusy(true);setError("");setMessage("");
        try {remember(await reorderApi.ordered(storeId,draft));setConfirmOrder(null);setMessage("Marked ordered for your records. POS stock is unchanged.");}
        catch(failure) {setError(failure instanceof Error ? failure.message : "Unable to update draft.");}
        finally {setBusy(false);}
    }
    async function reload() {
        if (busy || editing) return;setBusy(true);setError("");
        try {setDrafts(await reorderApi.list(storeId));} catch(failure) {setError(failure instanceof Error ? failure.message : "Unable to reload.");} finally {setBusy(false);}
    }
    return <section className="content-card" aria-labelledby="reorder-title">
        <div className="section-heading"><div><span className="section-kicker">Your next restock</span><h2 id="reorder-title">Reorder drafts</h2><p>Review quantities, then order with your supplier outside Splynt. Stock never changes here.</p></div><div className="section-actions"><button className="button secondary compact" disabled={busy || !!editing} onClick={() => void reload()}>Reload drafts</button>{canManage && <button className="button primary compact" disabled={busy || !!editing || !products.some(p => p.storeId === storeId && p.active && p.stockKnown && p.lowStock && p.suggestedReorderQuantity > 0)} onClick={start}>Create from low stock</button>}</div></div>
        {error && <p className="message error-message" role="alert">{error}</p>}{message && <p className="detail-info" role="status">{message}</p>}
        {editing && <form onSubmit={event => void save(event)} className="reorder-editor"><fieldset disabled={busy}><legend>{isNew ? "New draft" : "Edit draft"}</legend>
            <div className="form-grid two-columns"><label>Supplier name<input maxLength={150} value={editing.supplier} onChange={e => setEditing({...editing,supplier:e.target.value})} /></label><label>Supplier contact / reference<input maxLength={255} value={editing.supplierContact} onChange={e => setEditing({...editing,supplierContact:e.target.value})} /></label></div>
            <p className="detail-info">Up to 200 items per draft. Each line starts as one pack containing the suggested quantity. Enter your supplier’s actual pack size and number of packs; totals are in the product’s stock unit.</p>
            <div className="table-wrapper"><table><thead><tr><th>Product</th><th>Packs</th><th>Units per pack</th><th>Total quantity</th><th>Action</th></tr></thead><tbody>{editing.items.map((item,index) => <tr key={item.productId}><td>{item.name}<small className="draft-barcode">{item.barcode}</small></td><td><input aria-label={`Packs for ${item.name}`} type="number" required min={1} max={1000000} step={1} value={item.packs || ""} onChange={e => updateLine(index,"packs",Number(e.target.value))} /></td><td><input aria-label={`Units per pack for ${item.name}`} type="number" required min={0.000001} max={999999} step={0.000001} value={item.unitsPerPack || ""} onChange={e => updateLine(index,"unitsPerPack",Number(e.target.value))} /></td><td>{Number(item.quantity.toFixed(6)).toLocaleString(undefined,{maximumFractionDigits:6})} {item.unit}</td><td><button type="button" className="button secondary compact" onClick={() => setEditing({...editing,items:editing.items.filter((_,i) => i!==index)})}>Remove</button></td></tr>)}</tbody></table></div>
            <label>Notes<textarea maxLength={1000} value={editing.notes} onChange={e => setEditing({...editing,notes:e.target.value})} /></label>
            <div className="section-actions"><button className="button primary" disabled={!editing.items.length} type="submit">{busy ? "Saving…" : "Save draft"}</button><button className="button secondary" type="button" onClick={() => {setEditing(null);setError("");}}>Discard edits</button></div>
        </fieldset></form>}
        {loading ? <p>Loading drafts…</p> : !drafts.length ? <p className="detail-info">No saved drafts yet. Start with your low-stock list.</p> : <><p className="detail-info">Showing the latest {drafts.length} drafts (up to 20). Saved quantities are a snapshot; review current stock before ordering.</p><div className="draft-list">{drafts.map(draft => <article key={draft.id}><div className="section-heading"><div><h3>{draft.supplier || "Supplier not set"}</h3><small>{new Date(draft.createdAt).toLocaleString()} · {draft.items.length} items</small></div><span className="badge">{draft.status === "ORDERED" ? "Ordered" : "Draft"}</span></div>
            <details><summary>View items and supplier details</summary><p>{draft.supplierContact || "No contact details"}</p><ul>{draft.items.map(item => <li key={item.productId}>{item.name}: {item.packs} × {item.unitsPerPack} = {item.quantity} {item.unit}</li>)}</ul><p>{draft.notes}</p>{draft.orderedAt && <p>Recorded as ordered: {new Date(draft.orderedAt).toLocaleString()}</p>}</details>
            {canManage && draft.status === "DRAFT" && <div className="section-actions"><button className="button secondary compact" disabled={busy || !!editing} onClick={() => {setEditing(structuredClone(draft));setIsNew(false);setError("");setMessage("");setConfirmOrder(null);}}>Edit</button><button className="button secondary compact" disabled={busy || !!editing} onClick={() => setConfirmOrder(draft.id)}>Mark ordered</button></div>}
            {confirmOrder === draft.id && <div className="detail-info"><p>Confirm you placed this order outside Splynt. This locks the saved draft; it does not send an order or update stock.</p><button className="button primary compact" disabled={busy} onClick={() => void ordered(draft)}>I placed this order</button> <button className="button secondary compact" disabled={busy} onClick={() => setConfirmOrder(null)}>Cancel</button></div>}
        </article>)}</div></>}
    </section>;
}

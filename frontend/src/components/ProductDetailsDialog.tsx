import { useEffect, useRef, useState, type FormEvent } from "react";
import { inventoryApi, productApi, ApiRequestError } from "../lib/api";
import type { InventoryMovement, Product, ProductSettings } from "../types";

export function ProductDetailsDialog({ product, storeId, canManage, timezone, onClose, onSaved }:
    { product: Product; storeId: number; canManage: boolean; timezone: string; onClose: () => void; onSaved: () => Promise<void> }) {
    const dialog = useRef<HTMLDialogElement>(null);
    const [tab, setTab] = useState<"settings" | "history">(product.active ? "settings" : "history");
    const [settings, setSettings] = useState<ProductSettings>({ version: product.version, name: product.name,
        brand: product.brand, category: product.category, reorderLevel: product.reorderLevel,
        targetStock: product.targetStock, unitCost: product.unitCost });
    const [history, setHistory] = useState<InventoryMovement[]>([]);
    const [historyLoading, setHistoryLoading] = useState(true);
    const [historyError, setHistoryError] = useState("");
    const [error, setError] = useState("");
    const [busy, setBusy] = useState(false);
    const [conflict, setConflict] = useState(false);
    const imported = product.source === "CLOVER";
    useEffect(() => {
        const element = dialog.current;
        element?.showModal();
        return () => element?.close();
    }, []);
    useEffect(() => {
        let cancelled = false;
        inventoryApi.history(storeId, product.id).then(result => { if (!cancelled) setHistory(result); })
            .catch(failure => { if (!cancelled) setHistoryError(failure instanceof Error ? failure.message : "Unable to load history."); })
            .finally(() => { if (!cancelled) setHistoryLoading(false); });
        return () => { cancelled = true; };
    }, [storeId, product.id]);
    async function save(event: FormEvent) {
        event.preventDefault(); setError("");
        if (settings.targetStock < settings.reorderLevel) { setError("Target stock must be at least the reorder level."); return; }
        setBusy(true);
        try {
            await productApi.settings(storeId, product.id, settings);
            await onSaved(); onClose();
        } catch (failure) {
            setConflict(failure instanceof ApiRequestError && failure.status === 409);
            setError(failure instanceof Error ? failure.message : "Unable to save settings.");
        } finally { setBusy(false); }
    }
    return <dialog ref={dialog} className="modal product-details" aria-labelledby="product-detail-title"
        onCancel={event => { event.preventDefault(); if (!busy) onClose(); }}>
        <div className="modal-form"><header className="modal-header"><div><span className="section-kicker">{product.active ? "Product details" : "Archived product"}</span><h2 id="product-detail-title">{product.name}</h2><p>{product.barcode} · {product.quantity ?? "Unknown"} {product.cloverDetails?.unitName ?? "units"} · {imported ? "Clover inventory" : "Manual inventory"}</p></div><button className="modal-close" aria-label="Close product details" disabled={busy} onClick={onClose}>×</button></header>
            {imported && product.cloverDetails && <details className="insight-method"><summary>Clover catalog details</summary><dl className="catalog-details">
                <div><dt>SKU</dt><dd>{product.cloverDetails.sku ?? "Not provided"}</dd></div>
                <div><dt>Alternate name</dt><dd>{product.cloverDetails.alternateName ?? "Not provided"}</dd></div>
                <div><dt>Categories</dt><dd>{product.cloverDetails.categories === null ? "Not provided" : product.cloverDetails.categories.join(", ") || "Uncategorized"}</dd></div>
                <div><dt>Unit</dt><dd>{product.cloverDetails.unitName ?? "Not provided"}</dd></div>
                <div><dt>Pricing</dt><dd>{product.cloverDetails.priceType === "PER_UNIT" ? "Per unit" : product.cloverDetails.priceType === "FIXED" ? "Fixed" : product.cloverDetails.priceType === "VARIABLE" ? "Variable" : "Not provided"}</dd></div>
                <div><dt>Available in Clover</dt><dd>{product.cloverDetails.available === null ? "Not provided" : product.cloverDetails.available ? "Yes" : "No"}</dd></div>
                <div><dt>Hidden in Clover</dt><dd>{product.cloverDetails.hidden === null ? "Not provided" : product.cloverDetails.hidden ? "Yes" : "No"}</dd></div>
            </dl><p>These fields come from Clover. Hidden or unavailable does not mean the product is archived in Splynt.</p></details>}
            <div className="detail-tabs" role="tablist" aria-label="Product information">
                {product.active && <button type="button" role="tab" aria-selected={tab === "settings"} onClick={() => setTab("settings")}>Stock settings</button>}
                <button type="button" role="tab" aria-selected={tab === "history"} onClick={() => setTab("history")}>Inventory history <span>{history.length}</span></button>
            </div>
            {tab === "settings" ? <form className="form-stack" onSubmit={save}>
                {imported && <p className="detail-info">Clover manages this product’s name and stock quantity. Your reorder targets and unit cost are managed here in Splynt.</p>}
                {!canManage && <p className="detail-info">Only your store owner or admin can change these settings.</p>}
                <fieldset disabled={!canManage || busy || conflict} className="detail-fieldset">
                    <label>Product name<input value={settings.name} maxLength={150} required disabled={imported} onChange={event => setSettings({ ...settings, name: event.target.value })} /></label>
                    <div className="form-grid two-columns"><label>Brand<input value={settings.brand ?? ""} maxLength={120} disabled={imported} onChange={event => setSettings({ ...settings, brand: event.target.value || null })} /></label><label>Category<input value={settings.category ?? ""} maxLength={120} disabled={imported} onChange={event => setSettings({ ...settings, category: event.target.value || null })} /></label></div>
                    <div className="form-grid two-columns"><label>Reorder when stock reaches<input type="number" min={0} step="0.000001" value={settings.reorderLevel} required onChange={event => setSettings({ ...settings, reorderLevel: Number(event.target.value) })} /><small>Items at or below this level need attention.</small></label><label>Restock up to<input type="number" min="0.000001" step="0.000001" value={settings.targetStock} required onChange={event => setSettings({ ...settings, targetStock: Number(event.target.value) })} /><small>Your ideal stock level after a delivery.</small></label></div>
                    <label>Unit cost<input type="number" min={0} max={9999999999.99} step="0.01" value={settings.unitCost ?? ""} onChange={event => setSettings({ ...settings, unitCost: event.target.value === "" ? null : Number(event.target.value) })} /><small>Leave blank if unknown. Used to estimate inventory value.</small></label>
                </fieldset>
                <div className="reorder-preview"><span>At today’s stock level</span><strong>{product.quantity === null ? "Unknown stock — synchronize before reordering" : `${product.quantity <= settings.reorderLevel ? Number(Math.max(0, settings.targetStock - product.quantity).toFixed(6)) : 0} ${product.cloverDetails?.unitName ?? "units"} to reorder`}</strong><small>Calculated from your targets, not a sales forecast.</small></div>
                {error && <p className="message error-message" role="alert">{error}</p>}
                <div className="modal-actions"><button type="button" className="button secondary" disabled={busy} onClick={onClose}>Close</button>{conflict ? <button type="button" className="button primary" onClick={async () => { await onSaved(); onClose(); }}>Refresh and reopen</button> : canManage && <button className="button primary" disabled={busy}>{busy ? "Saving…" : "Save settings"}</button>}</div>
            </form> : <section aria-label="Inventory movement history">
                <p className="detail-info">Times shown in {timezone}. Clover adjustments reconcile stock; they don’t represent individual sales.</p>
                {historyError ? <p role="alert" className="message error-message">{historyError}</p> : historyLoading ? <p>Loading history…</p> : history.length === 0 ? <div className="empty-state"><h3>A fresh start.</h3><p>Sales, restocks, and synced stock changes will appear here.</p></div> : <ol className="movement-timeline">{history.map(movement => <li key={movement.id}><span className={`movement-delta ${movement.quantityChange > 0 ? "positive" : ""}`}>{movement.quantityChange > 0 ? "+" : ""}{movement.quantityChange}</span><div><strong>{movement.movementType === "ADJUSTMENT" ? "Stock adjustment" : movement.movementType === "SALE" ? "Sale" : "Restock"}<span>{movement.source === "CLOVER" ? "Clover" : movement.source === "MANUAL" ? "Manual" : "System"}</span></strong><p>{movement.quantityBefore} → {movement.quantityAfter} units{movement.note && ` · ${movement.note}`}</p><time dateTime={movement.createdAt}>{new Date(movement.createdAt).toLocaleString([], { timeZone: timezone })}</time></div></li>)}</ol>}
            </section>}
        </div>
    </dialog>;
}

import { useEffect, useRef, useState } from "react";
import { cloverApi, type CloverConnection } from "../lib/api";

interface CloverAutoSyncProps {
    storeId: number | null;
    canManage: boolean;
    onSynchronized: () => Promise<void>;
}

export function CloverAutoSync({ storeId, canManage, onSynchronized }: CloverAutoSyncProps) {
    const [connection, setConnection] = useState<CloverConnection | null>(null);
    const [error, setError] = useState("");
    const [busy, setBusy] = useState(false);
    const onRefresh = useRef(onSynchronized);
    useEffect(() => { onRefresh.current = onSynchronized; }, [onSynchronized]);

    useEffect(() => {
        if (storeId === null) return;
        let cancelled = false;
        let lastSeen: string | null = null;
        async function refresh() {
            try {
                const result = await cloverApi.status(storeId!);
                if (cancelled) return;
                setConnection(result);
                setError("");
                if (result.lastSyncedAt && result.lastSyncedAt !== lastSeen) {
                    lastSeen = result.lastSyncedAt;
                    await onRefresh.current();
                }
            } catch (failure) {
                if (!cancelled) setError(failure instanceof Error ? failure.message : "Unable to check Clover connection.");
            }
        }
        void refresh();
        const interval = window.setInterval(() => void refresh(), 10_000);
        return () => { cancelled = true; window.clearInterval(interval); };
    }, [storeId]);

    async function connect() {
        if (storeId === null) return;
        setBusy(true); setError("");
        try {
            const result = await cloverApi.connect(storeId);
            window.location.assign(result.authorizationUrl);
        } catch (failure) {
            setError(failure instanceof Error ? failure.message : "Unable to connect Clover.");
            setBusy(false);
        }
    }

    async function synchronize() {
        if (storeId === null) return;
        setBusy(true); setError("");
        try {
            await cloverApi.sync(storeId);
            setConnection(await cloverApi.status(storeId));
            await onSynchronized();
        } catch (failure) {
            setError(failure instanceof Error ? failure.message : "Unable to sync inventory.");
        } finally { setBusy(false); }
    }

    if (storeId === null) return null;
    return <div className="clover-connection" aria-live="polite">
        <p className="clover-sync-status">
            {connection === null ? "Checking store connection…" : connection.connected
                ? connection.lastSyncedAt
                    ? `Clover connected · Updated ${new Date(connection.lastSyncedAt).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })}`
                    : "Clover connected · Your first inventory import will begin shortly."
                : "Connect Clover to bring your inventory into Splynt."}
        </p>
        {connection?.connected && <small>Inventory refreshes automatically, even when you close Splynt.</small>}
        {canManage && <div className="table-actions">
            <button type="button" className="button secondary compact" disabled={busy || !connection} onClick={() => void connect()}>
                {busy ? "Working…" : connection?.connected ? "Reconnect Clover" : "Connect Clover"}
            </button>
            {connection?.connected && <button type="button" className="button secondary compact" disabled={busy} onClick={() => void synchronize()}>Sync now</button>}
        </div>}
        {!canManage && connection && !connection.connected && <small>Ask your store owner or admin to connect Clover.</small>}
        {connection && connection.issueCount > 0 && <details className="import-review"><summary>Review {connection.issueCount} inventory {connection.issueCount === 1 ? "item" : "items"}</summary>
            <p>From the last completed import{connection.lastSyncedAt ? ` on ${new Date(connection.lastSyncedAt).toLocaleString()}` : ""}. A failed refresh does not clear this list.</p>
            {connection.issueCount > connection.issues.length && <p>Showing the first {connection.issues.length} items. Resolve these and sync again to see remaining issues.</p>}
            <ul>{connection.issues.map((issue, index) => <li key={`${issue.itemId}-${index}`}><strong>{issue.name ?? "Unnamed Clover item"}</strong><span>{issue.reason === "BARCODE_CONFLICT" ? "Barcode conflict" : issue.reason === "MISSING_CATALOG_ITEM" ? "Missing from Clover catalog" : issue.reason === "UNKNOWN_STOCK" ? "Unknown stock" : issue.reason === "INVALID_BARCODE" ? "Unsupported barcode" : "Incomplete item details"}</span><small>{issue.barcode ? `Barcode: ${issue.barcode} · ` : ""}{issue.itemId ? `Clover item: ${issue.itemId}` : "No item identifier supplied"}</small><p>{issue.nextStep}</p></li>)}</ul>
        </details>}
        {(error || connection?.lastSyncError) && <p role="alert" className="message error-message">{error || connection?.lastSyncError}</p>}
    </div>;
}

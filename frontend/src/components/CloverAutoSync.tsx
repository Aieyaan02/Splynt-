import { providerCopy } from "../lib/providerCopy";
import { useEffect, useRef, useState } from "react";
import { syncRefresh } from "../lib/syncRefresh";
import { cloverApi, type CloverConnection } from "../lib/api";

interface CloverAutoSyncProps {
    storeId: number | null;
    canManage: boolean;
    onSynchronized: () => Promise<void>;
}

export function CloverAutoSync({ storeId, canManage, onSynchronized }: CloverAutoSyncProps) {
    const [connection, setConnection] = useState<CloverConnection | null>(null);
    const [error, setError] = useState("");
    const [busy, setBusy] = useState<"connect" | "sync" | null>(null);
    const onRefresh = useRef(onSynchronized);
    useEffect(() => { onRefresh.current = onSynchronized; }, [onSynchronized]);

    useEffect(() => {
        if (storeId === null) return;
        let cancelled = false;
        const snapshot = syncRefresh();
        let pending = false;
        async function refresh() {
            if (pending || cancelled) return;
            pending = true;
            try {
                const result = await cloverApi.status(storeId!);
                if (cancelled) return;
                setConnection(result);
                setError("");
                await snapshot.run(result.lastSyncedAt, () => onRefresh.current());
            } catch (failure) {
                if (!cancelled) setError(failure instanceof Error ? failure.message : "Unable to check POS connection.");
            } finally { pending = false; }
        }
        void refresh();
        const interval = window.setInterval(() => void refresh(), 10_000);
        return () => { cancelled = true; window.clearInterval(interval); };
    }, [storeId]);

    async function connect() {
        if (storeId === null) return;
        setBusy("connect"); setError("");
        try {
            const result = await cloverApi.connect(storeId);
            window.location.assign(result.authorizationUrl);
        } catch (failure) {
            setError(failure instanceof Error ? failure.message : "Unable to connect your POS system.");
            setBusy(null);
        }
    }

    async function synchronize() {
        if (storeId === null) return;
        setBusy("sync"); setError("");
        try {
            await cloverApi.sync(storeId);
            setConnection(await cloverApi.status(storeId));
            await onSynchronized();
        } catch (failure) {
            setError(failure instanceof Error ? failure.message : "Unable to sync inventory.");
        } finally { setBusy(null); }
    }

    if (storeId === null) return null;
    return <div className="clover-connection" aria-live="polite">
        <p className="clover-sync-status">
            {busy === "sync" ? "Syncing…" : connection === null ? "Checking store connection…" : connection.connected
                ? connection.lastSyncedAt
                    ? `POS connected · Updated ${new Date(connection.lastSyncedAt).toLocaleString()}`
                    : "POS connected · Use Sync now to import your inventory."
                : "Connect your POS system to bring your inventory into Splynt."}
        </p>
        {connection?.connected && <small>Automatic refresh requires the server to be running. Use Sync now to request fresh inventory.</small>}
        {canManage && <div className="table-actions">
            <button type="button" className="button secondary compact" disabled={busy !== null || !connection} onClick={() => void connect()}>
                {busy === "connect" ? "Connecting…" : connection?.connected ? "Reconnect your POS system" : "Connect your POS system"}
            </button>
            {connection?.connected && <button type="button" className="button secondary compact" disabled={busy !== null} onClick={() => void synchronize()}>{busy === "sync" ? "Syncing…" : "Sync now"}</button>}
        </div>}
        {!canManage && connection && !connection.connected && <small>Ask your store owner or admin to connect your POS system.</small>}
        {connection && connection.issueCount > 0 && <details className="import-review"><summary>Review {connection.issueCount} inventory {connection.issueCount === 1 ? "item" : "items"}</summary>
            <p>From the last completed import{connection.lastSyncedAt ? ` on ${new Date(connection.lastSyncedAt).toLocaleString()}` : ""}. A failed refresh does not clear this list.</p>
            {connection.issueCount > connection.issues.length && <p>Showing the first {connection.issues.length} items. Resolve these and sync again to see remaining issues.</p>}
            <ul>{connection.issues.map((issue, index) => <li key={`${issue.itemId}-${index}`}><strong>{issue.name ?? "Unnamed POS item"}</strong><span>{issue.reason === "BARCODE_CONFLICT" ? "Barcode conflict" : issue.reason === "MISSING_CATALOG_ITEM" ? "Missing from your POS system catalog" : issue.reason === "UNKNOWN_STOCK" ? "Unknown stock" : issue.reason === "INVALID_BARCODE" ? "Unsupported barcode" : "Incomplete item details"}</span><small>{issue.barcode ? `Barcode: ${issue.barcode} · ` : ""}{issue.itemId ? `POS item: ${issue.itemId}` : "No item identifier supplied"}</small><p>{providerCopy(issue.nextStep)}</p></li>)}</ul>
        </details>}
        {(error || connection?.lastSyncError) && <p role="alert" className="message error-message">{providerCopy(error || connection?.lastSyncError || "")}</p>}
    </div>;
}

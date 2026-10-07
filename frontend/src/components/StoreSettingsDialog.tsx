import { useEffect, useRef, useState, type FormEvent } from "react";
import { storeApi, type StoreSettings } from "../lib/api";
import type { OrganizationAccess } from "../types";

export function StoreSettingsDialog({ storeId, organizations, onClose, onSaved }: {
    storeId: number | null; organizations: OrganizationAccess[]; onClose: () => void;
    onSaved: (id: number) => Promise<void>;
}) {
    const dialog = useRef<HTMLDialogElement>(null);
    const eligible = organizations.filter(org => org.role === "OWNER" || org.role === "ADMIN");
    const [organizationId, setOrganizationId] = useState(eligible[0]?.id ?? 0);
    const [settings, setSettings] = useState<StoreSettings>({ name: "", city: "", state: "", countryCode: "US",
        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone, currencyCode: "USD" });
    const [loading, setLoading] = useState(storeId !== null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const [savedId, setSavedId] = useState<number | null>(null);
    useEffect(() => { const element = dialog.current; element?.showModal(); return () => element?.close(); }, []);
    useEffect(() => {
        if (storeId === null) return;
        let cancelled = false;
        storeApi.read(storeId).then(data => { if (!cancelled) { setSettings(data); setLoading(false); } })
            .catch(failure => { if (!cancelled) setError(failure instanceof Error ? failure.message : "Unable to load settings."); });
        return () => { cancelled = true; };
    }, [storeId]);
    async function save(event: FormEvent) {
        event.preventDefault(); setBusy(true); setError("");
        try {
            // If refreshing the workspace fails after saving, retry only the refresh.
            const id = savedId ?? (storeId === null ? await storeApi.create(organizationId, settings) : await storeApi.update(storeId, settings)).id;
            if (id === undefined) throw new Error("The store response was incomplete. Reload your workspace.");
            setSavedId(id); await onSaved(id); onClose();
        } catch (failure) { setError(failure instanceof Error ? failure.message : "Unable to save store."); }
        finally { setBusy(false); }
    }
    const field = (key: keyof StoreSettings, value: string) => setSettings(current => ({ ...current, [key]: value }));
    return <dialog ref={dialog} className="modal" aria-labelledby="store-settings-title" onCancel={event => { event.preventDefault(); if (!busy) onClose(); }}>
        <form className="modal-form form-stack" onSubmit={save}>
            <header className="modal-header"><div><span className="section-kicker">Your workspace</span><h2 id="store-settings-title">{storeId === null ? "Add a store" : "Store settings"}</h2></div><button type="button" className="modal-close" aria-label="Close store settings" disabled={busy} onClick={onClose}>×</button></header>
            {loading && <p>Loading store settings…</p>}
            <fieldset className="detail-fieldset form-stack" disabled={loading || busy || savedId !== null}>
                {storeId === null && <label>Organization<select value={organizationId} onChange={event => setOrganizationId(Number(event.target.value))}>{eligible.map(org => <option key={org.id} value={org.id}>{org.name}</option>)}</select></label>}
                <label>Store name<input required maxLength={150} value={settings.name} onChange={event => field("name", event.target.value)} /></label>
                <div className="form-grid two-columns"><label>City<input maxLength={100} value={settings.city ?? ""} onChange={event => field("city", event.target.value)} /></label><label>State / region<input maxLength={100} value={settings.state ?? ""} onChange={event => field("state", event.target.value)} /></label></div>
                <div className="form-grid two-columns"><label>Country code<input required maxLength={2} pattern="[A-Z]{2}" placeholder="US" value={settings.countryCode} onChange={event => field("countryCode", event.target.value.toUpperCase())} /><small>Two-letter code, for example US, CA, or GB.</small></label><label>Currency code<input required maxLength={3} pattern="[A-Z]{3}" placeholder="USD" value={settings.currencyCode} onChange={event => field("currencyCode", event.target.value.toUpperCase())} /><small>Set before adding products or connecting Clover. Existing prices are not converted.</small></label></div>
                <label>Store timezone<input required maxLength={64} list="store-timezones" value={settings.timezone} onChange={event => field("timezone", event.target.value)} /><datalist id="store-timezones">{["America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles", "America/Toronto", "Europe/London", "Europe/Paris", "Asia/Dhaka", "Asia/Kolkata", "Australia/Sydney", "UTC"].map(zone => <option key={zone} value={zone} />)}</datalist><small>Use an IANA timezone such as America/Chicago. Sales charts group orders by this local time.</small></label>
            </fieldset>
            {error && <p className="message error-message" role="alert">{error}</p>}
            <footer className="modal-actions"><button type="button" className="button secondary" disabled={busy} onClick={onClose}>Cancel</button><button className="button primary" disabled={loading || busy || (storeId === null && !organizationId)}>{busy ? "Saving…" : savedId !== null ? "Refresh workspace" : storeId === null ? "Create store" : "Save settings"}</button></footer>
        </form>
    </dialog>;
}

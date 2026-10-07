import { useEffect, useRef, useState, type FormEvent } from "react";
import { accountApi, clearAccessToken } from "../lib/api";
import { passwordEncodingError } from "../lib/passwordValidation";

export function PasswordDialog({ onClose, onChanged }: { onClose: () => void; onChanged: () => void }) {
    const dialog = useRef<HTMLDialogElement>(null);
    const [currentPassword, setCurrentPassword] = useState("");
    const [newPassword, setNewPassword] = useState("");
    const [confirmation, setConfirmation] = useState("");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    useEffect(() => { dialog.current?.showModal(); }, []);
    async function submit(event: FormEvent) {
        event.preventDefault();
        const problem = passwordEncodingError(newPassword) ?? passwordEncodingError(currentPassword);
        if (problem) { setError(problem); return; }
        if (newPassword !== confirmation) { setError("The new passwords do not match."); return; }
        setBusy(true); setError("");
        try {
            await accountApi.changePassword(currentPassword, newPassword);
            clearAccessToken();
            onChanged();
        } catch (failure) {
            setError(failure instanceof Error ? failure.message : "Unable to change your password.");
        } finally { setBusy(false); }
    }
    return <dialog ref={dialog} className="modal compact-modal" aria-labelledby="password-title"
        onCancel={event => { event.preventDefault(); if (!busy) onClose(); }}>
        <form className="modal-form" onSubmit={event => void submit(event)}>
            <header className="modal-header"><h2 id="password-title">Change your password</h2></header>
            <p>After saving, sign in with your new password. Other signed-in sessions will also need to sign in again.</p>
            {error && <p role="alert" className="message error-message">{error}</p>}
            <label>Current password<input type="password" autoComplete="current-password" required maxLength={72}
                disabled={busy} value={currentPassword} onChange={event => setCurrentPassword(event.target.value)} /></label>
            <label>New password<input type="password" autoComplete="new-password" required minLength={10} maxLength={72}
                disabled={busy} value={newPassword} onChange={event => setNewPassword(event.target.value)} /></label>
            <label>Confirm new password<input type="password" autoComplete="new-password" required minLength={10} maxLength={72}
                disabled={busy} value={confirmation} onChange={event => setConfirmation(event.target.value)} /></label>
            <p>Use at least 10 characters. Emoji and some letters count toward the limit faster.</p>
            <footer className="modal-actions">
                <button type="button" className="button secondary" disabled={busy} onClick={onClose}>Cancel</button>
                <button type="submit" className="button primary" disabled={busy}>{busy ? "Saving…" : "Save and sign out"}</button>
            </footer>
        </form>
    </dialog>;
}

import { useEffect, useState, type FormEvent } from "react";
import { Brand } from "./Brand";
import { recoveryApi } from "../lib/api";
import { passwordEncodingError } from "../lib/passwordValidation";
import type { RecoveryPath } from "../lib/recoveryRoute";

export function RecoveryPage({ path, token, initialEmail, onPasswordReset, onVerified }: {
    path: RecoveryPath; token: string | null; initialEmail: string;
    onPasswordReset: () => void; onVerified: () => void;
}) {
    const [email, setEmail] = useState(initialEmail);
    const [password, setPassword] = useState("");
    const [confirmation, setConfirmation] = useState("");
    const [available, setAvailable] = useState<boolean | null>(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const [notice, setNotice] = useState("");
    const [done, setDone] = useState(false);
    const [requestNew, setRequestNew] = useState(false);
    const [statusAttempt, setStatusAttempt] = useState(0);
    const verify = path === "/verify-email";
    const redeem = path !== "/forgot-password" && token !== null && !requestNew;

    useEffect(() => {
        // Keep the token only in component/App memory, not the visible URL or history entry.
        if (window.location.hash.slice(1).split("?")[0] === path && window.location.hash.includes("?")) window.history.replaceState(null, "", window.location.pathname + window.location.search + "#" + path);
    }, [path]);
    useEffect(() => {
        if (redeem) return;
        let cancelled = false;
        recoveryApi.status().then(result => { if (!cancelled) setAvailable(result.available); })
            .catch(() => { if (!cancelled) setError("Unable to check email availability. Please try again."); });
        return () => { cancelled = true; };
    }, [redeem, statusAttempt]);

    async function submit(event: FormEvent) {
        event.preventDefault();
        if (busy || done) return;
        if (redeem && !verify) {
            const problem = passwordEncodingError(password);
            if (problem) { setError(problem); return; }
            if (password !== confirmation) { setError("The passwords do not match."); return; }
        }
        setBusy(true); setError(""); setNotice("");
        try {
            if (redeem && token) {
                if (verify) { await recoveryApi.verify(token); onVerified(); }
                else { await recoveryApi.reset(token, password); onPasswordReset(); }
                setPassword(""); setConfirmation(""); setDone(true);
            } else {
                const result = await recoveryApi.requestEmail(email, verify ? "verify" : "reset");
                setNotice(result.message); setDone(true);
            }
        } catch (failure) { setError(failure instanceof Error ? failure.message : "Unable to complete this request. Try again."); }
        finally { setBusy(false); }
    }
    const title = done && redeem ? (verify ? "Email verified" : "Password updated")
        : verify ? "Verify your email" : redeem ? "Choose a new password" : "Reset your password";
    return <main className="recovery-page">
        <section className="auth-card recovery-card" aria-labelledby="recovery-title">
            <Brand />
            <h1 id="recovery-title">{title}</h1>
            {done && redeem ? <>
                <p role="status">{verify ? "Your email ownership is confirmed. You can return to your workspace." : "Your password has been changed. Sign in with your new password to continue."}</p>
                <a className="button primary" href={verify ? "#/app" : "#/login"}>{verify ? "Continue to Splynt" : "Sign in"}</a>
            </> : <>
                <p>{redeem ? (verify ? "Confirm that you own this email address. This link can be used once." : "Use at least 10 characters. Other signed-in sessions will need to sign in again.")
                    : "Enter your account email and we’ll send instructions if the address is eligible."}</p>
                {path === "/reset-password" && !token && <p className="message" role="status">Open the complete link from your latest email, or request a new one below.</p>}
                {error && <p role="alert" className="message error-message">{error}</p>}
                {notice && <p role="status" className="message success-message">{notice}</p>}
                {!redeem && available === false && <p role="status" className="message">Email delivery is not available yet. Please try again later or <a href="#/contact">contact Splynt</a>.</p>}
                {!redeem && available === null && !error && <p role="status">Checking email availability…</p>}
                {!redeem && available === null && error && <button className="button secondary" onClick={() => { setError(""); setStatusAttempt(value => value + 1); }}>Check again</button>}
                {!done && <form className="form-stack" onSubmit={event => void submit(event)}>
                    {!redeem && <label>Email address<input type="email" autoComplete="email" required maxLength={255} disabled={busy} value={email} onChange={event => setEmail(event.target.value)} /></label>}
                    {redeem && !verify && <>
                        <label>New password<input type="password" autoComplete="new-password" required minLength={10} maxLength={72} disabled={busy} value={password} onChange={event => setPassword(event.target.value)} /></label>
                        <label>Confirm new password<input type="password" autoComplete="new-password" required minLength={10} maxLength={72} disabled={busy} value={confirmation} onChange={event => setConfirmation(event.target.value)} /></label>
                    </>}
                    <button className="button primary wide" disabled={busy || (!redeem && available !== true)}>{busy ? "Working…" : redeem ? (verify ? "Verify email" : "Save new password") : "Send email"}</button>
                </form>}
                {redeem && error && <button type="button" className="button secondary" disabled={busy} onClick={() => { setRequestNew(true); setError(""); }}>Request a new link</button>}
            </>}
            <a className="auth-back" href="#/login">Back to sign in</a>
        </section>
    </main>;
}

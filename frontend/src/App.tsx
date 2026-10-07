import { RecoveryPage } from "./components/RecoveryPage";
import { recoveryRoute } from "./lib/recoveryRoute";
import { useEffect, useState } from "react";

import { ContactInbox } from "./components/ContactInbox";
import { LandingPage } from "./components/LandingPage";
import { AuthPage } from "./components/AuthPage";
import { Dashboard } from "./components/Dashboard";

import {
    accountApi,
    ApiRequestError,
    SESSION_EXPIRED_EVENT,
    clearAccessToken,
    getAccessToken
} from "./lib/api";

import type { Account } from "./types";

export default function App() {
    const [route, setRoute] = useState(() => window.location.hash.slice(1) || "/");
    useEffect(() => {
        const changed = () => { setRoute(window.location.hash.slice(1) || "/"); };
        window.addEventListener("hashchange", changed);
        return () => window.removeEventListener("hashchange", changed);
    }, []);
    const [account, setAccount] =
        useState<Account | null>(null);

    const [checkingSession, setCheckingSession] =
        useState(true);

    const [sessionError, setSessionError] = useState(false);
    const [sessionExpired, setSessionExpired] = useState(false);
    const [restoreAttempt, setRestoreAttempt] = useState(0);

    useEffect(() => {
        const expired = () => {
            setAccount(null);
            setSessionError(false);
            setCheckingSession(false);
            setSessionExpired(true);
            if (!recoveryRoute(window.location.hash.slice(1))) window.location.hash = "/login";
        };
        window.addEventListener(SESSION_EXPIRED_EVENT, expired);
        return () => window.removeEventListener(SESSION_EXPIRED_EVENT, expired);
    }, []);

    useEffect(() => {
        let cancelled = false;

        async function restoreSession() {
            if (!getAccessToken()) {
                setCheckingSession(false);
                return;
            }

            const token = getAccessToken();
            try {
                const currentAccount =
                    await accountApi.getCurrent();

                if (!cancelled && getAccessToken() === token) {
                    setAccount(currentAccount);
                }
            } catch (error) {
                if (!cancelled && getAccessToken() === token
                    && !(error instanceof ApiRequestError && error.status === 401)) {
                    setSessionError(true);
                }
            } finally {
                if (!cancelled) {
                    setCheckingSession(false);
                }
            }
        }

        void restoreSession();

        return () => {
            cancelled = true;
        };
    }, [restoreAttempt]);

    const recovery = recoveryRoute(route);
    if (recovery) return <RecoveryPage key={route} path={recovery.path} token={recovery.token}
        initialEmail={account?.email ?? ""}
        onPasswordReset={() => { clearAccessToken(); setAccount(null); setSessionError(false); setSessionExpired(false); }}
        onVerified={() => setRestoreAttempt(value => value + 1)} />;

    if (route === "/" || route === "/product" || route === "/contact") {
        return <LandingPage signedIn={!!account} route={route} />;
    }

    if (checkingSession) {
        return (
            <main className="loading-screen">
                <div className="brand">
                    <span className="brand-mark">S</span>
                    <span>Splynt</span>
                </div>
                <p>Restoring your workspace…</p>
            </main>
        );
    }

    if (sessionError && !account) {
        return <main className="loading-screen">
            <h1>We couldn’t restore your workspace</h1>
            <p role="alert">Check your connection and try again. Your sign-in has been kept.</p>
            <button className="button primary" onClick={() => {
                setSessionError(false); setCheckingSession(true); setRestoreAttempt(value => value + 1);
            }}>Try again</button>
            <button className="button secondary" onClick={() => {
                clearAccessToken(); setSessionError(false); window.location.hash = "/login";
            }}>Use another account</button>
        </main>;
    }

    if (!account) {
        return (
            <AuthPage
                key={route}
                notice={sessionExpired ? "Your session has expired. Sign in again to continue." : undefined}
                initialMode={route === "/signup" ? "register" : "login"}
                onAuthenticated={account => { setSessionExpired(false); setAccount(account); window.location.hash = "/app"; }}
            />
        );
    }

    if (route === "/inquiries") return <ContactInbox />;

    return (
        <Dashboard
            account={account}
            onAccountChanged={setAccount}
            onLogout={() => { setAccount(null); window.location.hash = "/login"; }}
        />
    );
}
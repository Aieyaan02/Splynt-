import { useEffect, useState } from "react";

import { ContactInbox } from "./components/ContactInbox";
import { LandingPage } from "./components/LandingPage";
import { AuthPage } from "./components/AuthPage";
import { Dashboard } from "./components/Dashboard";

import {
    accountApi,
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

    useEffect(() => {
        let cancelled = false;

        async function restoreSession() {
            if (!getAccessToken()) {
                setCheckingSession(false);
                return;
            }

            try {
                const currentAccount =
                    await accountApi.getCurrent();

                if (!cancelled) {
                    setAccount(currentAccount);
                }
            } catch {
                clearAccessToken();
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
    }, []);

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

    if (!account) {
        return (
            <AuthPage
                key={route}
                initialMode={route === "/signup" ? "register" : "login"}
                onAuthenticated={account => { setAccount(account); window.location.hash = "/app"; }}
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
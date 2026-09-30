import { useEffect, useState } from "react";
import { operationsApi, type ContactInquiry } from "../lib/api";
import { Brand } from "./Brand";
export function ContactInbox() {
    const [page, setPage] = useState(0);
    const [pages, setPages] = useState(0);
    const [inquiries, setInquiries] = useState<ContactInquiry[]>([]);
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(true);
    useEffect(() => {
        let cancelled = false;
        operationsApi.inquiries(page).then(result => {
            if (!cancelled) { setInquiries(result.content); setPages(result.totalPages); setError(""); }
        }).catch(failure => { if (!cancelled) setError(failure instanceof Error ? failure.message : "Unable to open inbox."); })
            .finally(() => { if (!cancelled) setLoading(false); });
        return () => { cancelled = true; };
    }, [page]);
    return <main className="inbox-page"><header><Brand /><a className="text-link" href="#/app">Back to workspace ↗</a></header><span className="overline">SPLYNT OPERATIONS</span><h1>Conversations start here.</h1><p className="inbox-description">Demo requests and questions from the public website. Access is limited to verified platform operators.</p>
        {error ? <div className="message error-message" role="alert">{error}</div> : loading ? <p>Loading inquiries…</p> : inquiries.length === 0 ? <div className="empty-state"><h2>Your inbox is clear.</h2><p>New website inquiries will appear here.</p></div> : inquiries.map(inquiry => <article className="inquiry-card" key={inquiry.id}><div><h2>{inquiry.name}</h2><time dateTime={inquiry.createdAt}>{new Date(inquiry.createdAt).toLocaleString()}</time></div><p className="inquiry-meta">{inquiry.business || "Independent retailer"} · {inquiry.email}</p><p className="inquiry-message">{inquiry.message}</p><a className="button secondary" href={`mailto:${encodeURIComponent(inquiry.email)}?subject=${encodeURIComponent("Your Splynt inquiry")}`}>Reply by email ↗</a></article>)}
        {!error && pages > 1 && <div className="inbox-pagination"><button className="button secondary" disabled={page === 0 || loading} onClick={() => { setLoading(true); setPage(page - 1); }}>Previous</button><span>Page {page + 1} of {pages}</span><button className="button secondary" disabled={page + 1 >= pages || loading} onClick={() => { setLoading(true); setPage(page + 1); }}>Next</button></div>}
    </main>;
}

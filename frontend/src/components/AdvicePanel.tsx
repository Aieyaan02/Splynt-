import { HistoricalSales } from "./HistoricalSales";
import type { SalesInsights } from "../lib/api";
import { useEffect, useRef, useState } from "react";
import { adviceApi, type AdviceView } from "../lib/api";
export function AdvicePanel({ storeId, canManage }: { storeId: number; canManage: boolean }) {
    const generation = useRef(0);
    const generating = useRef(false);
    const [data, setData] = useState<AdviceView | null>(null);
    const [error, setError] = useState("");
    const [busy, setBusy] = useState(false);
    useEffect(() => {
        let cancelled = false;
        const load = async () => {
            if (generating.current) return;
            const current = ++generation.current;
            try { const result = await adviceApi.read(storeId); if (!cancelled && current === generation.current) { setData(result); setError(""); } }
            catch (failure) { if (!cancelled && current === generation.current) setError(failure instanceof Error ? failure.message : "Unable to load recommendations."); }
        };
        void load(); const timer = window.setInterval(() => void load(), 60_000);
        return () => { cancelled = true; window.clearInterval(timer); };
    }, [storeId]);
    async function generate() {
        generating.current = true; generation.current++;
        setBusy(true); setError("");
        try { setData(await adviceApi.generate(storeId)); }
        catch (failure) { setError(failure instanceof Error ? failure.message : "Unable to generate recommendations."); }
        finally { generating.current = false; setBusy(false); }
    }
    function sources(ids: string[]) {
        return <details className="insight-method"><summary>View supporting store data</summary>{ids.map(id => <div key={id}><EvidenceView value={data?.evidence?.[id]} /></div>)}</details>;
    }
    return <section className="content-card" aria-labelledby="advice-title"><div className="section-heading"><div><span className="section-kicker">From patterns to next steps</span><h2 id="advice-title">AI recommendations</h2></div><span className="badge">Owner-reviewed ideas</span></div>
        <p className="detail-info">Generate actions and small product experiments from this store’s sales summary. Product ideas are hypotheses to test, not promises of increased sales.</p>
        {error && <p role="alert" className="message error-message">{error}</p>}
        {!data && !error && <p>Loading recommendations…</p>}
        {data && <>
            {data.message && <p role="status" className="detail-info">{data.message}</p>}
            {data.error && <p role="alert" className="message error-message">{data.error}</p>}
            {canManage && data.configured && <div className="advice-controls"><p>Generating sends this store’s leading product names, aggregate sales, city/region, and timezone to OpenAI. Customer information and Clover credentials are excluded.</p><button className="button primary" disabled={!data.canGenerate || busy} onClick={() => void generate()}>{busy ? "Analyzing your store…" : data.report ? "Generate updated report" : "Generate recommendations"}</button>{data.nextGenerationAt && <small>Next generation available: {new Date(data.nextGenerationAt).toLocaleString()}</small>}</div>}
            {!canManage && !data.report && <p className="detail-info">Your owner or admin can generate a report when enough sales data is available.</p>}
            {data.report && <div className="advice-report"><p className="detail-info">Generated {data.generatedAt ? new Date(data.generatedAt).toLocaleString() : "previously"} · {data.model}. This saved report reflects its attached data snapshot, not live stock.</p><p>{data.report.summary}</p>
                <h3>Actions to consider</h3><div className="advice-grid">{data.report.actions.map((action, index) => <article key={index}><h4>{action.title}</h4><p>{action.rationale}</p><p><strong>Next step:</strong> {action.nextStep}</p>{sources(action.evidenceIds)}</article>)}</div>
                <h3>Product experiments</h3><div className="advice-grid">{data.report.productExperiments.map((idea, index) => <article key={index}><span className="section-kicker">Unproven hypothesis</span><h4>{idea.productIdea}</h4><p>{idea.hypothesis}</p><p><strong>Small test:</strong> {idea.smallTest}</p><p><strong>Measure:</strong> {idea.measure}</p>{sources(idea.evidenceIds)}</article>)}</div>
                <details className="insight-method"><summary>Limits of this report</summary><ul>{data.report.limitations.map((limit, index) => <li key={index}>{limit}</li>)}</ul><p>AI can make mistakes. Check the supporting data before changing orders or merchandising. No inventory changes or purchases happen automatically.</p></details>
            </div>}
        </>}
    </section>;
}

function EvidenceView({ value }: { value: unknown }) {
    if (value === null || value === undefined) return <span>Not available</span>;
    if (typeof value !== "object") return <p>{String(value)}</p>;
    if ("months" in value && "comparisons" in value && "methodology" in value) return <HistoricalSales history={value as SalesInsights["history"]} />;
    const labels: Record<string, string> = { name: "Product", units: "Units in included orders", unitsPerDay: "Average units per day", estimatedDaysRemaining: "Estimated days of stock", currentStock: "Stock at generation", from: "First day", untilExclusive: "End date (exclusive)", completeDays: "Complete days", orders: "Included orders", timezone: "Store timezone", meaning: "Interpretation", location: "Location", methodology: "Method", hourlyUnits: "Units by local hour", weekdayUnitsMondayFirst: "Units by weekday" };
    return <dl className="advice-evidence">{Object.entries(value).filter(([key]) => key !== "productId").map(([key, item]) => <div key={key}><dt>{labels[key] ?? key}</dt><dd>{Array.isArray(item) ? item.map((amount, index) => `${key === "hourlyUnits" ? `${index}:00` : ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"][index] ?? index}: ${amount}`).join(" · ") : item === null ? "Not available" : String(item)}</dd></div>)}</dl>;
}

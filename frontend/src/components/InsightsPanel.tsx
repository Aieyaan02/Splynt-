import { HistoricalSales } from "./HistoricalSales";
import { useEffect, useState } from "react";
import { insightsApi, type SalesInsights } from "../lib/api";
export function InsightsPanel({ storeId }: { storeId: number }) {
    const [data, setData] = useState<SalesInsights | null>(null);
    const [error, setError] = useState("");
    useEffect(() => {
        let cancelled = false;
        async function load() {
            try { const result = await insightsApi.read(storeId); if (!cancelled) { setData(result); setError(""); } }
            catch (failure) { if (!cancelled) setError(failure instanceof Error ? failure.message : "Unable to load insights."); }
        }
        void load(); const timer = window.setInterval(() => void load(), 60_000);
        return () => { cancelled = true; window.clearInterval(timer); };
    }, [storeId]);
    return <section className="content-card" id="insights-section"><div className="section-heading"><div><span className="section-kicker">Your store’s rhythm</span><h2>Sales insights</h2></div><span className="badge">Clover orders</span></div>
        {error && <p role="alert" className="message error-message">{error}</p>}
        {!data && !error ? <p>Loading sales insights…</p> : data && <>
            <p className="detail-info">{data.storeName} · {data.location || "Location not set"} · {data.timezone}</p>
            {data.syncError && <p className="message error-message" role="status">{data.syncError}</p>}
            {!data.lastSyncedAt ? <div className="empty-state"><h3>Let’s learn your store’s rhythm.</h3><p>Connect Clover with order-read permission. After sales history imports, your product and timing insights will appear here.</p></div> : <>
                <p className="detail-info">Last successful sales import: {new Date(data.lastSyncedAt).toLocaleString(undefined, { timeZone: data.timezone })} ({data.timezone}). Analysis ends before {data.untilExclusive}; later days are not counted as zero sales.</p>
                <div className="insight-summary"><div><strong>{data.summary.orders}</strong><span>paid orders</span></div><div><strong>{data.summary.completeDays}</strong><span>complete days analyzed</span></div></div>
                {!data.summary.sufficientForVelocity && <p className="detail-info">Daily velocity estimates need at least 14 complete days and 30 included orders. We’ll show estimates when there’s enough data.</p>}
                {data.summary.topProducts.length > 0 ? <div className="table-wrapper"><table><thead><tr><th>Product</th><th>Orders containing product</th><th>Quantity sold</th><th>Units / day</th><th>Estimated stock cover</th></tr></thead><tbody>{data.summary.topProducts.map(product => <tr key={product.productId}><td><strong>{product.name}</strong></td><td>{product.orders}</td><td>{product.units?.toLocaleString() ?? "—"} {product.unit}</td><td>{product.unitsPerDay ?? "More data needed"}</td><td>{product.estimatedDaysRemaining === null ? "—" : `${product.estimatedDaysRemaining} days`}</td></tr>)}</tbody></table></div> : <p className="detail-info">No qualifying product sales in this window yet.</p>}
                <div className="insight-timing"><div><h3>Time of day</h3><BarChart values={data.summary.hourlyOrders} labels={Array.from({ length: 24 }, (_, hour) => `${hour}:00`)} /></div><div><h3>Day of week</h3><BarChart values={data.summary.weekdayOrders} labels={["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]} /></div></div>
                <HistoricalSales history={data.history} />
                <details className="insight-method"><summary>How to read these insights</summary><p>{data.methodology}</p><p>Window: {data.from} up to {data.untilExclusive} (exclusive). Daily averages include days with no sales. Stock cover assumes that average continues; it is not a guarantee or a demand forecast. Timing bars count each included order once. Products are ranked by the number of orders containing them; product quantities keep their original units. Mixed or unknown units suppress quantity totals and rates; stock cover also requires the current stock unit to match.</p><p>{data.seasonalStatus}</p></details>
            </>}
        </>}
    </section>;
}
function BarChart({ values, labels }: { values: number[]; labels: string[] }) {
    const max = Math.max(1, ...values);
    return <div className="insight-bars" role="list" aria-label="Orders by period">{values.map((value, index) => <div role="listitem" key={labels[index]} aria-label={`${labels[index]}: ${value} orders`} title={`${labels[index]}: ${value} orders`}><div className="bar-track"><span style={{ height: `${value / max * 100}%` }} /></div><small>{values.length > 7 && index % 4 !== 0 ? "" : labels[index]}</small></div>)}</div>;
}

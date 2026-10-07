import { useEffect, useState, type FormEvent } from "react";
import { Brand } from "./Brand";

export function LandingPage({ signedIn, route }: { signedIn: boolean; route: string }) {
    const [busy, setBusy] = useState(false);
    const [sent, setSent] = useState(false);
    const [error, setError] = useState("");
    useEffect(() => {
        if (route === "/contact" || route === "/product") {
            document.getElementById(route.slice(1))?.scrollIntoView();
        } else window.scrollTo(0, 0);
    }, [route]);
    async function contact(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        const form = event.currentTarget;
        const data = new FormData(form);
        setBusy(true); setError("");
        try {
            const response = await fetch("/api/public/inquiries", {
                method: "POST", headers: { "Content-Type": "application/json" },
                body: JSON.stringify(Object.fromEntries(data.entries()))
            });
            if (!response.ok) {
                const body = await response.json().catch(() => null);
                throw new Error(body?.message ?? "We couldn't save your request. Please try again.");
            }
            setSent(true); form.reset();
        } catch (failure) { setError(failure instanceof Error ? failure.message : "Please try again."); }
        finally { setBusy(false); }
    }
    return <div className="marketing">
        <a className="skip-link" href="#main-content" onClick={event => { event.preventDefault(); const main = document.getElementById("main-content"); main?.focus(); main?.scrollIntoView(); }}>Skip to content</a>
        <header className="site-header"><Brand /><nav aria-label="Main navigation">
            <a href="#/product">The product</a><a href="#/contact">Let’s talk</a>
            <a className="nav-signin" href={signedIn ? "#/app" : "#/login"}>{signedIn ? "Your workspace" : "Sign in"}<span aria-hidden="true"> ↗</span></a>
        </nav></header>
        <main id="main-content" tabIndex={-1}>
            <section className="landing-hero">
                <div className="hero-copy"><span className="pill-label"><i /> Built for independent retailers</span>
                    <h1>A little clarity.<br />A better stocked<br /><em>tomorrow.</em></h1>
                    <p>Your shelves have a story. Connect Clover, see what’s running low, and make your next restock a little simpler.</p>
                    <div className="hero-cta"><a className="button primary" href="#/signup">Create your workspace <span aria-hidden="true">↗</span></a><a className="text-link" href="#/product">Take a closer look <span aria-hidden="true">↓</span></a></div>
                    <div className="hero-fineprint">Your store. Your inventory. One clear view.</div>
                </div>
                <div className="hero-art" aria-label="Illustration of a Splynt inventory overview with sample data">
                    <div className="art-orbit orbit-one" /><div className="art-orbit orbit-two" />
                    <div className="inventory-preview">
                        <div className="preview-top"><span className="tiny-mark">s.</span><span>Maple & Main<small>Store overview · Sample data</small></span><span className="preview-dots">•••</span></div>
                        <div className="preview-heading"><span>Good morning, Alex.</span><strong>Let’s keep your shelves happy.</strong></div>
                        <div className="preview-stats"><div><span>Products</span><strong>128</strong></div><div><span>Need a restock</span><strong className="amber-text">3<span className="tiny-arrow"> ↗</span></strong></div></div>
                        <div className="preview-label">A little attention goes a long way <span>↓</span></div>
                        {[['🍵', 'Matcha oat latte', 'Beverages', 4, 20], ['🫒', 'Extra virgin olive oil', 'Pantry', 2, 12], ['🍫', 'Dark chocolate 70%', 'Snacks', 6, 24]].map(([emoji, name, category, stock, target]) => <div className="preview-product" key={name}><span className="product-art">{emoji}</span><div><strong>{name}</strong><small>{category}</small></div><span className="preview-stock">{stock} left<small>Target {target}</small></span></div>)}
                        <div className="preview-bottom"><span className="status-dot" /> Inventory synced with Clover <span>✓</span></div>
                    </div>
                    <div className="floating-note"><span>↗</span><div><strong>Less guesswork.</strong><small>More room to grow.</small></div></div>
                    <span className="art-caption">A calmer way to run your store.</span>
                </div>
            </section>
            <div className="promise-strip"><span>Made for the stores that make a neighborhood.</span><div>Corner shops <i /> Specialty retail <i /> Independent grocers</div></div>
            <section className="product-section" id="product">
                <div className="section-intro"><span className="overline">YOUR NEXT MOVE, MADE CLEAR</span><h2>Know your stock.<br /><em>Find your focus.</em></h2><p>You have enough to keep track of. Splynt brings the important inventory details together, so you can get back to your customers.</p></div>
                <div className="feature-grid">
                    <article className="feature-card feature-green"><span className="feature-number">01 / CONNECT</span><div className="connection-graphic"><span className="connection-logo">s.</span><span className="connection-line">············</span><span className="clover-word">clover</span></div><h3>Your inventory, brought together.</h3><p>Authorize your Clover store and bring its tracked inventory into your own workspace. Background sync keeps it refreshed.</p></article>
                    <article className="feature-card feature-cream"><span className="feature-number">02 / PRIORITIZE</span><div className="stock-graphic"><span>Oat milk <b>4 left</b></span><div><i /></div><small>Below your reorder level of 8</small></div><h3>Catch the low-stock moments.</h3><p>See products that need attention, with reorder quantities based on the stock targets you choose.</p></article>
                    <article className="feature-card feature-lilac"><span className="feature-number">03 / UNDERSTAND</span><div className="history-graphic"><span>↗ <b>Restock</b><em>+24</em></span><span>↙ <b>Sale</b><em>−2</em></span><span>⇄ <b>Clover adjustment</b><em>−1</em></span></div><h3>Keep the bigger picture.</h3><p>Maintain a record of inventory changes and keep each store’s products in its own workspace.</p></article>
                </div>
            </section>
            <section className="steps-section"><div><span className="overline">A SIMPLE START</span><h2>From connected<br />to <em>in control.</em></h2><a className="text-link" href="#/signup">Let’s set up your store ↗</a></div><ol>{[['Make yourself at home', 'Create your account and tell us about your business and first store.'], ['Connect your Clover store', 'Securely authorize access with Clover. Your credentials stay on the server.'], ['See what needs attention', 'After your first import, explore your catalog and set the stock levels that work for you.']].map(([title, body], index) => <li key={title}><span>0{index + 1}</span><div><h3>{title}</h3><p>{body}</p></div></li>)}</ol></section>
            <section className="future-note"><span className="pill-label">Sales insights &amp; optional AI</span><h2>See the patterns.<br />Consider your next move.</h2><p>Explore leading products and sales by day and hour. As your history grows, compare months and recurring patterns. When AI is enabled and enough sales data is available, generate restock actions and product experiments grounded in your store’s data.</p><p>Every report includes its supporting snapshot. Location adds context; product ideas are experiments to evaluate, not promises of higher sales.</p></section>
            <section className="product-faq" aria-labelledby="faq-title">
                <span className="overline">BEFORE YOU CONNECT</span><h2 id="faq-title">A few useful answers.</h2>
                <details><summary>What do I need to get started?</summary><p>Create an account, then set your store’s region, timezone and currency. You can add manual products right away. Connecting Clover requires an authorized Clover account and a configured Splynt integration. Contact us if you need help getting connected.</p></details>
                <details><summary>Does Splynt change my Clover inventory?</summary><p>The connection reads your Clover catalog, stock and permitted sales records. Change Clover stock in Clover; Splynt refreshes its view on the next successful sync. Reorder targets in Splynt help you plan purchases, but do not place orders or change Clover balances.</p></details>
                <details><summary>How do I know my stock is up to date?</summary><p>Your workspace shows the last completed import and any sync problems. Missing or unsupported balances appear as unknown instead of zero. You can review affected products and request another sync. Imports run periodically, so changes in Clover may take time to appear.</p></details>
                <details><summary>When will I see sales patterns and AI ideas?</summary><p>Sales insights depend on Clover order access and imported history. A new connection starts with up to 90 days; year-over-year comparisons need longer coverage. AI reports are optional and require configuration plus enough recent sales evidence. If those requirements aren’t met, your workspace explains what’s missing.</p></details>
                <details><summary>What information is used for AI reports?</summary><p>When an owner or admin generates a report, Splynt sends aggregate sales, leading product names, and store location/timezone to OpenAI. Customer details and Clover credentials are excluded. Review the report’s evidence and limitations before changing purchases or merchandising.</p></details>
            </section>
            <section className="contact-section" id="contact"><div><span className="overline">LET’S TALK SHOP</span><h2>Tell us about<br /><em>your store.</em></h2><p>Want a walkthrough, have a question, or see something we could do better? Leave a note for the Splynt team.</p><span className="contact-aside">Built around real stores.<br />Shaped by conversations like yours.</span></div>
                <form className="contact-form form-stack" onSubmit={contact}>
                    {sent ? <div className="contact-success" role="status"><span>✓</span><h3>Your note is with us.</h3><p>Your inquiry has been saved for the Splynt team. We’ll use the email you provided to respond.</p><button className="button secondary" type="button" onClick={() => setSent(false)}>Send another note</button></div> : <>
                    <div className="form-grid two-columns"><label>Your name<input name="name" autoComplete="name" maxLength={120} required placeholder="Alex Taylor" /></label><label>Work email<input name="email" type="email" autoComplete="email" maxLength={255} required placeholder="alex@yourstore.com" /></label></div>
                    <label>Business name <span className="optional">(optional)</span><input name="business" autoComplete="organization" maxLength={150} placeholder="Your shop, your story" /></label>
                    <label>How can we help?<textarea name="message" rows={4} minLength={10} maxLength={3000} required placeholder="I'd love to see how Splynt could help our store…" /></label>
                    <div className="honeypot" aria-hidden="true"><label>Website<input name="website" tabIndex={-1} autoComplete="off" /></label></div>
                    <small>We’ll use these details to respond to your request. Please don’t include passwords or payment details.</small>
                    {error && <p role="alert" className="message error-message">{error}</p>}
                    <button className="button primary" disabled={busy}>{busy ? "Sending your note…" : "Send your note ↗"}</button>
                    </>}
                </form>
            </section>
        </main>
        <footer className="site-footer"><Brand /><p>A little more clarity for independent retail.</p><span>© {new Date().getFullYear()} Splynt</span><a href="#/contact">Get in touch ↗</a></footer>
    </div>;
}

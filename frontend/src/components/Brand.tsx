export function BrandSymbol() {
    return <svg className="brand-symbol" width="34" height="34" viewBox="0 0 34 34" fill="none" aria-hidden="true">
        <rect width="34" height="34" rx="10" fill="#172b27" />
        <path d="M10 11h14l-4 5H10l4-5Zm14 12H10l4-5h10l-4 5Z" fill="#d5ed98" />
    </svg>;
}

export function Brand({ light = false }: { light?: boolean }) {
    return <a className={`brand${light ? " brand-light" : ""}`} href="#/" aria-label="Splynt home">
        <BrandSymbol /><span>splynt<span className="brand-dot">.</span></span>
    </a>;
}

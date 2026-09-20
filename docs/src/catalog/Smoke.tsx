// Temporary — Task 4 replaces this with CatalogViewer. Its only job is to
// prove the React + JSX + Vitest/jsdom toolchain works end to end.
export default function Smoke({ label }: { label: string }) {
    return <p data-testid="smoke">{label}</p>;
}

// The shadcn CLI rewrites the `cn` import of every component it adds to this
// module (components.json `aliases.utils`), so it has to exist even though the
// implementation comes from the `cn` package.
export { cn } from "cn";

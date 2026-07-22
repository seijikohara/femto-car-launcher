// `let` is forbidden alongside `var` (eslint/no-var in .oxlintrc.json):
// every binding is a const, and mutable state lives in fields of a const
// holder object (see the `state` holder in src/main.ts).
//
// Oxlint JS plugin (ESLint-compatible rule shape), wired up via the
// `jsPlugins` key in .oxlintrc.json. Replaces the Biome GritQL predecessor
// (no-let.grit), which matched on source text because Biome's bridge did not
// expose the declaration kind; the ESLint AST does, so this rule matches the
// `kind` field directly.
const noLetPlugin = {
    meta: { name: "femto" },
    rules: {
        "no-let": {
            meta: { type: "problem" },
            create(context) {
                return {
                    VariableDeclaration(node) {
                        if (node.kind === "let") {
                            context.report({
                                node,
                                message:
                                    "let is forbidden: use const, and keep mutable state in fields of a const holder.",
                            });
                        }
                    },
                };
            },
        },
    },
};

export default noLetPlugin;

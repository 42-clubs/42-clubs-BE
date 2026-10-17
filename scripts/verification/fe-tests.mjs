// Run installed FE tests with all generated caches outside the FE checkout.
import assert from "node:assert/strict";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { resolve, join } from "node:path";
import { pathToFileURL } from "node:url";

assert(process.argv[2], "Pass the FE repository path");
const root = resolve(process.argv[2]);
const cacheDir = await mkdtemp(join(tmpdir(), "clubs-fe-tests-"));
const { startVitest } = await import(
    pathToFileURL(join(root, "node_modules/vitest/dist/node.js"))
);
const { default: react } = await import(
    pathToFileURL(join(root, "node_modules/@vitejs/plugin-react/dist/index.js"))
);
let context;
try {
    context = await startVitest(
        "test",
        [],
        {
            root,
            config: false,
            watch: false,
            environment: "jsdom",
            setupFiles: [join(root, "src/test/setup.ts")],
            css: false,
        },
        { cacheDir, plugins: [react()] },
    );
    if (!context) process.exitCode = 1;
} finally {
    await context?.close();
    await rm(cacheDir, { recursive: true, force: true });
}

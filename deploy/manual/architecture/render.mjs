// Run from the repository root. The page serves the checked-in SVG without a Node runtime.
import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync, mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { pathToFileURL } from "node:url";

const work = mkdtempSync(join(tmpdir(), "locker-likec4-"));
try {
  execFileSync(
    "npm",
    [
      "install",
      "--prefix",
      work,
      "--no-audit",
      "--no-fund",
      "likec4@1.61.1",
      "@hpcc-js/wasm-graphviz@1.29.2",
    ],
    { stdio: "inherit" },
  );
  execFileSync(
    join(work, "node_modules/.bin/likec4"),
    ["gen", "dot", "deploy/manual/architecture", "-o", work],
    { stdio: "inherit" },
  );
  const { Graphviz } = await import(
    pathToFileURL(
      join(work, "node_modules/@hpcc-js/wasm-graphviz/dist/index.js"),
    )
  );
  // Compact spacing keeps service names legible in the embedded inspector.
  const dot = readFileSync(join(work, "flow.dot"), "utf8")
    .replace(/height=[\d.]+/g, "height=0.58")
    .replace(/width=4\.445/g, "width=1.55")
    .replace(/POINT-SIZE="20"/g, 'POINT-SIZE="14"')
    .replace(/nodesep=[\d.]+/, "nodesep=0.18")
    .replace(/ranksep=[\d.]+/, "ranksep=0.33");
  // Fold the async leg back under the synchronous calls while preserving arrow direction.
  const folded = dot
    .replace(/(\w+) -> (\w+) \[/g, (_, from, to) => {
      const backwards = [
        "relay",
        "kafka",
        "notifications",
        "receipts",
      ].includes(from);
      const edge = backwards ? to + " -> " + from : from + " -> " + to;
      return (
        edge +
        ' [id="' +
        from +
        "--" +
        to +
        '", ' +
        (backwards ? "dir=back, " : "")
      );
    })
    .replace(/}\s*$/, "{rank=same; postgres; relay} }");
  writeFileSync(
    "deploy/manual/web/diagram.svg",
    (await Graphviz.load()).dot(folded),
  );
} finally {
  rmSync(work, { recursive: true, force: true });
}

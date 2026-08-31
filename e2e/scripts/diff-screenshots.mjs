#!/usr/bin/env node
/**
 * Compares freshly generated screenshots (e2e/screenshots-output/) against the
 * committed README images (images/) pixel by pixel.
 *
 *   node scripts/diff-screenshots.mjs            # report diffs, exit 1 if any
 *   node scripts/diff-screenshots.mjs --update   # adopt changed/new images
 *
 * For every changed image a visual diff (changed pixels in red) is written to
 * e2e/screenshots-output/diff/.
 */
import * as fs from "fs";
import * as path from "path";
import { fileURLToPath } from "url";
import { PNG } from "pngjs";
import pixelmatch from "pixelmatch";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const OUTPUT_DIR = path.resolve(__dirname, "../screenshots-output");
const DIFF_DIR = path.join(OUTPUT_DIR, "diff");
const IMAGES_DIR = path.resolve(__dirname, "../../images");

// Ratio of changed pixels below which two images count as identical. Absorbs
// antialiasing noise between runs without hiding real UI changes.
const MAX_DIFF_RATIO = 0.0005;

const update = process.argv.includes("--update");

if (!fs.existsSync(OUTPUT_DIR)) {
  console.error(
    `No generated screenshots found in ${OUTPUT_DIR}. Run "npm run screenshots" first.`,
  );
  process.exit(2);
}

const generated = fs
  .readdirSync(OUTPUT_DIR)
  .filter((file) => file.endsWith(".png"))
  .sort();

if (generated.length === 0) {
  console.error(
    `No PNG files in ${OUTPUT_DIR}. Run "npm run screenshots" first.`,
  );
  process.exit(2);
}

fs.rmSync(DIFF_DIR, { recursive: true, force: true });
fs.mkdirSync(DIFF_DIR, { recursive: true });

/** Copies an image into a canvas of the given size (top-left aligned). */
function fitTo(png, width, height) {
  if (png.width === width && png.height === height) {
    return png;
  }
  const canvas = new PNG({ width, height });
  PNG.bitblt(png, canvas, 0, 0, png.width, png.height, 0, 0);
  return canvas;
}

const results = [];

for (const file of generated) {
  const generatedPath = path.join(OUTPUT_DIR, file);
  const committedPath = path.join(IMAGES_DIR, file);

  if (!fs.existsSync(committedPath)) {
    results.push({ file, status: "new" });
    continue;
  }

  const a = PNG.sync.read(fs.readFileSync(committedPath));
  const b = PNG.sync.read(fs.readFileSync(generatedPath));

  const width = Math.max(a.width, b.width);
  const height = Math.max(a.height, b.height);
  const sizeChanged = a.width !== b.width || a.height !== b.height;

  const diff = new PNG({ width, height });
  const diffPixels = pixelmatch(
    fitTo(a, width, height).data,
    fitTo(b, width, height).data,
    diff.data,
    width,
    height,
    { threshold: 0.1 },
  );
  const ratio = diffPixels / (width * height);

  if (!sizeChanged && ratio <= MAX_DIFF_RATIO) {
    results.push({ file, status: "unchanged", ratio });
  } else {
    fs.writeFileSync(path.join(DIFF_DIR, file), PNG.sync.write(diff));
    results.push({
      file,
      status: "changed",
      ratio,
      sizeChanged,
      from: `${a.width}x${a.height}`,
      to: `${b.width}x${b.height}`,
    });
  }
}

const changed = results.filter((r) => r.status === "changed");
const added = results.filter((r) => r.status === "new");
const unchanged = results.filter((r) => r.status === "unchanged");

for (const r of results) {
  if (r.status === "unchanged") {
    console.log(`  unchanged  ${r.file}`);
  } else if (r.status === "new") {
    console.log(`  NEW        ${r.file} (not in images/ yet)`);
  } else {
    const size = r.sizeChanged ? `, size ${r.from} -> ${r.to}` : "";
    console.log(
      `  CHANGED    ${r.file} (${(r.ratio * 100).toFixed(2)}% pixels${size})`,
    );
  }
}

console.log(
  `\n${unchanged.length} unchanged, ${changed.length} changed, ${added.length} new`,
);
if (changed.length > 0) {
  console.log(`Visual diffs written to ${path.relative(process.cwd(), DIFF_DIR)}/`);
}

if (update) {
  for (const r of [...changed, ...added]) {
    fs.copyFileSync(
      path.join(OUTPUT_DIR, r.file),
      path.join(IMAGES_DIR, r.file),
    );
    console.log(`  updated images/${r.file}`);
  }
  process.exit(0);
}

process.exit(changed.length > 0 || added.length > 0 ? 1 : 0);

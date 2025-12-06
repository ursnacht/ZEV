import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import postcss from 'postcss';
import postcssImport from 'postcss-import';
import cssnano from 'cssnano';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

async function buildCSS() {
  const inputFile = path.join(__dirname, '../src/index.css');
  const outputFile = path.join(__dirname, '../dist/index.css');

  const css = fs.readFileSync(inputFile, 'utf8');

  const result = await postcss([
    postcssImport(),
    cssnano({
      preset: 'default',
    }),
  ]).process(css, {
    from: inputFile,
    to: outputFile,
  });

  fs.mkdirSync(path.dirname(outputFile), { recursive: true });
  fs.writeFileSync(outputFile, result.css);

  if (result.map) {
    fs.writeFileSync(outputFile + '.map', result.map.toString());
  }

  console.log('✓ CSS built successfully!');
  console.log(`  Output: ${outputFile}`);
  console.log(`  Size: ${(result.css.length / 1024).toFixed(2)} KB`);
}

buildCSS().catch(error => {
  console.error('Error building CSS:', error);
  process.exit(1);
});

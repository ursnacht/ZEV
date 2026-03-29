const licenseChecker = require('license-checker-rseidelsohn');
const fs = require('fs');
const path = require('path');

const packageLockPath = path.join(__dirname, '..', 'package-lock.json');
const outputPath = path.join(__dirname, '..', 'src', 'assets', 'frontend-licenses.json');

const packageLock = JSON.parse(fs.readFileSync(packageLockPath, 'utf8'));

function getIntegrity(name, version) {
  const key = `node_modules/${name}`;
  const pkg = packageLock.packages?.[key];
  if (pkg?.integrity) {
    return pkg.integrity;
  }
  return null;
}

function parseIntegrity(integrity) {
  if (!integrity) return null;
  const dashIdx = integrity.indexOf('-');
  if (dashIdx === -1) return null;
  const alg = integrity.substring(0, dashIdx);
  const b64 = integrity.substring(dashIdx + 1);
  if (!alg || !b64) return null;
  const algorithm = alg.toUpperCase().replace(/^SHA(\d)/, 'SHA-$1');
  const value = Buffer.from(b64, 'base64').toString('hex');
  return { algorithm, value };
}

licenseChecker.init(
  {
    start: path.join(__dirname, '..'),
    production: true,
    json: true,
    excludePrivatePackages: true,
  },
  (err, packages) => {
    if (err) {
      console.error('license-checker Fehler:', err);
      process.exit(1);
    }

    const result = Object.entries(packages).map(([nameVersion, info]) => {
      const atIdx = nameVersion.lastIndexOf('@');
      const name = nameVersion.substring(0, atIdx);
      const version = nameVersion.substring(atIdx + 1);

      const integrity = getIntegrity(name, version);
      const hash = parseIntegrity(integrity);

      return {
        name,
        version,
        license: Array.isArray(info.licenses) ? info.licenses.join(', ') : (info.licenses || 'Unknown'),
        publisher: info.publisher || null,
        url: info.repository || null,
        hashes: hash ? [hash] : [],
      };
    });

    result.sort((a, b) => a.name.localeCompare(b.name));

    fs.mkdirSync(path.dirname(outputPath), { recursive: true });
    fs.writeFileSync(outputPath, JSON.stringify(result, null, 2));
    console.log(`Generated ${result.length} frontend license entries -> ${outputPath}`);
  }
);

import { defineConfig } from 'vite';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

// Build-time details so the page can never drift from the real APK, plus the production Content-Security-Policy.
const SITE_URL = (process.env.SITE_URL || '').replace(/\/$/, '');   // e.g. https://projectorcontrol.example (optional)
const REPO = 'https://github.com/Yuvraj675/projector-control';

// website/release.json is written by the release workflow (.github/workflows/release.yml) every time a release is published, so the
// page always links to the newest APK on GitHub and shows its real version, size and SHA-256.
function apkInfo() {
  try {
    const r = JSON.parse(fs.readFileSync(path.resolve('release.json'), 'utf8'));
    return { url: r.url, version: r.version, sha: r.sha256, mb: (r.bytes / 1024 / 1024).toFixed(1) };
  } catch {
    return { url: `${REPO}/releases/latest`, version: '', sha: 'see the release page', mb: '' };
  }
}

const CSP = [
  "default-src 'self'",
  "script-src 'self'",
  "style-src 'self' 'unsafe-inline'",   // motion/GSAP write inline style attributes
  "img-src 'self' data: blob:",         // data: = the generated refraction map of the nav lens
  "font-src 'self'",
  "connect-src 'self'",
  "media-src 'self'",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'none'",
].join('; ');

export default defineConfig(({ command }) => ({
  plugins: [{
    name: 'projectorcontrol-html',
    transformIndexHtml(html) {
      const a = apkInfo();
      let out = html
        .replaceAll('%SITE_URL%', SITE_URL).replaceAll('%REPO%', REPO)
        .replaceAll('%APK_URL%', a.url).replaceAll('%APK_VERSION%', a.version)
        .replaceAll('%APK_SHA256%', a.sha).replaceAll('%APK_MB%', a.mb);
      out = out.replace('<!--canonical-->', SITE_URL ? `<link rel="canonical" href="${SITE_URL}/">` : '');
      if (command === 'build') out = out.replace('<!--csp-->', `<meta http-equiv="Content-Security-Policy" content="${CSP}">`);
      return out;
    },
    generateBundle() {
      if (!SITE_URL) return;
      this.emitFile({ type: 'asset', fileName: 'sitemap.xml', source: `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"><url><loc>${SITE_URL}/</loc><changefreq>monthly</changefreq></url></urlset>\n` });
      this.emitFile({ type: 'asset', fileName: 'robots.txt', source: `User-agent: *\nAllow: /\nSitemap: ${SITE_URL}/sitemap.xml\n` });
    },
  }],
  build: { chunkSizeWarningLimit: 900, sourcemap: false },
}));

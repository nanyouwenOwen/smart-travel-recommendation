#!/usr/bin/env node
import fs from 'node:fs';
import process from 'node:process';
import YAML from '../frontend/node_modules/yaml/dist/index.js';

const source = fs.readFileSync(new URL('../.github/workflows/ci.yml', import.meta.url), 'utf8');
const workflow = YAML.parse(source);
const jobs = workflow.jobs;
const expectedMainJobs = ['openapi', 'frontend', 'backend', 'e2e', 'container-smoke', 'security', 'release-candidate'];

if ('release-recovery-v0-1-0' in jobs) throw new Error('one-shot recovery job still exists');
for (const name of expectedMainJobs) {
  if (!(name in jobs)) throw new Error(`missing ordinary job: ${name}`);
}
if (!jobs.release) throw new Error('tag-only release job is missing');
if (jobs.release.if !== "github.event_name == 'push' && github.ref == 'refs/tags/v0.1.0'") {
  throw new Error('release job is not restricted to the exact tag push');
}

for (const [name, job] of Object.entries(jobs)) {
  if (name !== 'release' && job.permissions?.contents === 'write') {
    throw new Error(`ordinary job has contents:write: ${name}`);
  }
  const serialized = JSON.stringify(job);
  if (name !== 'release' && /(publish-github-release|gh release|\/releases(?:\/|\b)|RECOVERY_)/.test(serialized)) {
    throw new Error(`ordinary job contains a Release write/recovery path: ${name}`);
  }
}

if (workflow.permissions?.contents !== 'read') throw new Error('workflow top-level contents permission is not read-only');

const requiredNeeds = ['openapi', 'frontend', 'backend', 'e2e', 'container-smoke', 'security'];
if (JSON.stringify([...jobs['release-candidate'].needs].sort()) !== JSON.stringify([...requiredNeeds].sort())) {
  throw new Error('release-candidate no longer requires every quality job');
}

const requireText = (jobName, fragments) => {
  const body = JSON.stringify(jobs[jobName]);
  for (const fragment of fragments) {
    if (!body.includes(fragment)) throw new Error(`${jobName} lost gate: ${fragment}`);
  }
};
requireText('frontend', ['npm audit --audit-level=high', 'npm run test:coverage', 'npm run build']);
requireText('backend', ['mvn --batch-mode verify']);
requireText('e2e', ['mysql:8.4', 'npm run test:e2e']);
requireText('container-smoke', [
  'bash scripts/compose-smoke.sh',
  'performance-summary-${{ github.run_id }}',
  'perf-results/k6-summary.json',
  '"if":"always()"',
]);
requireText('security', [
  'aquasec/trivy:0.58.2',
  '--scanners vuln',
  '--severity HIGH,CRITICAL',
  '--scanners secret',
]);
requireText('release-candidate', [
  'scripts/verify-release-candidate.sh release',
  'smart-travel-assistant-0.1.0-rc',
  '"retention-days":14',
]);
process.stdout.write('release workflow boundary: PASS\n');

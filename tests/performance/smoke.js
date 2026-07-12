import http from 'k6/http';
import { check, fail } from 'k6';

const base = __ENV.BASE_URL;

export const options = {
  scenarios: {
    measurement: {
      executor: 'constant-vus',
      vus: 10,
      duration: '30s',
      exec: 'measurement',
      tags: { phase: 'measurement' },
    },
  },
  thresholds: {
    'http_req_failed{phase:measurement}': ['rate<0.01'],
    'http_req_duration{phase:measurement}': ['p(95)<2000'],
    'http_reqs{phase:measurement}': ['count>0'],
    'iterations{phase:measurement}': ['count>0'],
    'checks{phase:measurement}': ['rate==1'],
    'http_req_failed{phase:measurement,endpoint:backend}': ['rate<0.01'],
    'http_req_failed{phase:measurement,endpoint:frontend}': ['rate<0.01'],
    'http_req_duration{phase:measurement,endpoint:backend}': ['p(95)<2000'],
    'http_req_duration{phase:measurement,endpoint:frontend}': ['p(95)<2000'],
    'checks{phase:measurement,endpoint:backend}': ['rate==1'],
    'checks{phase:measurement,endpoint:frontend}': ['rate==1'],
  },
};

function requestPair(phase) {
  const health = http.get(`${base}/api/v1/health`, { tags: { phase, endpoint: 'backend' } });
  const healthOk = check(health, { 'health 200': (value) => value.status === 200 }, { phase, endpoint: 'backend' });
  const page = http.get(`${base}/`, { tags: { phase, endpoint: 'frontend' } });
  const pageOk = check(
    page,
    { 'frontend 200': (value) => value.status === 200 && value.body.includes('智能旅游助手') },
    { phase, endpoint: 'frontend' },
  );
  return healthOk && pageOk;
}

export function setup() {
  for (let attempt = 1; attempt <= 5; attempt += 1) {
    if (!requestPair('warmup')) fail(`warmup failed at attempt ${attempt}/5`);
  }
}

export function measurement() {
  requestPair('measurement');
}

export function handleSummary(data) {
  const output = __ENV.SUMMARY_PATH || '/results/k6-summary.json';
  return {
    [output]: JSON.stringify({ schemaVersion: 1, k6Version: '0.57.0', metrics: data.metrics }, null, 2),
  };
}

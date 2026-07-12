import http from 'node:http';

const port = Number(process.env.PORT);
const scenario = process.env.SCENARIO || 'success';
const counts = { backend: 0, frontend: 0 };

const server = http.createServer((request, response) => {
  if (request.url === '/ready') {
    response.end('ready');
    return;
  }
  if (request.url === '/counts') {
    response.setHeader('Content-Type', 'application/json');
    response.end(JSON.stringify(counts));
    return;
  }
  const endpoint = request.url === '/api/v1/health' ? 'backend' : request.url === '/' ? 'frontend' : null;
  if (!endpoint) {
    response.writeHead(404).end();
    return;
  }
  counts[endpoint] += 1;
  const measurement = counts[endpoint] > 5;
  if (scenario === `warmup-${endpoint}-status` || (measurement && scenario === 'measurement-http')) {
    response.writeHead(500).end('failed');
    return;
  }
  if (scenario === 'warmup-frontend-content' && endpoint === 'frontend') {
    response.end('wrong page');
    return;
  }
  if (measurement && scenario === 'measurement-content' && endpoint === 'frontend') {
    response.end('wrong page');
    return;
  }
  if (measurement && scenario === 'measurement-transport') {
    request.socket.destroy();
    return;
  }
  const send = () => response.end(endpoint === 'frontend' ? '智能旅游助手' : '{"status":"UP"}');
  if (measurement && scenario === 'measurement-p95') setTimeout(send, 2100);
  else send();
});

server.listen(port, '127.0.0.1');
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => server.close(() => process.exit(0)));

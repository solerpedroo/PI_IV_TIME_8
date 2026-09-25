import { createServer, type Server } from 'node:http';

export function createGateway(javaBaseUrl: string): Server {
  return createServer(async (request, response) => {
    response.setHeader('Content-Type', 'application/json; charset=utf-8');

    if (request.method === 'GET' && request.url === '/health') {
      response.writeHead(200);
      response.end(JSON.stringify({ status: 'UP' }));
      return;
    }

    if (request.method === 'GET' && request.url === '/ready') {
      try {
        const upstream = await fetch(new URL('/actuator/health', javaBaseUrl), {
          signal: AbortSignal.timeout(2000)
        });
        if (upstream.ok) {
          response.writeHead(200);
          response.end(JSON.stringify({ status: 'UP' }));
          return;
        }
      } catch {
        // Serviço Java indisponível ou tempo limite excedido.
      }

      response.writeHead(503);
      response.end(JSON.stringify({ status: 'DOWN' }));
      return;
    }

    response.writeHead(404);
    response.end(JSON.stringify({ error: 'Not found' }));
  });
}

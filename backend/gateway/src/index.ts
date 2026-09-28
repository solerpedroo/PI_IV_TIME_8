import { createGateway } from './server.js';

const host = process.env.GATEWAY_HOST ?? '127.0.0.1';
const port = Number(process.env.GATEWAY_PORT ?? '8080');
const javaBaseUrl = process.env.JAVA_BASE_URL ?? 'http://127.0.0.1:8081';

if (!Number.isInteger(port) || port < 1 || port > 65535) {
  throw new Error('GATEWAY_PORT deve ser uma porta TCP válida');
}

createGateway(javaBaseUrl).listen(port, host, () => {
  console.log(`Gateway pronto em http://${host}:${port}`);
});

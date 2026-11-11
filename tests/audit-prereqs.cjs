/* Pré-checagens compartilhadas pelas auditorias com API Java real. */
const net = require('node:net');

const ALGORITMOS_HOST = process.env.SERVIDOR_ALGORITMOS_HOST || '127.0.0.1';
const ALGORITMOS_PORT = Number(process.env.SERVIDOR_ALGORITMOS_PORT || 3000);

async function assertAlgoritmosDisponivel() {
  await new Promise((resolve, reject) => {
    const socket = net.connect({ host: ALGORITMOS_HOST, port: ALGORITMOS_PORT }, () => {
      socket.end();
      resolve();
    });
    const fail = () => {
      socket.destroy();
      reject(new Error(
        `Servidor de algoritmos indisponível em ${ALGORITMOS_HOST}:${ALGORITMOS_PORT}. ` +
        'Suba o ServerSocket antes da auditoria (register/talhões dependem dele).'
      ));
    };
    socket.setTimeout(2000, fail);
    socket.on('error', fail);
  });
}

module.exports = { assertAlgoritmosDisponivel };

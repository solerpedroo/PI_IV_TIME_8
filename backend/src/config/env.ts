import dotenv from 'dotenv';

dotenv.config();

if (!process.env.JWT_SECRET) {
  console.warn('⚠️  JWT_SECRET não definido. Usando fallback — NÃO use em produção!');
}

export const env = {
  PORT: Number(process.env.PORT) || 3333,
  JWT_SECRET: process.env.JWT_SECRET || 'fallback_secret',
};

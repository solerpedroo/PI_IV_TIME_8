import { PrismaClient } from '@prisma/client';

// Singleton: evita múltiplas conexões durante o desenvolvimento
const prisma = new PrismaClient();

export default prisma;

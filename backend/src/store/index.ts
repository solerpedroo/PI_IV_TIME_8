import { randomUUID } from 'crypto';

// =============================================================================
// TODO: CAMADA DE DADOS TEMPORÁRIA — EM MEMÓRIA
//
// Esta implementação usa arrays em memória como substituto provisório de um
// banco de dados. Todos os dados são PERDIDOS ao reiniciar o servidor.
//
// Antes de ir para produção, esta camada deve ser substituída por um banco
// de dados real. Decisões pendentes:
//
//   1. QUAL BANCO?
//      - PostgreSQL  → robusto, recomendado para produção (ex: Supabase, Neon, Railway)
//      - MySQL       → alternativa popular, boa para hospedagens tradicionais
//      - SQLite      → simples, arquivo local — bom para testes mas não para produção com múltiplos usuários
//
//   2. QUAL ORM / DRIVER?
//      - Prisma      → melhor DX, type-safe, migrations automáticas (era o usado antes)
//      - TypeORM     → mais verboso mas amplamente ensinado em cursos
//      - Drizzle     → mais leve e moderno, próximo de SQL puro
//      - SQL direto  → mais controle, mas mais código manual
//
//   3. O QUE MUDAR NO CÓDIGO?
//      - Substituir este arquivo (src/store/index.ts) por um cliente do banco escolhido
//      - Os controllers NÃO precisam mudar — eles só chamam os repos daqui
//      - Adicionar variável DATABASE_URL no .env
//      - Rodar migrations para criar as tabelas
//
// =============================================================================

// Tipos de dados
export interface User {
  id: string;
  name: string;
  email: string;
  password: string; // hash bcrypt
  createdAt: string;
  updatedAt: string;
}

export interface Propriedade {
  id: string;
  nome: string;
  localizacao?: string;
  area?: number;
  descricao?: string;
  userId: string;
  createdAt: string;
  updatedAt: string;
}

export interface Talhao {
  id: string;
  nome: string;
  area?: number;
  cultura?: string;
  status: string;
  propriedadeId: string;
  createdAt: string;
  updatedAt: string;
}

// TODO: substituir estes arrays por queries ao banco de dados escolhido
const users: User[] = [];
const propriedades: Propriedade[] = [];
const talhoes: Talhao[] = [];

// Helpers de geração de ID e timestamps
export const newId = () => randomUUID();
export const now = () => new Date().toISOString();

// Repositório de Users
export const usersRepo = {
  findByEmail: (email: string) => users.find((u) => u.email === email),
  findById: (id: string) => users.find((u) => u.id === id),
  create: (data: Omit<User, 'id' | 'createdAt' | 'updatedAt'>): User => {
    const user: User = { ...data, id: newId(), createdAt: now(), updatedAt: now() };
    users.push(user);
    return user;
  },
};

// Repositório de Propriedades
export const propriedadesRepo = {
  findAllByUser: (userId: string) =>
    propriedades
      .filter((p) => p.userId === userId)
      .map((p) => ({ ...p, _count: { talhoes: talhoes.filter((t) => t.propriedadeId === p.id).length } }))
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt)),

  findByIdAndUser: (id: string, userId: string) =>
    propriedades.find((p) => p.id === id && p.userId === userId),

  findById: (id: string) => propriedades.find((p) => p.id === id),

  create: (data: Omit<Propriedade, 'id' | 'createdAt' | 'updatedAt'>): Propriedade => {
    const propriedade: Propriedade = { ...data, id: newId(), createdAt: now(), updatedAt: now() };
    propriedades.push(propriedade);
    return propriedade;
  },

  update: (id: string, data: Partial<Omit<Propriedade, 'id' | 'userId' | 'createdAt' | 'updatedAt'>>): Propriedade | null => {
    const index = propriedades.findIndex((p) => p.id === id);
    if (index === -1) return null;
    propriedades[index] = { ...propriedades[index], ...data, updatedAt: now() };
    return propriedades[index];
  },

  delete: (id: string): void => {
    const index = propriedades.findIndex((p) => p.id === id);
    if (index !== -1) propriedades.splice(index, 1);
    // Remove talhões vinculados (cascata)
    const toRemove = talhoes.filter((t) => t.propriedadeId === id).map((t) => t.id);
    toRemove.forEach((tid) => talhoesRepo.delete(tid));
  },
};

// Repositório de Talhões
export const talhoesRepo = {
  findAllByUser: (userId: string, propriedadeId?: string) => {
    const userPropriedadeIds = propriedades.filter((p) => p.userId === userId).map((p) => p.id);
    return talhoes
      .filter((t) => {
        const belongsToUser = userPropriedadeIds.includes(t.propriedadeId);
        const matchesProp = propriedadeId ? t.propriedadeId === propriedadeId : true;
        return belongsToUser && matchesProp;
      })
      .map((t) => {
        const prop = propriedades.find((p) => p.id === t.propriedadeId);
        return { ...t, propriedade: prop ? { nome: prop.nome } : undefined };
      })
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  },

  findByIdAndUser: (id: string, userId: string) => {
    const talhao = talhoes.find((t) => t.id === id);
    if (!talhao) return null;
    const prop = propriedades.find((p) => p.id === talhao.propriedadeId && p.userId === userId);
    if (!prop) return null;
    return { ...talhao, propriedade: { id: prop.id, nome: prop.nome } };
  },

  create: (data: Omit<Talhao, 'id' | 'createdAt' | 'updatedAt'>): Talhao => {
    const talhao: Talhao = { ...data, id: newId(), createdAt: now(), updatedAt: now() };
    talhoes.push(talhao);
    return talhao;
  },

  update: (id: string, data: Partial<Omit<Talhao, 'id' | 'propriedadeId' | 'createdAt' | 'updatedAt'>>): Talhao | null => {
    const index = talhoes.findIndex((t) => t.id === id);
    if (index === -1) return null;
    talhoes[index] = { ...talhoes[index], ...data, updatedAt: now() };
    return talhoes[index];
  },

  delete: (id: string): void => {
    const index = talhoes.findIndex((t) => t.id === id);
    if (index !== -1) talhoes.splice(index, 1);
  },

  countByUser: (userId: string) => {
    const userPropriedadeIds = propriedades.filter((p) => p.userId === userId).map((p) => p.id);
    return talhoes.filter((t) => userPropriedadeIds.includes(t.propriedadeId)).length;
  },

  groupByStatus: (userId: string) => {
    const userPropriedadeIds = propriedades.filter((p) => p.userId === userId).map((p) => p.id);
    const userTalhoes = talhoes.filter((t) => userPropriedadeIds.includes(t.propriedadeId));
    return userTalhoes.reduce<Record<string, number>>((acc, t) => {
      acc[t.status] = (acc[t.status] || 0) + 1;
      return acc;
    }, {});
  },
};

import { z } from 'zod';

export const createPropriedadeSchema = z.object({
  nome: z.string().min(2, 'Nome deve ter ao menos 2 caracteres.'),
  localizacao: z.string().optional(),
  area: z.number().positive('Área deve ser positiva.').optional(),
  descricao: z.string().optional(),
});

export const updatePropriedadeSchema = createPropriedadeSchema.partial();

import { z } from 'zod';

export const createTalhaoSchema = z.object({
  nome: z.string().min(2, 'Nome deve ter ao menos 2 caracteres.'),
  area: z.number().positive('Área deve ser positiva.').optional(),
  cultura: z.string().optional(),
  status: z.enum(['ativo', 'inativo', 'em_preparo']).optional(),
  propriedadeId: z.string().min(1, 'ID da propriedade obrigatório.'),
});

export const updateTalhaoSchema = createTalhaoSchema.partial().omit({ propriedadeId: true });

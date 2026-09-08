import { Request, Response, NextFunction } from 'express';
import { ZodSchema, ZodError } from 'zod';

// Recebe um schema Zod e retorna um middleware que valida o body
export function validate(schema: ZodSchema) {
  return (req: Request, res: Response, next: NextFunction): void => {
    try {
      req.body = schema.parse(req.body);
      next();
    } catch (error) {
      if (error instanceof ZodError) {
        const messages = error.errors.map((e) => ({
          field: e.path.join('.'),
          message: e.message,
        }));
        res.status(400).json({ error: 'Dados inválidos.', details: messages });
        return;
      }
      next(error);
    }
  };
}

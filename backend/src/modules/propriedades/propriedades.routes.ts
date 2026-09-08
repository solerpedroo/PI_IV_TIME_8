import { Router } from 'express';
import { getAll, getById, create, update, remove } from './propriedades.controller';
import { authMiddleware } from '../../middlewares/auth.middleware';
import { validate } from '../../middlewares/validate.middleware';
import { createPropriedadeSchema, updatePropriedadeSchema } from './propriedades.schemas';

const router = Router();

router.use(authMiddleware);

router.get('/', getAll);
router.get('/:id', getById);
router.post('/', validate(createPropriedadeSchema), create);
router.put('/:id', validate(updatePropriedadeSchema), update);
router.delete('/:id', remove);

export default router;

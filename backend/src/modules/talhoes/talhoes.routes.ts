import { Router } from 'express';
import { getAll, getById, create, update, remove } from './talhoes.controller';
import { authMiddleware } from '../../middlewares/auth.middleware';
import { validate } from '../../middlewares/validate.middleware';
import { createTalhaoSchema, updateTalhaoSchema } from './talhoes.schemas';

const router = Router();

router.use(authMiddleware);

router.get('/', getAll);
router.get('/:id', getById);
router.post('/', validate(createTalhaoSchema), create);
router.put('/:id', validate(updateTalhaoSchema), update);
router.delete('/:id', remove);

export default router;

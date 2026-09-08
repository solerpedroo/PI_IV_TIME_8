import { Request, Response } from 'express';
import { talhoesRepo, propriedadesRepo } from '../../store';

export async function getAll(req: Request, res: Response): Promise<void> {
  const { propriedadeId } = req.query;

  if (propriedadeId) {
    const prop = propriedadesRepo.findByIdAndUser(String(propriedadeId), req.userId!);
    if (!prop) {
      res.status(403).json({ error: 'Acesso negado.' });
      return;
    }
  }

  const talhoes = talhoesRepo.findAllByUser(req.userId!, propriedadeId ? String(propriedadeId) : undefined);
  res.json(talhoes);
}

export async function getById(req: Request, res: Response): Promise<void> {
  const id = req.params.id as string;

  const talhao = talhoesRepo.findByIdAndUser(id, req.userId!);
  if (!talhao) {
    res.status(404).json({ error: 'Talhão não encontrado.' });
    return;
  }

  res.json(talhao);
}

export async function create(req: Request, res: Response): Promise<void> {
  const { nome, area, cultura, status, propriedadeId } = req.body;

  const prop = propriedadesRepo.findByIdAndUser(propriedadeId, req.userId!);
  if (!prop) {
    res.status(403).json({ error: 'Propriedade não encontrada ou sem acesso.' });
    return;
  }

  const talhao = talhoesRepo.create({ nome, area, cultura, status: status ?? 'ativo', propriedadeId });
  res.status(201).json(talhao);
}

export async function update(req: Request, res: Response): Promise<void> {
  const id = req.params.id as string;

  const exists = talhoesRepo.findByIdAndUser(id, req.userId!);
  if (!exists) {
    res.status(404).json({ error: 'Talhão não encontrado.' });
    return;
  }

  const { nome, area, cultura, status } = req.body;
  const updated = talhoesRepo.update(id, { nome, area, cultura, status });
  res.json(updated);
}

export async function remove(req: Request, res: Response): Promise<void> {
  const id = req.params.id as string;

  const exists = talhoesRepo.findByIdAndUser(id, req.userId!);
  if (!exists) {
    res.status(404).json({ error: 'Talhão não encontrado.' });
    return;
  }

  talhoesRepo.delete(id);
  res.status(204).send();
}

import { Request, Response } from 'express';
import { propriedadesRepo, talhoesRepo } from '../../store';

export async function getAll(req: Request, res: Response): Promise<void> {
  const propriedades = propriedadesRepo.findAllByUser(req.userId!);
  res.json(propriedades);
}

export async function getById(req: Request, res: Response): Promise<void> {
  const id = req.params.id as string;

  const propriedade = propriedadesRepo.findByIdAndUser(id, req.userId!);
  if (!propriedade) {
    res.status(404).json({ error: 'Propriedade não encontrada.' });
    return;
  }

  const talhoes = talhoesRepo.findAllByUser(req.userId!, id);
  res.json({ ...propriedade, talhoes });
}

export async function create(req: Request, res: Response): Promise<void> {
  const { nome, localizacao, area, descricao } = req.body;
  const propriedade = propriedadesRepo.create({ nome, localizacao, area, descricao, userId: req.userId! });
  res.status(201).json(propriedade);
}

export async function update(req: Request, res: Response): Promise<void> {
  const id = req.params.id as string;

  const exists = propriedadesRepo.findByIdAndUser(id, req.userId!);
  if (!exists) {
    res.status(404).json({ error: 'Propriedade não encontrada.' });
    return;
  }

  const { nome, localizacao, area, descricao } = req.body;
  const updated = propriedadesRepo.update(id, { nome, localizacao, area, descricao });
  res.json(updated);
}

export async function remove(req: Request, res: Response): Promise<void> {
  const id = req.params.id as string;

  const exists = propriedadesRepo.findByIdAndUser(id, req.userId!);
  if (!exists) {
    res.status(404).json({ error: 'Propriedade não encontrada.' });
    return;
  }

  propriedadesRepo.delete(id);
  res.status(204).send();
}

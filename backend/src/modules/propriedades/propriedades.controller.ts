import { Request, Response } from 'express';
import prisma from '../../prisma/client';

export async function getAll(req: Request, res: Response): Promise<void> {
  const propriedades = await prisma.propriedade.findMany({
    where: { userId: req.userId },
    include: { _count: { select: { talhoes: true } } },
    orderBy: { createdAt: 'desc' },
  });

  res.json(propriedades);
}

export async function getById(req: Request, res: Response): Promise<void> {
  const id = req.params.id as string;

  const propriedade = await prisma.propriedade.findFirst({
    where: { id, userId: req.userId },
    include: { talhoes: true },
  });

  if (!propriedade) {
    res.status(404).json({ error: 'Propriedade não encontrada.' });
    return;
  }

  res.json(propriedade);
}

export async function create(req: Request, res: Response): Promise<void> {
  const { nome, localizacao, area, descricao } = req.body;

  const propriedade = await prisma.propriedade.create({
    data: { nome, localizacao, area, descricao, userId: req.userId! },
  });

  res.status(201).json(propriedade);
}

export async function update(req: Request, res: Response): Promise<void> {
  const id = req.params.id as string;

  const propriedade = await prisma.propriedade.findFirst({
    where: { id, userId: req.userId },
  });

  if (!propriedade) {
    res.status(404).json({ error: 'Propriedade não encontrada.' });
    return;
  }

  const { nome, localizacao, area, descricao } = req.body;

  const updated = await prisma.propriedade.update({
    where: { id },
    data: { nome, localizacao, area, descricao },
  });

  res.json(updated);
}

export async function remove(req: Request, res: Response): Promise<void> {
  const id = req.params.id as string;

  const propriedade = await prisma.propriedade.findFirst({
    where: { id, userId: req.userId },
  });

  if (!propriedade) {
    res.status(404).json({ error: 'Propriedade não encontrada.' });
    return;
  }

  await prisma.propriedade.delete({ where: { id } });

  res.status(204).send();
}

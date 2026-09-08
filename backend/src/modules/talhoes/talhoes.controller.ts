import { Request, Response } from 'express';
import prisma from '../../prisma/client';

async function propriedadePertenceAoUsuario(propriedadeId: string, userId: string): Promise<boolean> {
  const propriedade = await prisma.propriedade.findFirst({
    where: { id: propriedadeId, userId },
  });
  return !!propriedade;
}

export async function getAll(req: Request, res: Response): Promise<void> {
  const { propriedadeId } = req.query;

  if (propriedadeId) {
    const pertence = await propriedadePertenceAoUsuario(String(propriedadeId), req.userId!);
    if (!pertence) {
      res.status(403).json({ error: 'Acesso negado.' });
      return;
    }
  }

  const talhoes = await prisma.talhao.findMany({
    where: {
      propriedade: { userId: req.userId },
      ...(propriedadeId ? { propriedadeId: String(propriedadeId) } : {}),
    },
    include: { propriedade: { select: { nome: true } } },
    orderBy: { createdAt: 'desc' },
  });

  res.json(talhoes);
}

export async function getById(req: Request, res: Response): Promise<void> {
  const id = req.params.id as string;

  const talhao = await prisma.talhao.findFirst({
    where: { id, propriedade: { userId: req.userId } },
    include: { propriedade: { select: { id: true, nome: true } } },
  });

  if (!talhao) {
    res.status(404).json({ error: 'Talhão não encontrado.' });
    return;
  }

  res.json(talhao);
}

export async function create(req: Request, res: Response): Promise<void> {
  const { nome, area, cultura, status, propriedadeId } = req.body;

  const pertence = await propriedadePertenceAoUsuario(propriedadeId, req.userId!);
  if (!pertence) {
    res.status(403).json({ error: 'Propriedade não encontrada ou sem acesso.' });
    return;
  }

  const talhao = await prisma.talhao.create({
    data: { nome, area, cultura, status: status ?? 'ativo', propriedadeId },
  });

  res.status(201).json(talhao);
}

export async function update(req: Request, res: Response): Promise<void> {
  const id = req.params.id as string;

  const talhao = await prisma.talhao.findFirst({
    where: { id, propriedade: { userId: req.userId } },
  });

  if (!talhao) {
    res.status(404).json({ error: 'Talhão não encontrado.' });
    return;
  }

  const { nome, area, cultura, status } = req.body;

  const updated = await prisma.talhao.update({
    where: { id },
    data: { nome, area, cultura, status },
  });

  res.json(updated);
}

export async function remove(req: Request, res: Response): Promise<void> {
  const id = req.params.id as string;

  const talhao = await prisma.talhao.findFirst({
    where: { id, propriedade: { userId: req.userId } },
  });

  if (!talhao) {
    res.status(404).json({ error: 'Talhão não encontrado.' });
    return;
  }

  await prisma.talhao.delete({ where: { id } });

  res.status(204).send();
}

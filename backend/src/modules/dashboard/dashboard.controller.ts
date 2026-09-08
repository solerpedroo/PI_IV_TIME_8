import { Request, Response } from 'express';
import prisma from '../../prisma/client';

export async function getDashboard(req: Request, res: Response): Promise<void> {
  const userId = req.userId!;

  const [propriedades, totalTalhoes, talhoesPorStatus] = await Promise.all([
    prisma.propriedade.findMany({
      where: { userId },
      select: { id: true, nome: true, area: true, _count: { select: { talhoes: true } } },
    }),

    prisma.talhao.count({
      where: { propriedade: { userId } },
    }),

    prisma.talhao.groupBy({
      by: ['status'],
      where: { propriedade: { userId } },
      _count: { status: true },
    }),
  ]);

  const areaTotal = propriedades.reduce((acc, p) => acc + (p.area ?? 0), 0);

  const statusTalhoes = talhoesPorStatus.reduce<Record<string, number>>((acc, item) => {
    acc[item.status] = item._count.status;
    return acc;
  }, {});

  res.json({
    resumo: {
      totalPropriedades: propriedades.length,
      totalTalhoes,
      areaTotal,
    },
    statusTalhoes,
    propriedades,
  });
}

import { Request, Response } from 'express';
import { propriedadesRepo, talhoesRepo } from '../../store';

export async function getDashboard(req: Request, res: Response): Promise<void> {
  const userId = req.userId!;

  const propriedades = propriedadesRepo.findAllByUser(userId);
  const totalTalhoes = talhoesRepo.countByUser(userId);
  const statusTalhoes = talhoesRepo.groupByStatus(userId);
  const areaTotal = propriedades.reduce((acc, p) => acc + (p.area ?? 0), 0);

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

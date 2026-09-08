import 'express-async-errors';
import express from 'express';
import cors from 'cors';
import { env } from './config/env';

import authRoutes from './modules/auth/auth.routes';
import propriedadesRoutes from './modules/propriedades/propriedades.routes';
import talhoesRoutes from './modules/talhoes/talhoes.routes';
import dashboardRoutes from './modules/dashboard/dashboard.routes';

const app = express();

// Middlewares globais
app.use(cors());
app.use(express.json());

// Rota de health check
app.get('/health', (_req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

// Rotas dos módulos
app.use('/auth', authRoutes);
app.use('/propriedades', propriedadesRoutes);
app.use('/talhoes', talhoesRoutes);
app.use('/dashboard', dashboardRoutes);

// Handler de erros global (captura erros não tratados)
app.use((err: Error, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
  console.error(err.stack);
  res.status(500).json({ error: 'Erro interno do servidor.' });
});

app.listen(env.PORT, () => {
  console.log(`🌱 AgroGestão Backend rodando em http://localhost:${env.PORT}`);
});

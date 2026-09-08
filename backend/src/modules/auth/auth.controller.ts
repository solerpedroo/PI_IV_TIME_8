import { Request, Response } from 'express';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import { usersRepo } from '../../store';
import { env } from '../../config/env';

export async function register(req: Request, res: Response): Promise<void> {
  const { name, email, password } = req.body;

  if (usersRepo.findByEmail(email)) {
    res.status(409).json({ error: 'E-mail já cadastrado.' });
    return;
  }

  const hashedPassword = await bcrypt.hash(password, 10);
  const user = usersRepo.create({ name, email, password: hashedPassword });
  const token = jwt.sign({ userId: user.id }, env.JWT_SECRET, { expiresIn: '7d' });

  res.status(201).json({
    user: { id: user.id, name: user.name, email: user.email, createdAt: user.createdAt },
    token,
  });
}

export async function login(req: Request, res: Response): Promise<void> {
  const { email, password } = req.body;

  const user = usersRepo.findByEmail(email);
  if (!user) {
    res.status(401).json({ error: 'E-mail ou senha incorretos.' });
    return;
  }

  const passwordMatch = await bcrypt.compare(password, user.password);
  if (!passwordMatch) {
    res.status(401).json({ error: 'E-mail ou senha incorretos.' });
    return;
  }

  const token = jwt.sign({ userId: user.id }, env.JWT_SECRET, { expiresIn: '7d' });

  res.json({
    user: { id: user.id, name: user.name, email: user.email },
    token,
  });
}

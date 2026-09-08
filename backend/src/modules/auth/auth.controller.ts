import { Request, Response } from 'express';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import prisma from '../../prisma/client';
import { env } from '../../config/env';

export async function register(req: Request, res: Response): Promise<void> {
  const { name, email, password } = req.body;

  const userExists = await prisma.user.findUnique({ where: { email } });
  if (userExists) {
    res.status(409).json({ error: 'E-mail já cadastrado.' });
    return;
  }

  const hashedPassword = await bcrypt.hash(password, 10);

  const user = await prisma.user.create({
    data: { name, email, password: hashedPassword },
    select: { id: true, name: true, email: true, createdAt: true },
  });

  const token = jwt.sign({ userId: user.id }, env.JWT_SECRET, { expiresIn: '7d' });

  res.status(201).json({ user, token });
}

export async function login(req: Request, res: Response): Promise<void> {
  const { email, password } = req.body;

  const user = await prisma.user.findUnique({ where: { email } });
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

<!-- Autor Principal: Pedro Henrique Contardi Soler - RA: 25005592 -->
# Fotos de evidência de campo (#29, #175, #176)

## Decisão

- **Opcional** no MVP (complemento aprovado nesta entrega).
- **Armazenamento:** subdocumento `fotoEvidencia` no mesmo BSON da ocorrência ou atividade, campo `dados` como `Binary`.
- **Upload separado** do POST/PUT de cadastro (`POST .../foto`) para que falha no anexo não perca título/descrição (#175).
- **Consulta:** JSON traz `temFoto` e metadados; bytes via `GET .../foto` (#176).

## Rotas (Bearer)

| Recurso | Enviar | Recuperar | Remover |
|---------|--------|-----------|---------|
| Ocorrência | `POST /api/ocorrencias/{id}/foto` + `{ versao, conteudoBase64, tipoMime?, nomeArquivo? }` | `GET /api/ocorrencias/{id}/foto` | `DELETE /api/ocorrencias/{id}/foto` + `{ versao }` |
| Atividade | `POST /api/atividades/{id}/foto` + `{ versao, conteudoBase64, ... }` | `GET /api/atividades/{id}/foto` | `DELETE /api/atividades/{id}/foto` + `{ versao }` |

Limites: JPEG/PNG/WebP/GIF, máx. **5 MiB** decodificados (`util.FotoEvidencia`). O HTTP aceita até **8 MiB** de corpo em rotas que terminam em `/foto` (`ParceiroHttp.MAXIMO_CORPO_FOTO_EVIDENCIA`), para acomodar Base64; demais rotas permanecem em 1 MiB.

## Validação

```bash
sh backend/api/testar.sh
```

Inclui fluxo de upload/recuperação em `TesteOcorrencias`.

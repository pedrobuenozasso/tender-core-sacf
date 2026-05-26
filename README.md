# SACF Tender Core

Projeto base para transformar a logica do SACF Tender em um modulo simples da futura plataforma central da SACF.

Este projeto foi criado para ser **apenas o motor funcional de busca no PNCP**. Ele nao carrega a estrutura SaaS do app atual.

## Objetivo

O objetivo e disponibilizar uma API aberta e simples para buscar oportunidades no PNCP, com uma interface Vue basica para teste e operacao inicial.

Ele foi separado do projeto Next.js original para remover dependencias que pertencem ao produto SaaS atual, como usuario, plano, pagamento, banco, relatorios e captcha.

## O Que Foi Mantido

- Busca de licitacoes abertas no PNCP.
- Busca de contratos/ganhadores no PNCP.
- Filtro por palavras-chave.
- Filtro por palavras proibidas.
- Filtro por UF.
- Filtro por periodo.
- Normalizacao de texto sem acentos.
- Match de palavras com fronteira de palavra, evitando falso positivo dentro de outra palavra.
- Montagem de links para o PNCP.
- Resposta JSON limpa para integrar com outras automacoes.

## O Que Foi Removido

- Usuarios.
- Login.
- NextAuth.
- Turnstile/CAPTCHA.
- Stripe.
- Supabase.
- Banco de dados.
- Cache persistente.
- Relatorios diarios.
- Envio de email.
- Admin.
- Onboarding.
- Controle de plano/trial.

## Estrutura

```text
sacf-tender-core/
  backend/
    Spring Boot API
  docs/
    Referencias de banco e arquitetura
  frontend/
    Vue 3 + Vite UI
```

## Banco De Dados

O projeto atual nao usa banco. Mesmo assim, existe um documento de referencia para quando a plataforma SACF decidir persistir keywords, buscas, resultados e emails:

```text
docs/database-schema.md
```

Esse arquivo descreve um schema sugerido para:

- organizations
- users
- keyword_sets
- keywords
- tender_searches
- tender_results
- contract_leads
- email_notifications
- pncp_cache

Ele e apenas referencia. A implementacao atual segue stateless.

## Backend

Stack:

- Java 21+
- Spring Boot
- Maven

Pasta:

```bash
cd backend
```

Rodar em desenvolvimento:

```bash
mvn spring-boot:run
```

Gerar build:

```bash
mvn -DskipTests package
```

Se quiser manter o cache Maven dentro deste projeto:

```bash
mvn -Dmaven.repo.local=../.m2 -DskipTests package
```

Servidor local:

```text
http://localhost:8080
```

## Frontend

Stack:

- Vue 3
- Vite
- npm

Pasta:

```bash
cd frontend
```

Instalar dependencias:

```bash
npm install
```

Rodar em desenvolvimento:

```bash
npm run dev
```

Gerar build:

```bash
npm run build
```

Interface local:

```text
http://localhost:5173
```

O Vite esta configurado para encaminhar `/api` para:

```text
http://localhost:8080
```

## Endpoints

### Health

```http
GET /api/health
```

Resposta:

```json
{
  "status": "ok",
  "service": "sacf-tender-core",
  "checkedAt": "2026-05-26T12:00:00Z"
}
```

### Busca Principal

```http
POST /api/tenders/search
```

Payload:

```json
{
  "mode": "open",
  "startDate": "2026-05-01",
  "endDate": "2026-05-07",
  "keywords": [
    {
      "text": "software",
      "qualifiers": []
    },
    {
      "text": "tecnologia",
      "qualifiers": ["licenca"]
    }
  ],
  "blockedKeywords": ["show", "festa"],
  "states": ["SP", "RJ"],
  "maxResults": 100
}
```

Modos aceitos:

```text
open      licitacoes abertas
awarded   contratos/ganhadores
```

Datas aceitas:

```text
YYYY-MM-DD
YYYYMMDD
```

Limite atual:

```text
31 dias por busca
```

### Atalho Para Licitacoes Abertas

```http
GET /api/tenders/open?startDate=2026-05-01&endDate=2026-05-07&keywords=software,tecnologia&states=SP,RJ
```

### Atalho Para Contratos/Ganhadores

```http
GET /api/tenders/contracts?startDate=2026-05-01&endDate=2026-05-07&keywords=software,tecnologia&states=SP,RJ
```

## Formato Da Resposta

```json
{
  "results": [
    {
      "id": "123",
      "type": "open",
      "pncpControlNumber": "123",
      "object": "Aquisicao de software",
      "agency": "Prefeitura Municipal",
      "supplier": null,
      "supplierDocument": null,
      "state": "SP",
      "estimatedValue": 150000.0,
      "publicationDate": "2026-05-01T10:00:00",
      "signatureDate": null,
      "closingDate": "2026-05-20T18:00:00",
      "pncpLink": "https://pncp.gov.br/app/editais/...",
      "matchedKeywords": ["software"],
      "modality": "Pregao"
    }
  ],
  "total": 1,
  "mode": "open",
  "startDate": "2026-05-01",
  "endDate": "2026-05-07",
  "keywordHits": {
    "software": 1
  }
}
```

## Regras De Busca

### Keywords

Cada keyword e normalizada:

- converte para minusculo;
- remove acentos;
- compara com fronteira de palavra;
- aceita espacos flexiveis entre termos.

Exemplo:

```text
licenca software
```

Pode bater em:

```text
licenca de software
```

### Qualificadores

Um qualificador limita a keyword principal.

Exemplo:

```json
{
  "text": "software",
  "qualifiers": ["licenca"]
}
```

Isso significa:

```text
Precisa ter "software" e tambem pelo menos um qualificador, como "licenca".
```

### Palavras Proibidas

Se qualquer palavra proibida bater no texto da oportunidade, o item e descartado.

Exemplo:

```json
{
  "blockedKeywords": ["show", "festa"]
}
```

## PNCP

Base usada:

```text
https://pncp.gov.br/api/consulta/v1
```

Licitacoes abertas:

```text
/contratacoes/publicacao
```

Contratos/ganhadores:

```text
/contratos
```

Codigos de modalidade usados para licitacoes abertas:

```text
1, 2, 4, 5, 6, 8, 12
```

Esses codigos foram herdados do projeto SACF Tender atual.

## Estado Atual

Validado localmente:

```bash
cd backend
mvn -Dmaven.repo.local=../.m2 -DskipTests package
```

Resultado:

```text
OK
```

Validado localmente:

```bash
cd frontend
npm install
npm run build
```

Resultado:

```text
OK
```

## Como Testar Manualmente

Terminal 1:

```bash
cd backend
mvn spring-boot:run
```

Terminal 2:

```bash
cd frontend
npm run dev
```

Abrir:

```text
http://localhost:5173
```

Ou testar direto via API:

```bash
curl -X POST http://localhost:8080/api/tenders/search \
  -H 'Content-Type: application/json' \
  -d '{
    "mode": "open",
    "startDate": "2026-05-01",
    "endDate": "2026-05-07",
    "keywords": [
      { "text": "software", "qualifiers": [] }
    ],
    "blockedKeywords": [],
    "states": [],
    "maxResults": 20
  }'
```

## Proximos Passos Recomendados

1. Confirmar com a equipe da plataforma SACF se o backend sera Spring Boot mesmo.
2. Definir se este projeto fica em monorepo ou vira repositorio separado.
3. Decidir se a API continua aberta ou recebe uma chave simples.
4. Adicionar cache curto para respostas PNCP.
5. Adicionar logs estruturados.
6. Adicionar testes unitarios para normalizacao e match de keywords.
7. Adicionar testes de integracao com respostas simuladas do PNCP.
8. Adaptar o contrato da API ao padrao da plataforma SACF.

## Observacao

Este projeto nao substitui ainda o SACF Tender atual em producao. Ele e uma extracao funcional, pensada para integrar o Tender dentro da plataforma SACF maior.

# Handoff: SACF Tender Core

Data: 2026-05-27

## Contexto Geral

Este projeto fica em:

```text
/Users/pedrobueno/Zasso/sacf-tender/sacf-tender-core
```

Ele e um repositorio Git separado dentro da pasta do projeto principal.

Repositorio remoto:

```text
https://github.com/pedrobuenozasso/tender-core-sacf.git
```

Estado atual do Git do core:

```text
main limpo
origin/main sincronizado
ultimo commit: c6640e3 Ignore Vercel project metadata
```

## Objetivo Do Core

Transformar o funcional do Tender em um modulo simples para a futura plataforma central da SACF.

O core deve manter apenas:

```text
Busca de licitacoes abertas no PNCP.
Busca de contratos/ganhadores no PNCP.
Filtro por keywords.
Filtro por qualificadores.
Filtro por keywords bloqueadas.
Filtro por UF.
Filtro por periodo.
Normalizacao e resposta JSON limpa.
```

O core nao deve carregar:

```text
Usuarios.
Login.
NextAuth.
Turnstile.
Stripe.
Supabase.
Banco de dados real.
Relatorios diarios.
Envio de email.
Admin.
Onboarding.
Plano/trial.
```

## Stack Atual

Backend:

```text
Java 21+
Spring Boot
Maven
```

Frontend:

```text
Vue 3
Vite
npm
```

Estrutura:

```text
sacf-tender-core/
  backend/
  docs/
  frontend/
```

## Backend Atual

Endpoints:

```text
GET  /api/health
POST /api/tenders/search
GET  /api/tenders/open
GET  /api/tenders/contracts
```

Busca principal:

```text
POST /api/tenders/search
```

Modos:

```text
open      licitacoes abertas
awarded   contratos/ganhadores
```

Limite atual:

```text
31 dias por busca
maxResults com limite interno
```

O backend chama:

```text
https://pncp.gov.br/api/consulta/v1
```

Arquivos principais:

```text
backend/src/main/java/io/sacf/tender/TenderCoreApplication.java
backend/src/main/java/io/sacf/tender/config/WebConfig.java
backend/src/main/java/io/sacf/tender/controller/TenderController.java
backend/src/main/java/io/sacf/tender/service/PncpClient.java
backend/src/main/java/io/sacf/tender/service/TenderSearchService.java
backend/src/main/java/io/sacf/tender/model/*.java
backend/src/main/resources/application.yml
```

Validacao ja feita:

```bash
cd backend
mvn -Dmaven.repo.local=../.m2 -DskipTests package
```

Tambem foi validado runtime local com:

```bash
mvn -q -Dmaven.repo.local=../.m2 spring-boot:run
curl -s http://localhost:8080/api/health
```

Resposta esperada:

```json
{"status":"ok","service":"sacf-tender-core"}
```

## Frontend Atual

Pasta:

```text
frontend/
```

Comandos:

```bash
npm install
npm run dev
npm run build
```

O Vite encaminha `/api` para:

```text
http://localhost:8080
```

O frontend foi criado como UI funcional para testar a API, mas o usuario achou distante do SACF original.

Pedido pendente:

```text
Aproximar muito mais o visual da versao original do SACF Tender.
Manter a ideia de core sem login/usuarios/Stripe/banco.
Fazer a tela parecer parte da plataforma SACF.
```

Observacao importante:

```text
O deploy Vercel feito anteriormente hospedou apenas o frontend Vue.
Como o backend Java nao esta na Vercel, a busca real nao funciona publicamente nesse deploy sem hospedar o backend ou usar API externa configurada.
```

## Deploy/Teste Frontend Feito Antes

Projeto Vercel usado para teste:

```text
frontend
```

URLs vistas:

```text
https://frontend-ruddy-seven-ad1x9zlll5.vercel.app
https://frontend-seej7g71y-pedro-vini08-7809s-projects.vercel.app
```

Foi desabilitada protecao SSO desse projeto de teste.

Nao confundir com o projeto principal `sacf-tender`.

## Banco

O core atual nao usa banco.

Documento de referencia criado:

```text
docs/database-schema.md
```

Esse arquivo documenta uma proposta futura para:

```text
organizations
users
keyword_sets
keywords
tender_searches
tender_results
contract_leads
email_notifications
pncp_cache
```

Mas a implementacao atual deve continuar stateless ate nova decisao.

## Projeto Principal: Estado Relevante

Nao mexer no projeto principal por enquanto, conforme pedido do usuario.

Resumo do principal:

```text
Login na Hostinger voltou a funcionar.
Erro inicial era env faltando de Supabase no container.
Stripe voltou a funcionar apos troca da STRIPE_SECRET_KEY expirada.
Cupom foi habilitado com STRIPE_ALLOW_PROMOTION_CODES=true.
HTTPS/cadeado em sacf.io esta OK.
Vercel foi separado/desativado para emails e aliases sacf.io/www.sacf.io foram removidos da Vercel.
```

PRs recentes do principal:

```text
PR #6/#7: removeu marcador visual Hostinger v2 e ajustes relacionados.
PR #8: ajuste de app icon para remover borda branca/cache.
```

Alerta de seguranca no principal:

```text
.env.local apareceu versionado na main do principal.
O proximo trabalho de seguranca deve remover .env.local do Git e rotacionar chaves sensiveis.
Mas o usuario pediu para nao mexer no principal agora.
```

Arquivos locais nao versionados no principal que devem ser ignorados por enquanto:

```text
docs/hostinger-turnstile-handoff.md
logo-perfil-sacf.png
sacf-tender-core/
```

## Proximo Trabalho No Core

Retomar a formatacao/visual do frontend do core para aproximar do SACF original, sem reintroduzir funcionalidades SaaS.

Direcao recomendada:

```text
1. Ler frontend/src/App.vue e frontend/src/style.css.
2. Comparar com o visual do projeto principal:
   - src/app/(dashboard)/buscar/page.tsx
   - src/app/globals.css
   - src/components/Sidebar.tsx
   - src/components/Topbar.tsx
   - public/brand/*
3. Copiar/adaptar brand assets para frontend/public/brand.
4. Recriar a UI Vue como dashboard operacional:
   - topbar escura SACF
   - sidebar/nav visual
   - formulario de busca
   - filtros por modo, datas, UFs, keywords, bloqueios
   - cards de metricas
   - tabela/lista de resultados
   - estado demo quando backend nao estiver disponivel
5. Rodar npm run build no frontend.
6. Commitar no repo do core e push para origin/main ou branch, conforme pedido.
```

Importante:

```text
Nao adicionar usuarios, login, Stripe, Supabase, banco, cron ou email no core.
Nao mexer no projeto principal ate o usuario pedir.
```

# Database Schema Reference

This file documents a possible database model for integrating Tender Core into the broader SACF platform.

The current `sacf-tender-core` project **does not use a database**. It is intentionally stateless:

- the API receives keywords in the request;
- calls PNCP directly;
- returns JSON results;
- does not store users, searches, emails or contracts.

Use this document as a reference when the central SACF platform decides to persist Tender data.

## Design Goal

Keep the Tender Core independent from product billing and user management.

Recommended split:

```text
SACF Platform
  owns users, organizations, permissions, plans, billing, email delivery

Tender Core
  owns PNCP search logic, result normalization, matching rules
```

If a database is added later, it should probably live in the platform layer or in a dedicated Tender schema.

## Main Entities

Suggested tables:

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

If the central platform already has `organizations` and `users`, do not duplicate them. Reference their IDs instead.

## organizations

Represents a SACF client/company.

```sql
create table organizations (
  id uuid primary key,
  name text not null,
  document text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
```

Notes:

- This may already exist in the central SACF platform.
- Tender should only reference `organization_id`.

## users

Represents platform users.

```sql
create table users (
  id uuid primary key,
  organization_id uuid references organizations(id),
  name text,
  email text not null unique,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
```

Notes:

- This is a platform-level table, not a Tender Core responsibility.
- Auth should be handled by the central platform.

## keyword_sets

Groups keywords by company, automation or search profile.

```sql
create table keyword_sets (
  id uuid primary key,
  organization_id uuid not null references organizations(id),
  name text not null,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
```

Examples:

```text
TI e software
Materiais esportivos
Obras e engenharia
Produtos hospitalares
```

## keywords

Stores monitored and blocked keywords.

```sql
create table keywords (
  id uuid primary key,
  keyword_set_id uuid not null references keyword_sets(id) on delete cascade,
  text text not null,
  type text not null default 'monitored',
  qualifiers jsonb not null default '[]'::jsonb,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint keywords_type_check check (type in ('monitored', 'blocked'))
);
```

Meaning:

```text
monitored  keyword that should match opportunities
blocked    keyword that excludes opportunities
```

Qualifiers:

```json
{
  "text": "software",
  "qualifiers": ["licenca", "assinatura"]
}
```

Rule:

```text
Match "software" only if the same text also contains at least one qualifier.
```

Recommended indexes:

```sql
create index keywords_keyword_set_id_idx on keywords(keyword_set_id);
create index keywords_active_type_idx on keywords(active, type);
```

## tender_searches

Stores each search execution.

```sql
create table tender_searches (
  id uuid primary key,
  organization_id uuid references organizations(id),
  user_id uuid references users(id),
  keyword_set_id uuid references keyword_sets(id),
  mode text not null,
  start_date date not null,
  end_date date not null,
  states text[] not null default '{}',
  total_results integer not null default 0,
  keyword_hits jsonb not null default '{}'::jsonb,
  status text not null default 'completed',
  error_message text,
  created_at timestamptz not null default now(),
  constraint tender_searches_mode_check check (mode in ('open', 'awarded')),
  constraint tender_searches_status_check check (status in ('completed', 'failed'))
);
```

Modes:

```text
open     licitacoes abertas
awarded  contratos/ganhadores
```

Recommended indexes:

```sql
create index tender_searches_org_created_idx on tender_searches(organization_id, created_at desc);
create index tender_searches_user_created_idx on tender_searches(user_id, created_at desc);
```

## tender_results

Stores normalized PNCP results from a search.

```sql
create table tender_results (
  id uuid primary key,
  search_id uuid not null references tender_searches(id) on delete cascade,
  pncp_control_number text,
  result_type text not null,
  object text not null,
  agency text,
  supplier text,
  supplier_document text,
  state text,
  estimated_value numeric(18, 2),
  publication_date timestamptz,
  signature_date timestamptz,
  closing_date timestamptz,
  pncp_link text,
  matched_keywords text[] not null default '{}',
  modality text,
  raw_payload jsonb,
  created_at timestamptz not null default now(),
  constraint tender_results_type_check check (result_type in ('open', 'awarded'))
);
```

Recommended indexes:

```sql
create index tender_results_search_id_idx on tender_results(search_id);
create index tender_results_pncp_control_number_idx on tender_results(pncp_control_number);
create index tender_results_state_idx on tender_results(state);
create index tender_results_publication_date_idx on tender_results(publication_date desc);
```

Deduplication option:

```sql
create unique index tender_results_search_pncp_unique
  on tender_results(search_id, pncp_control_number)
  where pncp_control_number is not null;
```

## contract_leads

Optional table for commercial/outreach workflows based on awarded contracts.

```sql
create table contract_leads (
  id uuid primary key,
  organization_id uuid references organizations(id),
  tender_result_id uuid references tender_results(id),
  supplier text,
  supplier_document text,
  object text,
  agency text,
  state text,
  estimated_value numeric(18, 2),
  pncp_link text,
  status text not null default 'new',
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint contract_leads_status_check check (status in ('new', 'contacted', 'qualified', 'discarded'))
);
```

Recommended indexes:

```sql
create index contract_leads_org_status_idx on contract_leads(organization_id, status);
create index contract_leads_supplier_document_idx on contract_leads(supplier_document);
```

## email_notifications

Optional table if the platform sends daily reports or alerts.

```sql
create table email_notifications (
  id uuid primary key,
  organization_id uuid references organizations(id),
  user_id uuid references users(id),
  email text not null,
  notification_type text not null,
  reference_date date,
  status text not null default 'pending',
  provider_id text,
  total_results integer not null default 0,
  error_message text,
  sent_at timestamptz,
  created_at timestamptz not null default now(),
  constraint email_notifications_type_check check (notification_type in ('open', 'awarded', 'both')),
  constraint email_notifications_status_check check (status in ('pending', 'sent', 'failed', 'skipped'))
);
```

Recommended indexes:

```sql
create index email_notifications_user_created_idx on email_notifications(user_id, created_at desc);
create index email_notifications_status_idx on email_notifications(status);
```

## pncp_cache

Optional short-lived cache for raw PNCP responses.

```sql
create table pncp_cache (
  id uuid primary key,
  cache_key text not null unique,
  source text not null,
  reference_start_date date not null,
  reference_end_date date not null,
  payload jsonb not null,
  total integer not null default 0,
  expires_at timestamptz not null,
  created_at timestamptz not null default now()
);
```

Examples of `cache_key`:

```text
open:2026-05-01:modalidade:1
contracts:2026-05-01:2026-05-07:page:1
```

Recommended indexes:

```sql
create index pncp_cache_expires_at_idx on pncp_cache(expires_at);
create index pncp_cache_source_dates_idx on pncp_cache(source, reference_start_date, reference_end_date);
```

## Minimal MVP Schema

If the platform wants the smallest useful database, start with only:

```text
keyword_sets
keywords
tender_searches
tender_results
```

Everything else can come later.

## What Not To Put In Tender Core

Avoid putting these responsibilities inside Tender Core:

```text
passwords
login sessions
billing plans
Stripe customer IDs
admin roles
email provider secrets
Turnstile secrets
```

Those belong to the central SACF platform.

## Integration Pattern

Recommended production flow:

```text
SACF Platform authenticates user
SACF Platform loads organization keyword set
SACF Platform calls Tender Core /api/tenders/search
Tender Core returns normalized PNCP results
SACF Platform stores/searches/sends emails if needed
```

This keeps Tender Core reusable by other SACF automations.

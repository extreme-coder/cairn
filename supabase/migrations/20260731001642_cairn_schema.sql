create schema if not exists private;
revoke all on schema private from anon, authenticated;

create type public.study_role as enum ('pi', 'coordinator', 'collector', 'viewer');

create table public.studies (
  id         uuid primary key default gen_random_uuid(),
  name       text not null check (length(trim(name)) > 0),
  created_by uuid not null default auth.uid() references auth.users (id),
  created_at timestamptz not null default now()
);

create table public.study_members (
  study_id uuid not null references public.studies (id) on delete cascade,
  user_id  uuid not null references auth.users (id) on delete cascade,
  role     public.study_role not null,
  added_at timestamptz not null default now(),
  primary key (study_id, user_id)
);

create index study_members_user_id_idx on public.study_members (user_id);

create table public.forms (
  id         uuid primary key default gen_random_uuid(),
  study_id   uuid not null references public.studies (id) on delete cascade,
  code       text not null,
  created_at timestamptz not null default now(),
  unique (study_id, code)
);

create index forms_study_id_idx on public.forms (study_id);

create table public.form_versions (
  id           uuid primary key default gen_random_uuid(),
  form_id      uuid not null references public.forms (id) on delete cascade,
  version      int not null check (version > 0),
  schema       jsonb not null,
  published_at timestamptz,
  created_at   timestamptz not null default now(),
  unique (form_id, version)
);

create index form_versions_form_id_idx on public.form_versions (form_id);

create table public.participants (
  id         uuid primary key default gen_random_uuid(),
  study_id   uuid not null references public.studies (id) on delete cascade,
  code       text not null,
  created_at timestamptz not null default now(),
  unique (study_id, code)
);

create index participants_study_id_idx on public.participants (study_id);

create table public.submissions (
  id              uuid primary key default gen_random_uuid(),
  study_id        uuid not null references public.studies (id) on delete cascade,
  form_version_id uuid not null references public.form_versions (id),
  participant_id  uuid references public.participants (id),
  collected_by    uuid not null default auth.uid() references auth.users (id),
  client_id       uuid not null,
  collected_at    timestamptz not null,
  data            jsonb not null,
  locked_at       timestamptz,
  updated_at      timestamptz not null default now(),
  deleted_at      timestamptz,
  unique (collected_by, client_id)
);

create index submissions_study_updated_idx
  on public.submissions (study_id, updated_at desc);
create index submissions_study_collector_updated_idx
  on public.submissions (study_id, collected_by, updated_at desc);
create index submissions_form_version_idx
  on public.submissions (form_version_id);
create index submissions_data_idx
  on public.submissions using gin (data jsonb_path_ops);

revoke delete on public.submissions from anon, authenticated;

create or replace function private.claim_new_study()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.study_members (study_id, user_id, role)
  values (new.id, new.created_by, 'pi')
  on conflict (study_id, user_id) do nothing;
  return new;
end;
$$;

create trigger claim_new_study
  after insert on public.studies
  for each row execute function private.claim_new_study();

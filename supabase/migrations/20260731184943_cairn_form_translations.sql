create or replace function private.study_of_form_version(p_form_version uuid)
returns uuid
language sql
security definer
stable
set search_path = ''
as $$
  select f.study_id
    from public.form_versions fv
    join public.forms f on f.id = fv.form_id
   where fv.id = p_form_version
$$;

create table public.form_translations (
  id              uuid primary key default gen_random_uuid(),
  form_version_id uuid not null references public.form_versions (id) on delete cascade,
  lang            text not null check (lang in ('en', 'fr', 'es', 'zh')),
  labels          jsonb not null,
  engine          text,
  reviewed_by     uuid references auth.users (id),
  reviewed_at     timestamptz,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now(),
  unique (form_version_id, lang),
  constraint form_translations_review_pair
    check ((reviewed_at is null) = (reviewed_by is null))
);

create index form_translations_form_version_idx
  on public.form_translations (form_version_id);
create index form_translations_reviewed_by_idx
  on public.form_translations (reviewed_by);

alter table public.form_translations enable row level security;

create policy "read reviewed translations" on public.form_translations
  for select to authenticated
  using (
    private.role_in_study(private.study_of_form_version(form_version_id))
      in ('pi', 'coordinator')
    or (
      private.role_in_study(private.study_of_form_version(form_version_id))
        in ('collector', 'viewer')
      and reviewed_at is not null
    )
  );

create policy "insert translations" on public.form_translations
  for insert to authenticated
  with check (
    private.role_in_study(private.study_of_form_version(form_version_id))
      in ('pi', 'coordinator')
  );

create policy "update translations" on public.form_translations
  for update to authenticated
  using (
    private.role_in_study(private.study_of_form_version(form_version_id))
      in ('pi', 'coordinator')
  )
  with check (
    private.role_in_study(private.study_of_form_version(form_version_id))
      in ('pi', 'coordinator')
  );

create policy "delete translations" on public.form_translations
  for delete to authenticated
  using (
    private.role_in_study(private.study_of_form_version(form_version_id))
      in ('pi', 'coordinator')
  );

create or replace function private.enforce_translation_review()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  new.updated_at := now();

  if (select auth.uid()) is null then
    return new;
  end if;

  if tg_op = 'INSERT' then
    if new.reviewed_at is not null then
      new.reviewed_by := (select auth.uid());
    end if;
    return new;
  end if;

  if new.reviewed_at is distinct from old.reviewed_at then
    if new.reviewed_at is null then
      new.reviewed_by := null;
    else
      new.reviewed_by := (select auth.uid());
    end if;
  elsif new.reviewed_by is distinct from old.reviewed_by then
    raise exception 'reviewed_by is set by the review action, not by the client'
      using errcode = '42501';
  end if;

  if new.labels is distinct from old.labels and old.reviewed_at is not null then
    new.reviewed_at := null;
    new.reviewed_by := null;
  end if;

  return new;
end;
$$;

create trigger enforce_translation_review
  before insert or update on public.form_translations
  for each row execute function private.enforce_translation_review();

create or replace function private.touch_updated_at()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

create trigger touch_updated_at
  before insert or update on public.submissions
  for each row execute function private.touch_updated_at();

create table public.submission_audit (
  id            bigint generated always as identity primary key,
  submission_id uuid not null references public.submissions (id) on delete cascade,
  study_id      uuid not null references public.studies (id) on delete cascade,
  collected_by  uuid not null,
  actor         uuid,
  action        text not null check (
                  action in ('collect', 'amend', 'lock', 'unlock', 'void', 'unvoid')
                ),
  changed_at    timestamptz not null default now(),
  before        jsonb,
  after         jsonb not null
);

create index submission_audit_submission_idx
  on public.submission_audit (submission_id, changed_at desc);
create index submission_audit_study_idx
  on public.submission_audit (study_id, changed_at desc);

create or replace function private.write_submission_audit()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_action text;
begin
  if tg_op = 'INSERT' then
    v_action := 'collect';
  elsif new.locked_at is distinct from old.locked_at then
    v_action := case when new.locked_at is null then 'unlock' else 'lock' end;
  elsif new.deleted_at is distinct from old.deleted_at then
    v_action := case when new.deleted_at is null then 'unvoid' else 'void' end;
  else
    v_action := 'amend';
  end if;

  insert into public.submission_audit
    (submission_id, study_id, collected_by, actor, action, before, after)
  values (
    new.id,
    new.study_id,
    new.collected_by,
    (select auth.uid()),
    v_action,
    case when tg_op = 'UPDATE' then to_jsonb(old) end,
    to_jsonb(new)
  );

  return null;
end;
$$;

create trigger write_submission_audit
  after insert or update on public.submissions
  for each row execute function private.write_submission_audit();

alter table public.submission_audit enable row level security;

create policy "read audit by role" on public.submission_audit
  for select to authenticated
  using (
    private.role_in_study(study_id) in ('pi', 'coordinator', 'viewer')
    or (
      private.role_in_study(study_id) = 'collector'
      and collected_by = (select auth.uid())
    )
  );

revoke insert, update, delete on public.submission_audit from anon, authenticated;

create view public.v_study_progress
  with (security_invoker = true) as
select
  study_id,
  form_version_id,
  date_trunc('day', collected_at) as day,
  count(*) filter (where deleted_at is null)                       as n_submissions,
  count(distinct participant_id) filter (where deleted_at is null) as n_participants
from public.submissions
group by 1, 2, 3;

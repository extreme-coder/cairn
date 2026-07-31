create or replace function private.role_in_study(p_study uuid)
returns public.study_role
language sql
security definer
stable
set search_path = ''
as $$
  select role
    from public.study_members
   where study_id = p_study
     and user_id = (select auth.uid())
$$;

create or replace function private.study_of_form(p_form uuid)
returns uuid
language sql
security definer
stable
set search_path = ''
as $$
  select study_id from public.forms where id = p_form
$$;

alter table public.studies       enable row level security;
alter table public.study_members enable row level security;
alter table public.forms         enable row level security;
alter table public.form_versions enable row level security;
alter table public.participants  enable row level security;
alter table public.submissions   enable row level security;

create policy "read studies you belong to" on public.studies
  for select to authenticated
  using (private.role_in_study(id) is not null);

create policy "create study" on public.studies
  for insert to authenticated
  with check (created_by = (select auth.uid()));

create policy "pi renames study" on public.studies
  for update to authenticated
  using (private.role_in_study(id) = 'pi')
  with check (private.role_in_study(id) = 'pi');

create policy "read roster" on public.study_members
  for select to authenticated
  using (
    user_id = (select auth.uid())
    or private.role_in_study(study_id) in ('pi', 'coordinator')
  );

create policy "pi manages roster" on public.study_members
  for all to authenticated
  using (private.role_in_study(study_id) = 'pi')
  with check (private.role_in_study(study_id) = 'pi');

create policy "read forms" on public.forms
  for select to authenticated
  using (private.role_in_study(study_id) is not null);

create policy "manage forms" on public.forms
  for all to authenticated
  using (private.role_in_study(study_id) in ('pi', 'coordinator'))
  with check (private.role_in_study(study_id) in ('pi', 'coordinator'));

create policy "read published versions" on public.form_versions
  for select to authenticated
  using (
    private.role_in_study(private.study_of_form(form_id)) in ('pi', 'coordinator')
    or (
      private.role_in_study(private.study_of_form(form_id)) in ('collector', 'viewer')
      and published_at is not null
    )
  );

create policy "manage versions" on public.form_versions
  for all to authenticated
  using (private.role_in_study(private.study_of_form(form_id)) in ('pi', 'coordinator'))
  with check (private.role_in_study(private.study_of_form(form_id)) in ('pi', 'coordinator'));

create policy "read participants" on public.participants
  for select to authenticated
  using (private.role_in_study(study_id) is not null);

create policy "enrol participant" on public.participants
  for insert to authenticated
  with check (private.role_in_study(study_id) in ('pi', 'coordinator', 'collector'));

create policy "amend participant" on public.participants
  for update to authenticated
  using (private.role_in_study(study_id) in ('pi', 'coordinator'))
  with check (private.role_in_study(study_id) in ('pi', 'coordinator'));

create policy "read by role" on public.submissions
  for select to authenticated
  using (
    private.role_in_study(study_id) in ('pi', 'coordinator', 'viewer')
    or (
      private.role_in_study(study_id) = 'collector'
      and collected_by = (select auth.uid())
    )
  );

create policy "collect own" on public.submissions
  for insert to authenticated
  with check (
    private.role_in_study(study_id) in ('pi', 'coordinator', 'collector')
    and collected_by = (select auth.uid())
  );

create policy "amend unlocked" on public.submissions
  for update to authenticated
  using (
    locked_at is null
    and (
      private.role_in_study(study_id) in ('pi', 'coordinator')
      or (
        private.role_in_study(study_id) = 'collector'
        and collected_by = (select auth.uid())
      )
    )
  )
  with check (
    private.role_in_study(study_id) in ('pi', 'coordinator')
    or (
      private.role_in_study(study_id) = 'collector'
      and collected_by = (select auth.uid())
    )
  );

create or replace function private.enforce_submission_invariants()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_role public.study_role;
begin

  if (select auth.uid()) is null then
    return new;
  end if;

  if new.study_id        is distinct from old.study_id
  or new.form_version_id is distinct from old.form_version_id
  or new.collected_by    is distinct from old.collected_by
  or new.client_id       is distinct from old.client_id then
    raise exception
      'submission provenance is immutable: study, form version, collector and client id cannot change'
      using errcode = '42501';
  end if;

  if new.locked_at is distinct from old.locked_at then
    v_role := private.role_in_study(new.study_id);
    if v_role is null or v_role not in ('pi', 'coordinator') then
      raise exception 'only a PI or coordinator can lock or unlock a submission'
        using errcode = '42501';
    end if;
  end if;

  return new;
end;
$$;

create trigger enforce_submission_invariants
  before update on public.submissions
  for each row execute function private.enforce_submission_invariants();

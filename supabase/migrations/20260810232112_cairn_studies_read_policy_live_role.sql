create or replace function private.role_in_study_now(p_study uuid)
returns public.study_role
language sql
volatile
security definer
set search_path = ''
as $$
  select role
    from public.study_members
   where study_id = p_study
     and user_id = (select auth.uid())
$$;

drop policy "read studies you belong to" on public.studies;

create policy "read studies you belong to" on public.studies
  for select to authenticated
  using (private.role_in_study_now(id) is not null);

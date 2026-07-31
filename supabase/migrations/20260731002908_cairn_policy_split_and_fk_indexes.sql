create index studies_created_by_idx on public.studies (created_by);
create index submissions_participant_idx on public.submissions (participant_id);

drop policy "manage forms" on public.forms;

create policy "insert forms" on public.forms
  for insert to authenticated
  with check (private.role_in_study(study_id) in ('pi', 'coordinator'));

create policy "update forms" on public.forms
  for update to authenticated
  using (private.role_in_study(study_id) in ('pi', 'coordinator'))
  with check (private.role_in_study(study_id) in ('pi', 'coordinator'));

create policy "delete forms" on public.forms
  for delete to authenticated
  using (private.role_in_study(study_id) in ('pi', 'coordinator'));

drop policy "manage versions" on public.form_versions;

create policy "insert versions" on public.form_versions
  for insert to authenticated
  with check (private.role_in_study(private.study_of_form(form_id)) in ('pi', 'coordinator'));

create policy "update versions" on public.form_versions
  for update to authenticated
  using (private.role_in_study(private.study_of_form(form_id)) in ('pi', 'coordinator'))
  with check (private.role_in_study(private.study_of_form(form_id)) in ('pi', 'coordinator'));

create policy "delete versions" on public.form_versions
  for delete to authenticated
  using (private.role_in_study(private.study_of_form(form_id)) in ('pi', 'coordinator'));

drop policy "pi manages roster" on public.study_members;

create policy "pi adds member" on public.study_members
  for insert to authenticated
  with check (private.role_in_study(study_id) = 'pi');

create policy "pi changes member role" on public.study_members
  for update to authenticated
  using (private.role_in_study(study_id) = 'pi')
  with check (private.role_in_study(study_id) = 'pi');

create policy "pi removes member" on public.study_members
  for delete to authenticated
  using (private.role_in_study(study_id) = 'pi');

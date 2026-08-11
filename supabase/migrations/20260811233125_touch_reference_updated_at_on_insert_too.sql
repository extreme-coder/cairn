drop trigger touch_studies_updated_at on public.studies;
drop trigger touch_study_members_updated_at on public.study_members;
drop trigger touch_forms_updated_at on public.forms;
drop trigger touch_form_versions_updated_at on public.form_versions;
drop trigger touch_participants_updated_at on public.participants;

create trigger touch_studies_updated_at
  before insert or update on public.studies
  for each row execute function private.touch_updated_at();

create trigger touch_study_members_updated_at
  before insert or update on public.study_members
  for each row execute function private.touch_updated_at();

create trigger touch_forms_updated_at
  before insert or update on public.forms
  for each row execute function private.touch_updated_at();

create trigger touch_form_versions_updated_at
  before insert or update on public.form_versions
  for each row execute function private.touch_updated_at();

create trigger touch_participants_updated_at
  before insert or update on public.participants
  for each row execute function private.touch_updated_at();

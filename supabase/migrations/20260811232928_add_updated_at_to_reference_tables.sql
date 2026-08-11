alter table public.studies add column updated_at timestamptz not null default now();
alter table public.study_members add column updated_at timestamptz not null default now();
alter table public.forms add column updated_at timestamptz not null default now();
alter table public.form_versions add column updated_at timestamptz not null default now();
alter table public.participants add column updated_at timestamptz not null default now();

update public.studies set updated_at = created_at;
update public.study_members set updated_at = added_at;
update public.forms set updated_at = created_at;
update public.form_versions set updated_at = created_at;
update public.participants set updated_at = created_at;

create trigger touch_studies_updated_at
  before update on public.studies
  for each row execute function private.touch_updated_at();

create trigger touch_study_members_updated_at
  before update on public.study_members
  for each row execute function private.touch_updated_at();

create trigger touch_forms_updated_at
  before update on public.forms
  for each row execute function private.touch_updated_at();

create trigger touch_form_versions_updated_at
  before update on public.form_versions
  for each row execute function private.touch_updated_at();

create trigger touch_participants_updated_at
  before update on public.participants
  for each row execute function private.touch_updated_at();

create index studies_updated_at_idx on public.studies (updated_at);
create index study_members_study_id_updated_at_idx on public.study_members (study_id, updated_at);
create index forms_study_id_updated_at_idx on public.forms (study_id, updated_at);
create index form_versions_form_id_updated_at_idx on public.form_versions (form_id, updated_at);
create index participants_study_id_updated_at_idx on public.participants (study_id, updated_at);

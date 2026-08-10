alter table public.study_members
  drop constraint study_members_study_id_fkey;

alter table public.study_members
  add constraint study_members_study_id_fkey
  foreign key (study_id) references public.studies (id)
  on delete cascade
  deferrable initially deferred;

drop trigger claim_new_study on public.studies;

create trigger claim_new_study
  before insert on public.studies
  for each row execute function private.claim_new_study();

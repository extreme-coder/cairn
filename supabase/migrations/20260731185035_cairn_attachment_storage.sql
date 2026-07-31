insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'attachments',
  'attachments',
  false,
  26214400,
  array['image/jpeg', 'image/png', 'image/webp', 'audio/mpeg', 'audio/mp4', 'audio/ogg']
)
on conflict (id) do nothing;

create or replace function private.path_uuid(p_name text, p_index int)
returns uuid
language plpgsql
immutable
set search_path = ''
as $$
declare
  v uuid;
begin
  begin
    v := (string_to_array(p_name, '/'))[p_index]::uuid;
  exception when others then
    return null;
  end;
  return v;
end;
$$;

create or replace function private.can_read_attachment(p_name text)
returns boolean
language sql
security definer
stable
set search_path = ''
as $$
  select exists (
    select 1
      from public.submissions s
     where s.id = private.path_uuid(p_name, 2)
       and s.study_id = private.path_uuid(p_name, 1)
       and (
         private.role_in_study(s.study_id) in ('pi', 'coordinator', 'viewer')
         or (
           private.role_in_study(s.study_id) = 'collector'
           and s.collected_by = (select auth.uid())
         )
       )
  )
$$;

create or replace function private.can_write_attachment(p_name text)
returns boolean
language sql
security definer
stable
set search_path = ''
as $$
  select exists (
    select 1
      from public.submissions s
     where s.id = private.path_uuid(p_name, 2)
       and s.study_id = private.path_uuid(p_name, 1)
       and s.locked_at is null
       and (
         private.role_in_study(s.study_id) in ('pi', 'coordinator')
         or (
           private.role_in_study(s.study_id) = 'collector'
           and s.collected_by = (select auth.uid())
         )
       )
  )
$$;

create policy "read attachments by role" on storage.objects
  for select to authenticated
  using (bucket_id = 'attachments' and private.can_read_attachment(name));

create policy "upload attachments" on storage.objects
  for insert to authenticated
  with check (bucket_id = 'attachments' and private.can_write_attachment(name));

create policy "replace attachments" on storage.objects
  for update to authenticated
  using (bucket_id = 'attachments' and private.can_write_attachment(name))
  with check (bucket_id = 'attachments' and private.can_write_attachment(name));

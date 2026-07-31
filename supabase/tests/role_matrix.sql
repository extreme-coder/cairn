begin;

set local search_path = public, extensions;

create extension if not exists pgtap with schema extensions;

create or replace function pg_temp.role_matrix() returns setof text
language plpgsql
as $fn$
declare
  v_owner text := current_user;
  v_n     bigint;
  v_t     text;
begin
  return next plan(24);

  insert into auth.users
    (instance_id, id, aud, role, email, encrypted_password,
     email_confirmed_at, created_at, updated_at,
     raw_app_meta_data, raw_user_meta_data)
  select
    '00000000-0000-0000-0000-000000000000',
    u.id, 'authenticated', 'authenticated', u.email, '',
    now(), now(), now(),
    '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb
  from (values
    ('e0000000-0000-0000-0000-000000000001'::uuid, 'pi@cairn.test'),
    ('e0000000-0000-0000-0000-000000000002'::uuid, 'coordinator@cairn.test'),
    ('e0000000-0000-0000-0000-000000000003'::uuid, 'collector.a@cairn.test'),
    ('e0000000-0000-0000-0000-000000000004'::uuid, 'collector.b@cairn.test'),
    ('e0000000-0000-0000-0000-000000000005'::uuid, 'viewer@cairn.test'),
    ('e0000000-0000-0000-0000-000000000006'::uuid, 'outside.pi@cairn.test')
  ) as u(id, email);

  insert into public.studies (id, name, created_by) values
    ('11111111-1111-1111-1111-111111111111',
     'Kluane ground squirrel survey', 'e0000000-0000-0000-0000-000000000001'),
    ('22222222-2222-2222-2222-222222222222',
     'Peel watershed water quality',  'e0000000-0000-0000-0000-000000000006');

  insert into public.study_members (study_id, user_id, role) values
    ('11111111-1111-1111-1111-111111111111', 'e0000000-0000-0000-0000-000000000002', 'coordinator'),
    ('11111111-1111-1111-1111-111111111111', 'e0000000-0000-0000-0000-000000000003', 'collector'),
    ('11111111-1111-1111-1111-111111111111', 'e0000000-0000-0000-0000-000000000004', 'collector'),
    ('11111111-1111-1111-1111-111111111111', 'e0000000-0000-0000-0000-000000000005', 'viewer');

  insert into public.forms (id, study_id, code) values
    ('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'baseline_intake'),
    ('88888888-8888-8888-8888-888888888888', '22222222-2222-2222-2222-222222222222', 'water_quality');

  insert into public.form_versions (id, form_id, version, schema, published_at) values
    ('44444444-4444-4444-4444-444444444444', '33333333-3333-3333-3333-333333333333', 1, '{"fields":[]}', now()),
    ('55555555-5555-5555-5555-555555555555', '33333333-3333-3333-3333-333333333333', 2, '{"fields":[]}', null),
    ('99999999-9999-9999-9999-999999999999', '88888888-8888-8888-8888-888888888888', 1, '{"fields":[]}', now());

  insert into public.participants (id, study_id, code) values
    ('66666666-6666-6666-6666-666666666666', '11111111-1111-1111-1111-111111111111', 'KL-0148');

  insert into public.submissions
    (id, study_id, form_version_id, participant_id, collected_by, client_id, collected_at, data)
  values
    ('77777777-7777-7777-7777-777777777771', '11111111-1111-1111-1111-111111111111',
     '44444444-4444-4444-4444-444444444444', '66666666-6666-6666-6666-666666666666',
     'e0000000-0000-0000-0000-000000000003', 'a1a1a1a1-0000-0000-0000-000000000001',
     now(), '{"mass_g":182}'),
    ('77777777-7777-7777-7777-777777777772', '11111111-1111-1111-1111-111111111111',
     '44444444-4444-4444-4444-444444444444', '66666666-6666-6666-6666-666666666666',
     'e0000000-0000-0000-0000-000000000003', 'a1a1a1a1-0000-0000-0000-000000000002',
     now(), '{"mass_g":190}'),
    ('77777777-7777-7777-7777-777777777773', '11111111-1111-1111-1111-111111111111',
     '44444444-4444-4444-4444-444444444444', '66666666-6666-6666-6666-666666666666',
     'e0000000-0000-0000-0000-000000000004', 'b1b1b1b1-0000-0000-0000-000000000001',
     now(), '{"mass_g":205}'),
    ('77777777-7777-7777-7777-777777777774', '22222222-2222-2222-2222-222222222222',
     '99999999-9999-9999-9999-999999999999', null,
     'e0000000-0000-0000-0000-000000000006', 'c1c1c1c1-0000-0000-0000-000000000001',
     now(), '{"ph":7.21}');

  perform set_config('role', 'authenticated', true);
  perform set_config('request.jwt.claims',
    '{"sub":"e0000000-0000-0000-0000-000000000003","role":"authenticated"}', true);

  select count(*) into v_n from public.submissions;
  return next ok(v_n = 2, 'Collector sees only their own submissions, saw ' || v_n);

  return next is_empty(
    $q$ select 1 from public.submissions
         where id = '77777777-7777-7777-7777-777777777773' $q$,
    'Collector cannot read a teammate submission');

  return next lives_ok(
    $q$ insert into public.submissions
          (study_id, form_version_id, client_id, collected_at, data)
        values ('11111111-1111-1111-1111-111111111111',
                '44444444-4444-4444-4444-444444444444',
                'a1a1a1a1-0000-0000-0000-000000000003', now(), '{"mass_g":175}') $q$,
    'Collector can collect a submission of their own');

  return next throws_ok(
    $q$ insert into public.submissions
          (study_id, form_version_id, collected_by, client_id, collected_at, data)
        values ('11111111-1111-1111-1111-111111111111',
                '44444444-4444-4444-4444-444444444444',
                'e0000000-0000-0000-0000-000000000004',
                'a1a1a1a1-0000-0000-0000-000000000004', now(), '{}') $q$,
    '42501', null::text,
    'Collector cannot collect on behalf of a teammate');

  return next lives_ok(
    $q$ update public.submissions set data = '{"mass_g":183}'
         where id = '77777777-7777-7777-7777-777777777771' $q$,
    'Collector can amend their own unlocked submission');

  return next throws_ok(
    $q$ update public.submissions set locked_at = now()
         where id = '77777777-7777-7777-7777-777777777772' $q$,
    '42501', null::text,
    'Collector cannot lock a submission');

  select count(*) into v_n from public.form_versions;
  return next ok(v_n = 1,
    'Collector sees the published form version but not the draft, saw ' || v_n);

  return next is_empty(
    $q$ select 1 from public.studies
         where id = '22222222-2222-2222-2222-222222222222' $q$,
    'Collector cannot see a study they are not a member of');

  return next is_empty(
    $q$ select 1 from public.v_study_progress
         where study_id = '22222222-2222-2222-2222-222222222222' $q$,
    'v_study_progress honours the caller policies, so security_invoker is on');

  return next throws_ok(
    $q$ delete from public.submissions
         where id = '77777777-7777-7777-7777-777777777771' $q$,
    '42501', null::text,
    'Nothing is hard-deleted: DELETE is revoked on submissions');

  return next throws_ok(
    $q$ insert into public.submission_audit
          (submission_id, study_id, collected_by, action, after)
        values ('77777777-7777-7777-7777-777777777771',
                '11111111-1111-1111-1111-111111111111',
                'e0000000-0000-0000-0000-000000000003', 'amend', '{}') $q$,
    '42501', null::text,
    'The audit table is append-only from any client');

  select count(*) into v_n from public.submission_audit
   where submission_id = '77777777-7777-7777-7777-777777777771';
  return next ok(v_n = 2,
    'Audit records the original collection and the amendment, saw ' || v_n);

  update public.submissions set data = '{"tampered":true}'
   where id = '77777777-7777-7777-7777-777777777773';

  perform set_config('role', v_owner, true);
  perform set_config('request.jwt.claims', '', true);

  select data ->> 'tampered' into v_t from public.submissions
   where id = '77777777-7777-7777-7777-777777777773';
  return next ok(v_t is null,
    'A collector amending a teammate row changes nothing');

  perform set_config('role', 'authenticated', true);
  perform set_config('request.jwt.claims',
    '{"sub":"e0000000-0000-0000-0000-000000000002","role":"authenticated"}', true);

  select count(*) into v_n from public.submissions
   where study_id = '11111111-1111-1111-1111-111111111111';
  return next ok(v_n = 4,
    'Coordinator sees every submission in the study, saw ' || v_n);

  select count(*) into v_n from public.form_versions;
  return next ok(v_n = 2,
    'Coordinator sees the draft form version as well as the published one, saw ' || v_n);

  return next lives_ok(
    $q$ update public.submissions set data = '{"mass_g":184,"note":"checked"}'
         where id = '77777777-7777-7777-7777-777777777771' $q$,
    'Coordinator can amend a collector submission');

  return next lives_ok(
    $q$ update public.submissions set locked_at = now()
         where id = '77777777-7777-7777-7777-777777777771' $q$,
    'Coordinator can lock a submission');

  return next throws_ok(
    $q$ delete from public.submissions
         where id = '77777777-7777-7777-7777-777777777772' $q$,
    '42501', null::text,
    'Coordinator cannot hard-delete either');

  return next is_empty(
    $q$ select 1 from public.submissions
         where study_id = '22222222-2222-2222-2222-222222222222' $q$,
    'Coordinator cannot read another study');

  update public.submissions set data = '{"tampered":true}'
   where id = '77777777-7777-7777-7777-777777777771';

  perform set_config('role', v_owner, true);
  perform set_config('request.jwt.claims', '', true);

  select data ->> 'tampered' into v_t from public.submissions
   where id = '77777777-7777-7777-7777-777777777771';
  return next ok(v_t is null,
    'A locked submission cannot be amended, including by the coordinator who locked it');

  perform set_config('role', 'authenticated', true);
  perform set_config('request.jwt.claims',
    '{"sub":"e0000000-0000-0000-0000-000000000005","role":"authenticated"}', true);

  select count(*) into v_n from public.submissions
   where study_id = '11111111-1111-1111-1111-111111111111';
  return next ok(v_n = 4, 'Viewer reads every submission in the study, saw ' || v_n);

  return next throws_ok(
    $q$ insert into public.submissions
          (study_id, form_version_id, client_id, collected_at, data)
        values ('11111111-1111-1111-1111-111111111111',
                '44444444-4444-4444-4444-444444444444',
                'd1d1d1d1-0000-0000-0000-000000000001', now(), '{}') $q$,
    '42501', null::text,
    'Viewer cannot collect');

  update public.submissions set data = '{"tampered":true}'
   where id = '77777777-7777-7777-7777-777777777772';

  perform set_config('role', v_owner, true);
  perform set_config('request.jwt.claims', '', true);

  select data ->> 'tampered' into v_t from public.submissions
   where id = '77777777-7777-7777-7777-777777777772';
  return next ok(v_t is null, 'Viewer changes nothing');

  perform set_config('role', 'authenticated', true);
  perform set_config('request.jwt.claims',
    '{"sub":"e0000000-0000-0000-0000-000000000003","role":"authenticated"}', true);

  insert into public.submissions
    (study_id, form_version_id, client_id, collected_at, data)
  values ('11111111-1111-1111-1111-111111111111',
          '44444444-4444-4444-4444-444444444444',
          'a1a1a1a1-0000-0000-0000-000000000002', now(), '{"mass_g":191}')
  on conflict (collected_by, client_id) do update set data = excluded.data;

  select count(*) into v_n from public.submissions;
  return next ok(v_n = 3,
    'Replaying a queued submission upserts rather than duplicating, saw ' || v_n);

  perform set_config('role', v_owner, true);
  perform set_config('request.jwt.claims', '', true);

  return query select * from finish();
end;
$fn$;

select * from pg_temp.role_matrix();

rollback;

alter table "events"."event_pop_ups" add column "user_ids" text[] not null default '{}'::text[];



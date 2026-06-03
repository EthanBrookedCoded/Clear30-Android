create sequence "comms"."feature_ideas_id_seq";

create table "comms"."feature_ideas" (
    "id" bigint not null default nextval('comms.feature_ideas_id_seq'::regclass),
    "title" text not null,
    "body" text not null,
    "hex_color" text not null default '#007AFF'::text,
    "score" integer not null default 0,
    "user_id" text not null,
    "created_at" timestamp with time zone not null default now(),
    "tag" text not null default 'Idea'::text
);


alter table "comms"."feature_ideas" enable row level security;

alter sequence "comms"."feature_ideas_id_seq" owned by "comms"."feature_ideas"."id";

CREATE UNIQUE INDEX feature_ideas_pkey ON comms.feature_ideas USING btree (id);

CREATE INDEX idx_feature_ideas_created_at ON comms.feature_ideas USING btree (created_at DESC);

CREATE INDEX idx_feature_ideas_score ON comms.feature_ideas USING btree (score DESC);

CREATE INDEX idx_feature_ideas_user_id ON comms.feature_ideas USING btree (user_id);

alter table "comms"."feature_ideas" add constraint "feature_ideas_pkey" PRIMARY KEY using index "feature_ideas_pkey";

alter table "comms"."feature_ideas" add constraint "fk_feature_ideas_user_id" FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE not valid;

alter table "comms"."feature_ideas" validate constraint "fk_feature_ideas_user_id";

create policy "All can select"
on "comms"."feature_ideas"
as permissive
for select
to public
using (true);


create policy "User can insert for self"
on "comms"."feature_ideas"
as permissive
for insert
to public
with check ((user_id = ( SELECT get_user_id() AS get_user_id)));




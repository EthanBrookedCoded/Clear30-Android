create table "community"."reported_posts" (
    "id" uuid not null default gen_random_uuid(),
    "created_at" timestamp with time zone not null default now(),
    "updated_at" timestamp with time zone default now(),
    "user_id" text,
    "post_id" uuid,
    "is_flagged_by_llm" boolean default false
);


alter table "community"."reported_posts" enable row level security;

alter table "community"."posts" alter column "is_hidden" set default false;

alter table "community"."posts" alter column "is_pinned" set default false;

CREATE UNIQUE INDEX reported_posts_pkey ON community.reported_posts USING btree (id);

alter table "community"."reported_posts" add constraint "reported_posts_pkey" PRIMARY KEY using index "reported_posts_pkey";

alter table "community"."reported_posts" add constraint "reported_posts_post_id_fkey" FOREIGN KEY (post_id) REFERENCES community.posts(id) not valid;

alter table "community"."reported_posts" validate constraint "reported_posts_post_id_fkey";

alter table "community"."reported_posts" add constraint "reported_posts_user_id_fkey" FOREIGN KEY (user_id) REFERENCES users(id) not valid;

alter table "community"."reported_posts" validate constraint "reported_posts_user_id_fkey";



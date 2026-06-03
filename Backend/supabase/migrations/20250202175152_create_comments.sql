create table "community"."comments" (
    "id" uuid not null default gen_random_uuid(),
    "created_at" timestamp with time zone not null default now(),
    "post_id" uuid not null,
    "user_id" text not null,
    "body" text,
    "parent_comment_id" uuid
);


alter table "community"."comments" enable row level security;

alter table "community"."tags" alter column "name" set not null;

CREATE UNIQUE INDEX comments_pkey ON community.comments USING btree (id);

alter table "community"."comments" add constraint "comments_pkey" PRIMARY KEY using index "comments_pkey";

alter table "community"."comments" add constraint "comments_parent_comment_id_fkey" FOREIGN KEY (parent_comment_id) REFERENCES community.comments(id) not valid;

alter table "community"."comments" validate constraint "comments_parent_comment_id_fkey";

alter table "community"."comments" add constraint "comments_post_id_fkey" FOREIGN KEY (post_id) REFERENCES community.posts(id) ON DELETE CASCADE not valid;

alter table "community"."comments" validate constraint "comments_post_id_fkey";

alter table "community"."comments" add constraint "comments_user_id_fkey" FOREIGN KEY (user_id) REFERENCES users(id) not valid;

alter table "community"."comments" validate constraint "comments_user_id_fkey";



CREATE UNIQUE INDEX deleted_posts_log_pkey ON community.deleted_posts_log USING btree (id);

alter table "community"."deleted_posts_log" add constraint "deleted_posts_log_pkey" PRIMARY KEY using index "deleted_posts_log_pkey";



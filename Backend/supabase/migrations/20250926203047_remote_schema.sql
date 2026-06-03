alter table "community"."activities" drop constraint "activities_actor_id_fkey";

alter table "community"."activities" drop constraint "activities_recipient_id_fkey";

alter table "community"."comments" drop constraint "comments_user_id_fkey";

alter table "community"."posts" drop constraint "posts_user_id_fkey";

alter table "community"."profiles" drop constraint "profiles_id_fkey";

alter table "community"."reactions" drop constraint "reactions_user_id_fkey";

alter table "community"."reported_posts" drop constraint "reported_posts_user_id_fkey";

alter table "community"."activities" add constraint "activities_actor_id_fkey" FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE CASCADE not valid;

alter table "community"."activities" validate constraint "activities_actor_id_fkey";

alter table "community"."activities" add constraint "activities_recipient_id_fkey" FOREIGN KEY (recipient_id) REFERENCES users(id) ON DELETE CASCADE not valid;

alter table "community"."activities" validate constraint "activities_recipient_id_fkey";

alter table "community"."comments" add constraint "comments_user_id_fkey" FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE not valid;

alter table "community"."comments" validate constraint "comments_user_id_fkey";

alter table "community"."posts" add constraint "posts_user_id_fkey" FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE not valid;

alter table "community"."posts" validate constraint "posts_user_id_fkey";

alter table "community"."profiles" add constraint "profiles_id_fkey" FOREIGN KEY (id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE not valid;

alter table "community"."profiles" validate constraint "profiles_id_fkey";

alter table "community"."reactions" add constraint "reactions_user_id_fkey" FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE not valid;

alter table "community"."reactions" validate constraint "reactions_user_id_fkey";

alter table "community"."reported_posts" add constraint "reported_posts_user_id_fkey" FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE not valid;

alter table "community"."reported_posts" validate constraint "reported_posts_user_id_fkey";



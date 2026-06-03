alter table "community"."comments" drop constraint "comments_parent_comment_id_fkey";

alter table "community"."comments" drop constraint "comments_user_id_fkey";

alter table "community"."post_tags" drop constraint "post_tags_post_id_fkey";

alter table "community"."post_tags" drop constraint "post_tags_tag_id_fkey";

alter table "community"."posts" drop constraint "posts_user_id_fkey";

alter table "community"."reactions" drop constraint "reactions_post_id_fkey";

alter table "community"."reactions" drop constraint "reactions_user_id_fkey";

alter table "community"."comments" add constraint "comments_parent_comment_id_fkey" FOREIGN KEY (parent_comment_id) REFERENCES community.comments(id) ON UPDATE CASCADE ON DELETE CASCADE not valid;

alter table "community"."comments" validate constraint "comments_parent_comment_id_fkey";

alter table "community"."comments" add constraint "comments_user_id_fkey" FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE not valid;

alter table "community"."comments" validate constraint "comments_user_id_fkey";

alter table "community"."post_tags" add constraint "post_tags_post_id_fkey" FOREIGN KEY (post_id) REFERENCES community.posts(id) ON UPDATE CASCADE ON DELETE CASCADE not valid;

alter table "community"."post_tags" validate constraint "post_tags_post_id_fkey";

alter table "community"."post_tags" add constraint "post_tags_tag_id_fkey" FOREIGN KEY (tag_id) REFERENCES community.tags(id) ON UPDATE CASCADE ON DELETE CASCADE not valid;

alter table "community"."post_tags" validate constraint "post_tags_tag_id_fkey";

alter table "community"."posts" add constraint "posts_user_id_fkey" FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE not valid;

alter table "community"."posts" validate constraint "posts_user_id_fkey";

alter table "community"."reactions" add constraint "reactions_post_id_fkey" FOREIGN KEY (post_id) REFERENCES community.posts(id) ON UPDATE CASCADE ON DELETE CASCADE not valid;

alter table "community"."reactions" validate constraint "reactions_post_id_fkey";

alter table "community"."reactions" add constraint "reactions_user_id_fkey" FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE not valid;

alter table "community"."reactions" validate constraint "reactions_user_id_fkey";


create table "public"."deleted_users" (
    "id" text not null,
    "data" jsonb not null,
    "deleted_at" timestamp with time zone default now()
);


alter table "public"."deleted_users" enable row level security;

CREATE UNIQUE INDEX deleted_users_pkey ON public.deleted_users USING btree (id);

alter table "public"."deleted_users" add constraint "deleted_users_pkey" PRIMARY KEY using index "deleted_users_pkey";

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.delete_user()
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
    _user_id text;
    _auth_id uuid;
    _user_exists boolean;
    _user_data jsonb;
BEGIN
    -- Get the current user ID
    _user_id := public.get_user_id();
    
    -- Check if user exists
    IF _user_id IS NULL THEN
        RAISE EXCEPTION 'User not found';
    END IF;
    
    -- Verify user exists in public.users
    SELECT EXISTS (
        SELECT 1 FROM public.users WHERE id = _user_id
    ) INTO _user_exists;
    
    IF NOT _user_exists THEN
        RAISE EXCEPTION 'User not found in public.users table';
    END IF;
    
    -- Get the auth_id and capture ALL user data as JSON
    SELECT auth_id, row_to_json(users.*)::jsonb
    INTO _auth_id, _user_data
    FROM public.users
    WHERE id = _user_id;
    
    -- Check if auth_id exists
    IF _auth_id IS NULL THEN
        RAISE EXCEPTION 'User does not have an auth_id';
    END IF;
    
    -- Archive user data before deletion
    INSERT INTO public.deleted_users (id, data)
    VALUES (_user_id, _user_data);
    
    -- Delete from public.users first
    DELETE FROM public.users
    WHERE id = _user_id;
    
    -- Delete from auth.users using the auth_id
    DELETE FROM auth.users
    WHERE id = _auth_id;
    
EXCEPTION
    WHEN others THEN
        RAISE EXCEPTION 'Error deleting user account: %', SQLERRM;
END;
$function$
;

grant delete on table "public"."deleted_users" to "anon";

grant insert on table "public"."deleted_users" to "anon";

grant references on table "public"."deleted_users" to "anon";

grant select on table "public"."deleted_users" to "anon";

grant trigger on table "public"."deleted_users" to "anon";

grant truncate on table "public"."deleted_users" to "anon";

grant update on table "public"."deleted_users" to "anon";

grant delete on table "public"."deleted_users" to "authenticated";

grant insert on table "public"."deleted_users" to "authenticated";

grant references on table "public"."deleted_users" to "authenticated";

grant select on table "public"."deleted_users" to "authenticated";

grant trigger on table "public"."deleted_users" to "authenticated";

grant truncate on table "public"."deleted_users" to "authenticated";

grant update on table "public"."deleted_users" to "authenticated";

grant delete on table "public"."deleted_users" to "service_role";

grant insert on table "public"."deleted_users" to "service_role";

grant references on table "public"."deleted_users" to "service_role";

grant select on table "public"."deleted_users" to "service_role";

grant trigger on table "public"."deleted_users" to "service_role";

grant truncate on table "public"."deleted_users" to "service_role";

grant update on table "public"."deleted_users" to "service_role";

create policy "Disable all access"
on "public"."deleted_users"
as permissive
for all
to public
using (false);




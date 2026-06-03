create table "groups"."group_messages" (
    "id" uuid not null default gen_random_uuid(),
    "group_id" uuid not null,
    "user_id" text not null,
    "message" text not null,
    "timestamp" timestamp with time zone not null default now(),
    "is_deleted" boolean not null default false
);


CREATE UNIQUE INDEX group_messages_pkey ON groups.group_messages USING btree (id);

alter table "groups"."group_messages" add constraint "group_messages_pkey" PRIMARY KEY using index "group_messages_pkey";

alter table "groups"."group_messages" add constraint "group_messages_group_id_fkey" FOREIGN KEY (group_id) REFERENCES groups.groups(id) ON DELETE CASCADE not valid;

alter table "groups"."group_messages" validate constraint "group_messages_group_id_fkey";

alter table "groups"."group_messages" add constraint "group_messages_user_id_fkey" FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE not valid;

alter table "groups"."group_messages" validate constraint "group_messages_user_id_fkey";

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION groups.get_user_group()
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'groups'
AS $function$
DECLARE
  v_user_id text;
  v_group_id uuid;
BEGIN
  -- Get the actual user ID using the public.get_user_id() function
  SELECT public.get_user_id() INTO v_user_id;
  
  -- If user not found, return null
  IF v_user_id IS NULL THEN
    RETURN NULL;
  END IF;
  
  -- Get any group the user is a member of - just getting the first one
  -- according to the natural order (likely insertion order)
  SELECT group_id INTO v_group_id
  FROM groups.group_members
  WHERE user_id = v_user_id
  LIMIT 1;
  
  RETURN v_group_id;
END;
$function$
;



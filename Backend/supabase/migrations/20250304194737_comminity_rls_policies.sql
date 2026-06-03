set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.get_user_id()
 RETURNS text
 LANGUAGE plpgsql
AS $function$
BEGIN
  RETURN (SELECT id FROM users WHERE auth_id = auth.uid());
END;
$function$
;


drop policy "Users can see their own activities" on "community"."activities";

drop policy "comments" on "community"."comments";

drop policy "Only admins can view deleted posts log" on "community"."deleted_posts_log";

drop policy "post_tags" on "community"."post_tags";

drop policy "Posts" on "community"."posts";

drop policy "reactions" on "community"."reactions";

drop policy "reported_posts" on "community"."reported_posts";

drop policy "tags" on "community"."tags";

create policy "Users can set read for own activity"
on "community"."activities"
as permissive
for update
to public
using ((recipient_id = ( SELECT get_user_id() AS get_user_id)))
with check ((recipient_id = ( SELECT get_user_id() AS get_user_id)));


create policy "Users can view own activity"
on "community"."activities"
as permissive
for select
to public
using ((recipient_id = ( SELECT get_user_id() AS get_user_id)));


create policy "Public select"
on "community"."comments"
as permissive
for select
to public
using (true);


create policy "Users can create for self"
on "community"."comments"
as permissive
for insert
to public
with check ((user_id = ( SELECT get_user_id() AS get_user_id)));


create policy "Admin only"
on "community"."deleted_posts_log"
as permissive
for all
to authenticated
using (admin_check());


create policy "Public select"
on "community"."post_tags"
as permissive
for select
to public
using (true);


create policy "Public select"
on "community"."posts"
as permissive
for select
to public
using (true);


create policy "Public select"
on "community"."reactions"
as permissive
for select
to public
using (true);


create policy "Users can react for self"
on "community"."reactions"
as permissive
for insert
to public
with check ((user_id = ( SELECT get_user_id() AS get_user_id)));


create policy "Admin all"
on "community"."reported_posts"
as permissive
for all
to public
using (( SELECT admin_check() AS admin_check));


create policy "Users can insert for self"
on "community"."reported_posts"
as permissive
for insert
to public
with check ((user_id = ( SELECT get_user_id() AS get_user_id)));


create policy "Users can select for self"
on "community"."reported_posts"
as permissive
for select
to public
using ((user_id = ( SELECT get_user_id() AS get_user_id)));


create policy "Admin all"
on "community"."tags"
as permissive
for all
to public
using (( SELECT admin_check() AS admin_check));


create policy "Public select"
on "community"."tags"
as permissive
for select
to public
using (true);


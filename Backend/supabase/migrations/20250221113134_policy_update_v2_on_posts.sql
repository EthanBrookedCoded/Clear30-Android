drop policy "comments" on "community"."comments";

drop policy "post_tags" on "community"."post_tags";

drop policy "reported_posts" on "community"."reported_posts";

drop policy "tags" on "community"."tags";

create policy "comments"
on "community"."comments"
as permissive
for all
to public
using (true);


create policy "post_tags"
on "community"."post_tags"
as permissive
for all
to public
using (true);


create policy "reported_posts"
on "community"."reported_posts"
as permissive
for all
to public
using (true);


create policy "tags"
on "community"."tags"
as permissive
for all
to public
using (true);




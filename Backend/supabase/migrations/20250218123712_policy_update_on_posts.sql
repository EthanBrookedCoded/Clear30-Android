drop policy "Posts" on "community"."posts";

create policy "Posts"
on "community"."posts"
as permissive
for all
to public
using (true);




create policy "Disable public access"
on "library"."campaigns"
as permissive
for all
to public
using (false);




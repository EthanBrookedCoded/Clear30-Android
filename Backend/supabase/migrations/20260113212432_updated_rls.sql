drop policy "Service role can do everything on subscription_cache" on "payment"."subscription_cache";


  create policy "Disable all access"
  on "payment"."subscription_cache"
  as permissive
  for select
  to public
using (false);




drop policy "Service role only" on "comms"."video_testimonial_candidates";


  create policy "Admin only"
  on "comms"."video_testimonial_candidates"
  as permissive
  for all
  to public
using (( SELECT public.admin_check() AS admin_check));



  create policy "Admin check"
  on "comms"."video_testimonials"
  as permissive
  for select
  to public
using (( SELECT public.admin_check() AS admin_check));




drop policy "Users can access own" on "comms"."video_testimonials";

drop policy "Admin check" on "comms"."video_testimonials";

CREATE INDEX idx_video_testimonials_created_at ON comms.video_testimonials USING btree (created_at DESC);

CREATE INDEX idx_video_testimonials_user_id ON comms.video_testimonials USING btree (user_id);


  create policy "Admin check"
  on "comms"."video_testimonials"
  as permissive
  for select
  to public
using ((( SELECT public.admin_check() AS admin_check) OR (user_id = ( SELECT public.get_user_id() AS get_user_id))));




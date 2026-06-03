alter table "schools"."leaderboard_requests" drop constraint "leaderboard_requests_user_id_fkey";

alter table "schools"."leaderboard_requests" add constraint "leaderboard_requests_user_id_fkey" FOREIGN KEY (user_id) REFERENCES public.users(id) ON UPDATE CASCADE ON DELETE CASCADE not valid;

alter table "schools"."leaderboard_requests" validate constraint "leaderboard_requests_user_id_fkey";



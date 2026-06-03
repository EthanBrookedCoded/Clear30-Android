create or replace view "views"."latest_user_events" as  SELECT events.user_id,
    max(events."timestamp") AS latest_activity
   FROM events
  GROUP BY events.user_id;


create or replace view "views"."inactive_users_31_days" as  WITH all_users AS (
         SELECT users.id,
            users.logging_id
           FROM users
        ), active_user_ids AS (
         SELECT DISTINCT latest_user_events.user_id
           FROM views.latest_user_events
          WHERE (latest_user_events.latest_activity >= (CURRENT_DATE - '31 days'::interval))
        )
 SELECT u.id AS user_id
   FROM all_users u
  WHERE ((NOT (u.id IN ( SELECT active_user_ids.user_id
           FROM active_user_ids))) AND (NOT (EXISTS ( SELECT 1
           FROM unnest(u.logging_id) logging_id(logging_id)
          WHERE (logging_id.logging_id IN ( SELECT active_user_ids.user_id
                   FROM active_user_ids))))));




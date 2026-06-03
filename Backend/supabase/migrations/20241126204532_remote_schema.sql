create or replace view "views"."groups_summary" as  SELECT g.id AS group_id,
    g.name AS group_name,
    ( SELECT json_agg(DISTINCT u.name) AS json_agg
           FROM (groups.group_members gm
             LEFT JOIN users u ON ((gm.user_id = u.id)))
          WHERE (gm.group_id = g.id)) AS members_in_group,
    json_agg(ga.activity) AS activities_in_group,
    max(ga."timestamp") AS most_recent_activity_timestamp
   FROM (groups.groups g
     LEFT JOIN groups.group_activity ga ON ((g.id = ga.group_id)))
  GROUP BY g.id, g.name
  ORDER BY g.id;




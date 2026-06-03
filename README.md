# Clear30

## 📁 Project Structure

This repo contains all Clear30 related code.

These are the important files and folders. There are some others (assets, etc.) but these are the main dev related ones.

```
App/
├── confidential.yml.example
└── [Clear30 XCode project]

Backend/
├── .env.example
├── HELP.sh
├── start
├── supabase/
├   ├── functions/
├   └── migrations/
├
├── backups/ (Databse backup CSV files)
└── admin-panel/ (Clear30 panel React App)

Other/
└── Website (Website assets and WebFlow script)
```

## 💻 Setup

1. Clone the Repo
```
git clone https://github.com/thatcherclough/Clear30.git
```

2. Setup app `confidential.yml`
```
cp App/confidential.yml.example App/confidential.yml

# Enter necessary keys (OpenAI, Supabase, Adjust, etc.)
```

3. Setup backend `.env`
```
cp Backend/.env.example Backend/.env

## Enter necessary keys (TWILIO, FIREBASE, etc.)
```


## 📱 App

The `App` folder contains the XCode project for the iOS app.

The `confidential.yml` file contains API keys and secrets that are obfucated on app build.


## 🌐 Backend

The backend folder contains all backend logic.

### Supabase

The `supabase` folder houses all SQL files to setup the Supabase instance and Edge Function Typescript logic.

Note: Supabase needs docker to run locally on your computer.

To start the supabase instance, in `Backend/` run:
```
sh start
```

This will pull the most recent database and setup the docker container.

`HELP.sh` contains common Supabase command as a reference.

#### Dealing with Views

You can create views in Supabase.

To allow access to views, you must

1. Set security invoker (this makes it so RLS applies to the view)

`CREATE VIEW _ WITH (security_invoker) AS ...`

2. Allow selecting on the view for the authenticated role.

`GRANT SELECT ON _ TO authenticated;`

#### Dealing with new schemas.

To allow access to a new schema, you have to run the following commands.

You must replace `myschema` with the actual schema name.

```
GRANT USAGE ON SCHEMA myschema TO anon, authenticated, service_role;
GRANT ALL ON ALL TABLES IN SCHEMA myschema TO anon, authenticated, service_role;
GRANT ALL ON ALL ROUTINES IN SCHEMA myschema TO anon, authenticated, service_role;
GRANT ALL ON ALL SEQUENCES IN SCHEMA myschema TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA myschema GRANT ALL ON TABLES TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA myschema GRANT ALL ON ROUTINES TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA myschema GRANT ALL ON SEQUENCES TO anon, authenticated, service_role;
```


You might even have to manually add the following to a migration file if doing it from CLI

```
grant usage on schema myschema to "anon";
grant usage on schema myschema to "authenticated";
grant usage on schema myschema to "service_role";
```

### Dealing with extensions

So Supabase allows you to use extensions, like `pg_cron` for cron jobs.

This essentially created a new schema for the extension. 

The issue is that Supabase CLI does not pull extension schemas.

So while you can enable the cron job schema, you can't pull the existing cron jobs from remote.

To backup remote cron jobs through, you can run the following query:

```
SELECT 
    'SELECT cron.schedule(''' || jobname || ''', ''' || schedule || ''', $$' || command || '$$);'
FROM cron.job;

```

### Admin Panel

The `admin-panel` folder contains a React app for the Clear30 panel (clear30.org/panel), a webpage where other employees can login and respond to user's text messages, Dr. Fred messages, and view other information. 

To serve the admin panel locally, run
```
# Install dependencies
npm install

# Serve locally
npm run dev
```

### SMS Broadcasts

In supabase, we have the ability to send SMS broadcasts to users. 

To do this, you can create an sms broadcast by using the following SQL query:

```
SELECT comms.create_sms_broadcast(
    'Your message here',
    ARRAY['user_id_1', 'user_id_2', ...]
);
```


This will add sms messages to the `comms.sms_messages` table.

To cancel a broadcast you can run the following query:

```
SELECT comms.cancel_sms_broadcast(broadcast_id);
```


## 🖱️ Other

The other folder has some app assets and other files. 

### Website
Among these is the `Website` folder.

Our website is created using WebFlow.
To host the site, we :

1. Push the site to a webflow.io domain.
2. Download the site using [Webflow Exporter](https://exflow.sktch.io/)
3. Copy files to the `.../webflow_export`
4. Run the build script `.../build.sh`
5. Copy the files in the `.../output` dir to host on AWS S3.

## Events

<details>

## App Logged events
| Group | Metric Name | Extra Data | Description |
|-------|-------------|------------|-------------|
| 🚀 Onboarding | opened_onboarding_screen | - | User opened the onboarding screen. |
| 🚀 Onboarding | signed_up | type: phone or email | User put phone number in on first slide and confirmed it. |
| 🚀 Onboarding | started_assessment_question | question | User started an assessment question. |
| 🚀 Onboarding | started_assessment_slide | - | User started an assessment slide. |
| 🚀 Onboarding | completed_assessment_question | question | User completed assessment question. |
| 🚀 Onboarding | completed_assessment_slide | - | User completed an assessment slide. |
| 🚀 Onboarding | completed_assessment | type: new_clear30/clear_30 | User completed the Clear30 assessment. (New Clear30 is from Life program) |
| 🚀 Onboarding | opened_feedback | type: onboarding/in-app | User viewed their assessment feedback. |
| 🚀 Onboarding | walkthrough | type: walkthrough event | User performed some action in the walkthrough. |
| 🚀 Onboarding | started_with_tracking | - | User began tracking in Life Program (non-Clear30 path). |
| 🚀 Onboarding | opened_paywall | - | User viewed the paywall screen. |
| 🚀 Onboarding | subscribed | type: yearly/monthly, pop_up: bool, free_code: string | User paid for a subscription (monthly or yearly). |
| 🚀 Onboarding | unsubscribed | - | User unsubscribed from the premium plan. |
| 🚀 Onboarding | started_free_mode | - | User chose not to subscribe but began Clear30 in free mode. |
| 📱 Tabs | opened_home | - | User opened the home tab. |
| 📱 Tabs | opened_content | - | User opened the content tab. |
| 📱 Tabs | opened_profile | - | User opened the profile tab. |
| 📱 Tabs | opened_community_tab | - | User opened the community tab. |
| 📍 Specific Content | focused_message_cover | message_title, home_preview: bool? | The user scrolled to the message cover. |
| 📍 Specific Content | opened_message | message_title, home_preview: bool? | The user opened the daily message within the app |
| 📍 Specific Content | listened_to_meditation | meditation_name, home_preview: bool? | The user clicked play on a meditation. |
| 📍 Specific Content | opened_reddit_thread | url, home_preview: bool? | The user clicked on and opened a Reddit thread |
| 📍 Specific Content | opened_youtube_video | url, home_preview: bool? | The user watched a YouTube video |
| 📍 Specific Content | opened_resource | url | The user opened a resource card (A URL) |
| 📍 Specific Content | opened_page_info | - | User opened page information. |
| 📍 Specific Content | used_claire | user_message, claire_message | The user sent a message to Claire (AI Bot) |
| 📍 Specific Content | opened_claire | from | The user opened Claire (AI Bot) |
| 📍 Specific Content | claire_opened_voice | - | The user opened Claire voice mode |
| 📍 Specific Content | claire_closed_voice | - | The user closed Claire voice mode |
| 📚 All Content | opened_craving_resources | - | Opened the craving resources section. |
| 📚 All Content | opened_daily_messages | - | User opened daily messages. |
| 📚 All Content | opened_all_meditations | - | User clicked meditations button. |
| 📚 All Content | opened_all_reddits | - | User accessed the Reddit feed. |
| 📚 All Content | opened_all_youtubes | - | User clicked the videos button. |
| 📚 All Content | opened_all_resources | home_preview: bool? | User viewied a days resources from the home tab. |
| 📚 All Content | opened_all_prompts | home_preview: bool? | User opened all prompts. |
| 📚 All Content | opened_all_page_info | - | User accessed all page information. |
| 📚 All Content | opened_community | - | The user accessed the community section of the app |
| 📚 All Content | searched | search_text | Clicked Search Bar |
| 📊 Tracking | opened_app | - | User opened the app. |
| 📊 Tracking | ended_session | - | User closed the app. |
| 📊 Tracking | checked_in | sober: bool, type: today/yesterday, check_in_method?, check_in_amount?, check_in_completion?, custom_check_in_id? | User checked in. |
| 📊 Tracking | opened_custom_check_in | - | User opened the custom check in card. |
| 📊 Tracking | added_custom_check_in | type: string | User added a custom check in. |
| 📊 Tracking | removed_custom_check_in | type: string | User removed a custom check in. |
| 📊 Tracking | edited_last_smoked_timer | - | User edited the last smoked timer. |
| 📊 Tracking | toggled_calendar | type: month/week | User toggled the calendar. |
| 📊 Tracking | navigated_to_past_day | date | User navigated to a past day on the calendar. |
| 📊 Tracking | navigated_to_today | - | User navigated to today on the calendar. |
| 📊 Tracking | opened_start_fresh_sheet | day_number | User opened the sheet to restart their break from day 1 in the welcome back popup. |
| 📊 Tracking | started_fresh_break | day_number | User confirmed restarting their break from day 1 in the welcome back popup. |
| 📊 Tracking | continued_break | day_number | User chose to continue their break from where they left off in the welcome back popup. |
| 📊 Tracking | opened_check_in_screen | day_number | User opened the check-in screen. |
| 📊 Tracking | opened_check_in_reward_screen | day_number | User opened the check-in reward screen. |
| 📊 Tracking | opened_check_in_topic_screen | day_number, title | User opened the check-in topic screen. |
| 📊 Tracking | skipped_check_in | day_number, from | User skipped the check-in process. |
| 📊 Tracking | completed_check_in | day_number | User completed the check-in process. |
| 📊 Tracking | skipped_reward | day_number, from | User went back from reward screen. |
| 📊 Tracking | completed_reward | day_number | User completed the reward screen. |
| 📊 Tracking | skipped_topic | day_number, title, from | User skipped or went back from topic screen. |
| 📊 Tracking | completed_topic | day_number, title | User completed the topic. |
| 📊 Tracking | saw_static_reward | day_number, reward_type | User saw a static reward. |
| 📊 Tracking | saw_variable_reward | day_number, reward_type | User saw a variable reward. |
| 🔔 Notifications | enabled_content_notifications | - | User enabled content notifications. |
| 🔔 Notifications | disabled_content_notifications | - | User disabled content notifications. |
| 🔔 Notifications | enabled_check_in_notifications | - | User enabled check in notifications. |
| 🔔 Notifications | disabled_check_in_notifications | - | User disabled check in notifications. |
| 🔔 Notifications | enabled_popin_notifications | - | User enabled pop in (progress) notifications. |
| 🔔 Notifications | enabled_group_notifications | - | User enabled group notifications. |
| 🔔 Notifications | disabled_group_notifications | - | User disabled group notifications. |
| 🔔 Notifications | disabled_popin_notifications | - | User disabled pop in (progress) notifications. |
| 🔔 Notifications | opened_notification | title, type | User opened the app from a notification. |
| 🔔 Notifications | enabled_community_notifications | - | User enabled community notifications. |
| 🔔 Notifications | disabled_community_notifications | - | User disabled community notifications. |
| 📱 SMS | enabled_accountability_messages | - | The user opted in to receive accountability-related messages for support and progress tracking. |
| 📱 SMS | disabled_accountability_messages | - | The user opted out of receiving accountability-related messages. |
| 📱 SMS | enabled_milestone_messages | - | Enabled milestone (day sms) messages. |
| 📱 SMS | scheduled_milestone_messages | - | Scheduled milestone (day sms) messages. |
| 📱 SMS | rescheduled_milestone_messages | - | Rescheduled milestone (day sms) messages. |
| 📱 SMS | disabled_milestone_messages | - | Disabled milestone (day sms) messages. |
| 📱 SMS | sent_message_to_self | - | User sent a message to themselves through the app. |
| 📱 SMS | opened_from_link | title, type | User opened the app from a clear30:// link. |
| 📔 Journal | opened_journal | - | User opened the journal from the profile tab. |
| 📔 Journal | created_journal_entry | type: video/text, prompt | The user clicked NEW VIDEO or NEW TEXT journal |
| 📔 Journal | saved_journal_entry | type: video/text | User saved a journal entry after creating one. |
| 📔 Journal | opened_journal_entry | type: video/text, date, home_preview: bool | User opened a previous journal entry. |
| 👨‍⚕️ Dr Fred | opened_talk_to_dr_fred | - | User accessed the Talk to Dr. Fred section. |
| 👨‍⚕️ Dr Fred | talked_to_dr_fred | user_message: string?, fred_message: string? | User sent or receives a Dr. Fred message. |
| 🔄 Shares | shared_app | from: "Day 14 message"/"App intro" | User shared the app. |
| 🔄 Shares | shared_timer | - | User shared the timer. |
| 🔄 Shares | shared_calendar | - | User shared a calendar entry. |
| 🔄 Shares | shared_journal_entry | type: video/text | User shared a journal entry. |
| 🤒 Symptoms | opened_symptom_card | symptom_name | The user clicked on symptom card button |
| 🤒 Symptoms | updated_symptoms | symptoms_list | The user updated their symptoms in the app, tracking changes in their experience. |
| 🤒 Symptoms | opened_symptom_messages | - | User opened the symptom messages sign up card. |
| 🤒 Symptoms | enabled_symptom_message | frequency: int (1-3) | The user opted in to receive messages |
| 🤒 Symptoms | disabled_symptom_message | - | The user opted out of receiving messages addressing their symptoms. |
| 👥 Groups | created_group | - | User created a new group. |
| 👥 Groups | joined_group | - | User joined a group. |
| 👥 Groups | opened_groups | - | User opened groups screen. |
| 👥 Groups | shared_group_link | - | User shared a group invite link. |
| 👥 Groups | left_group | - | User left a group. |
| 👥 Groups | opened_share_group_link | - | User opened the share group link sheet. |
| 👥 Groups | opened_group_members | - | User opened group members section. |
| 👥 Groups | opened_group_chat | - | User opened group chat section. |
| 👥 Groups | opened_group_notes | - | User opened group notes section. |
| 👥 Groups | opened_group_settings | - | User opened group settings section. |
| 👥 Groups | sent_group_note | - | User sent a note to a group member. |
| 👥 Groups | sent_group_chat_message | - | User sent a message in group chat. |
| 👥 Groups | sent_group_ping | - | User sent a ping to remind a group member to check in. |
| 👥 Community | opened_community_prompt | - | User opened a community prompt. |
| 👥 Community | focused_community_post | - | User scrolled to or focused on a community post. |
| 👥 Community | opened_community_post | - | User opened a community post to view in detail. |
| 👥 Community | opened_create_community_post | - | User opened the interface to create a new community post. |
| 👥 Community | created_community_post | - | User successfully created a new post in the community. |
| 👥 Community | opened_edit_community_post | - | User opened the interface to edit an existing community post. |
| 👥 Community | edited_community_post | - | User submitted edits to an existing community post. |
| 👥 Community | reacted_to_community_post | - | User reacted to a community post (liked, etc.). |
| 👥 Community | reported_community_post | - | User reported a community post for moderation. |
| 👥 Community | deleted_community_post | - | User deleted their own community post. |
| 👥 Community | opened_community_activity | - | User opened the community activity feed. |
| 👥 Community | opened_community_notifications | - | User opened community notifications. |
| 👥 Community | opened_community_my_posts | - | User opened the view of their own community posts. |
| 👥 Community | edited_community_feed_filters | - | User adjusted filters for the community feed. |
| 👥 Community | added_community_comment | - | User added a comment to a community post. |
|| 🏥 Health | opened_health_timeline | health_category, was_animated | User opened a health category timeline (Heart, Brain, Sleep, etc.). |
|| 🏆 Achievements | opened_achievement_list | total_achievements | User opened the full achievements list. |
|| 🏆 Achievements | opened_achievement_detail | achievement_key, achievement_name, rarity, is_first_view, source | User opened achievement detail popup from list or mini display. |
| 📅 Events | event_entered | - | User entered an event. |
| 📅 Events | event_left | - | User left an event. |
| 📅 Events | event_declined | - | User declined an event invitation. |
| 📅 Events | event_accepted | - | User accepted an event invitation. |
| 📅 Events | event_opened_lobby | - | User opened an event lobby. |
| 📅 Events | event_opened_popup | - | User opened an event popup notification. |
| 🎯 Post Clear30 | opened_gbw_card | - | User opened the post-Clear30 'GBW'/life card. |
| ⚙️ Other | started_demo | type | User started demo mode of app (nys, counselor, generic). |
| ⚙️ Other | adjust_tracking_token | token | The user's adjust token. |
| ⚙️ Other | opened_clear30_widgets | - | User opened Clear30 widgets. |
| ⚙️ Other | opened_submit_feedback | - | User opened the submit feedback sheet. |
| ⚙️ Other | clicked_review_button | - | User clicked the review button in settings. |
| ⚙️ Other | signed_out | - | User signed out. |
| ⚙️ Other | deleted_account | - | User deleted account. |
| ⚙️ Other | $exposure | - | System tracked user exposure to a feature or content. |
| ⚙️ Other | opened_settings | - | User opened the settings sheet. |
| ⚙️ Other | opened_toggle_settings | type | User opened toggle settings interface. |

</details>

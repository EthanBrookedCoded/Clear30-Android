-- Expand event_type check to include booking sheet events.
alter table comms.coach_referral_events
    drop constraint coach_referral_events_event_type_check;

alter table comms.coach_referral_events
    add constraint coach_referral_events_event_type_check
    check (event_type in ('shown', 'cta_tapped', 'skipped', 'sheet_shown', 'book_tapped', 'sheet_dismissed'));

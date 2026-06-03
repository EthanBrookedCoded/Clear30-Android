create table "library"."sms_schedule" (
    "id" text not null,
    "message" text not null
);


alter table "library"."sms_schedule" enable row level security;

CREATE UNIQUE INDEX sms_schedule_pkey ON library.sms_schedule USING btree (id);

alter table "library"."sms_schedule" add constraint "sms_schedule_pkey" PRIMARY KEY using index "sms_schedule_pkey";



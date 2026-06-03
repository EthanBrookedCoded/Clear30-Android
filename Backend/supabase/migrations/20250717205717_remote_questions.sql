create sequence "programs"."remote_assessment_questions_id_seq";

create table "programs"."remote_assessment_questions" (
    "id" integer not null default nextval('programs.remote_assessment_questions_id_seq'::regclass),
    "after_question_id" text not null,
    "stripped_prompt" text not null,
    "prompt1" text not null default ''::text,
    "prompt2" text not null,
    "options" jsonb not null,
    "displayed_options" jsonb,
    "image_names" jsonb,
    "badges" jsonb,
    "subtexts" jsonb,
    "min" integer not null default 1,
    "max" integer not null default 1,
    "enabled" boolean not null default true,
    "created_at" timestamp with time zone not null default now()
);


alter table "programs"."remote_assessment_questions" enable row level security;

alter sequence "programs"."remote_assessment_questions_id_seq" owned by "programs"."remote_assessment_questions"."id";

CREATE INDEX idx_remote_assessment_questions_after_question_id ON programs.remote_assessment_questions USING btree (after_question_id);

CREATE INDEX idx_remote_assessment_questions_enabled ON programs.remote_assessment_questions USING btree (enabled);

CREATE INDEX idx_remote_assessment_questions_stripped_prompt ON programs.remote_assessment_questions USING btree (stripped_prompt);

CREATE UNIQUE INDEX remote_assessment_questions_pkey ON programs.remote_assessment_questions USING btree (id);

CREATE UNIQUE INDEX remote_assessment_questions_stripped_prompt_key ON programs.remote_assessment_questions USING btree (stripped_prompt);

alter table "programs"."remote_assessment_questions" add constraint "remote_assessment_questions_pkey" PRIMARY KEY using index "remote_assessment_questions_pkey";

alter table "programs"."remote_assessment_questions" add constraint "remote_assessment_questions_stripped_prompt_key" UNIQUE using index "remote_assessment_questions_stripped_prompt_key";

grant delete on table "programs"."program_assessment_responses" to "anon";

grant insert on table "programs"."program_assessment_responses" to "anon";

grant references on table "programs"."program_assessment_responses" to "anon";

grant select on table "programs"."program_assessment_responses" to "anon";

grant trigger on table "programs"."program_assessment_responses" to "anon";

grant truncate on table "programs"."program_assessment_responses" to "anon";

grant update on table "programs"."program_assessment_responses" to "anon";

grant delete on table "programs"."program_assessment_responses" to "authenticated";

grant insert on table "programs"."program_assessment_responses" to "authenticated";

grant references on table "programs"."program_assessment_responses" to "authenticated";

grant trigger on table "programs"."program_assessment_responses" to "authenticated";

grant truncate on table "programs"."program_assessment_responses" to "authenticated";

grant update on table "programs"."program_assessment_responses" to "authenticated";

grant delete on table "programs"."program_assessments" to "anon";

grant insert on table "programs"."program_assessments" to "anon";

grant references on table "programs"."program_assessments" to "anon";

grant select on table "programs"."program_assessments" to "anon";

grant trigger on table "programs"."program_assessments" to "anon";

grant truncate on table "programs"."program_assessments" to "anon";

grant update on table "programs"."program_assessments" to "anon";

grant delete on table "programs"."program_assessments" to "authenticated";

grant insert on table "programs"."program_assessments" to "authenticated";

grant references on table "programs"."program_assessments" to "authenticated";

grant select on table "programs"."program_assessments" to "authenticated";

grant trigger on table "programs"."program_assessments" to "authenticated";

grant truncate on table "programs"."program_assessments" to "authenticated";

grant update on table "programs"."program_assessments" to "authenticated";

grant delete on table "programs"."program_feedback" to "anon";

grant insert on table "programs"."program_feedback" to "anon";

grant references on table "programs"."program_feedback" to "anon";

grant select on table "programs"."program_feedback" to "anon";

grant trigger on table "programs"."program_feedback" to "anon";

grant truncate on table "programs"."program_feedback" to "anon";

grant update on table "programs"."program_feedback" to "anon";

grant delete on table "programs"."program_feedback" to "authenticated";

grant insert on table "programs"."program_feedback" to "authenticated";

grant references on table "programs"."program_feedback" to "authenticated";

grant select on table "programs"."program_feedback" to "authenticated";

grant trigger on table "programs"."program_feedback" to "authenticated";

grant truncate on table "programs"."program_feedback" to "authenticated";

grant update on table "programs"."program_feedback" to "authenticated";

grant delete on table "programs"."program_guides" to "anon";

grant insert on table "programs"."program_guides" to "anon";

grant references on table "programs"."program_guides" to "anon";

grant select on table "programs"."program_guides" to "anon";

grant trigger on table "programs"."program_guides" to "anon";

grant truncate on table "programs"."program_guides" to "anon";

grant update on table "programs"."program_guides" to "anon";

grant delete on table "programs"."program_guides" to "authenticated";

grant insert on table "programs"."program_guides" to "authenticated";

grant references on table "programs"."program_guides" to "authenticated";

grant select on table "programs"."program_guides" to "authenticated";

grant trigger on table "programs"."program_guides" to "authenticated";

grant truncate on table "programs"."program_guides" to "authenticated";

grant update on table "programs"."program_guides" to "authenticated";

grant delete on table "programs"."program_guides_qa" to "anon";

grant insert on table "programs"."program_guides_qa" to "anon";

grant references on table "programs"."program_guides_qa" to "anon";

grant select on table "programs"."program_guides_qa" to "anon";

grant trigger on table "programs"."program_guides_qa" to "anon";

grant truncate on table "programs"."program_guides_qa" to "anon";

grant update on table "programs"."program_guides_qa" to "anon";

grant delete on table "programs"."program_guides_qa" to "authenticated";

grant insert on table "programs"."program_guides_qa" to "authenticated";

grant references on table "programs"."program_guides_qa" to "authenticated";

grant select on table "programs"."program_guides_qa" to "authenticated";

grant trigger on table "programs"."program_guides_qa" to "authenticated";

grant truncate on table "programs"."program_guides_qa" to "authenticated";

grant update on table "programs"."program_guides_qa" to "authenticated";

grant delete on table "programs"."program_messages" to "anon";

grant insert on table "programs"."program_messages" to "anon";

grant references on table "programs"."program_messages" to "anon";

grant select on table "programs"."program_messages" to "anon";

grant trigger on table "programs"."program_messages" to "anon";

grant truncate on table "programs"."program_messages" to "anon";

grant update on table "programs"."program_messages" to "anon";

grant delete on table "programs"."program_messages" to "authenticated";

grant insert on table "programs"."program_messages" to "authenticated";

grant references on table "programs"."program_messages" to "authenticated";

grant select on table "programs"."program_messages" to "authenticated";

grant trigger on table "programs"."program_messages" to "authenticated";

grant truncate on table "programs"."program_messages" to "authenticated";

grant update on table "programs"."program_messages" to "authenticated";

grant delete on table "programs"."program_messages_qa" to "anon";

grant insert on table "programs"."program_messages_qa" to "anon";

grant references on table "programs"."program_messages_qa" to "anon";

grant select on table "programs"."program_messages_qa" to "anon";

grant trigger on table "programs"."program_messages_qa" to "anon";

grant truncate on table "programs"."program_messages_qa" to "anon";

grant update on table "programs"."program_messages_qa" to "anon";

grant delete on table "programs"."program_messages_qa" to "authenticated";

grant insert on table "programs"."program_messages_qa" to "authenticated";

grant references on table "programs"."program_messages_qa" to "authenticated";

grant select on table "programs"."program_messages_qa" to "authenticated";

grant trigger on table "programs"."program_messages_qa" to "authenticated";

grant truncate on table "programs"."program_messages_qa" to "authenticated";

grant update on table "programs"."program_messages_qa" to "authenticated";

grant delete on table "programs"."program_stages" to "anon";

grant insert on table "programs"."program_stages" to "anon";

grant references on table "programs"."program_stages" to "anon";

grant select on table "programs"."program_stages" to "anon";

grant trigger on table "programs"."program_stages" to "anon";

grant truncate on table "programs"."program_stages" to "anon";

grant update on table "programs"."program_stages" to "anon";

grant delete on table "programs"."program_stages" to "authenticated";

grant insert on table "programs"."program_stages" to "authenticated";

grant references on table "programs"."program_stages" to "authenticated";

grant select on table "programs"."program_stages" to "authenticated";

grant trigger on table "programs"."program_stages" to "authenticated";

grant truncate on table "programs"."program_stages" to "authenticated";

grant update on table "programs"."program_stages" to "authenticated";

grant delete on table "programs"."programs" to "anon";

grant insert on table "programs"."programs" to "anon";

grant references on table "programs"."programs" to "anon";

grant select on table "programs"."programs" to "anon";

grant trigger on table "programs"."programs" to "anon";

grant truncate on table "programs"."programs" to "anon";

grant update on table "programs"."programs" to "anon";

grant delete on table "programs"."programs" to "authenticated";

grant insert on table "programs"."programs" to "authenticated";

grant references on table "programs"."programs" to "authenticated";

grant select on table "programs"."programs" to "authenticated";

grant trigger on table "programs"."programs" to "authenticated";

grant truncate on table "programs"."programs" to "authenticated";

grant update on table "programs"."programs" to "authenticated";

grant delete on table "programs"."remote_assessment_questions" to "anon";

grant insert on table "programs"."remote_assessment_questions" to "anon";

grant references on table "programs"."remote_assessment_questions" to "anon";

grant select on table "programs"."remote_assessment_questions" to "anon";

grant trigger on table "programs"."remote_assessment_questions" to "anon";

grant truncate on table "programs"."remote_assessment_questions" to "anon";

grant update on table "programs"."remote_assessment_questions" to "anon";

grant delete on table "programs"."remote_assessment_questions" to "authenticated";

grant insert on table "programs"."remote_assessment_questions" to "authenticated";

grant references on table "programs"."remote_assessment_questions" to "authenticated";

grant select on table "programs"."remote_assessment_questions" to "authenticated";

grant trigger on table "programs"."remote_assessment_questions" to "authenticated";

grant truncate on table "programs"."remote_assessment_questions" to "authenticated";

grant update on table "programs"."remote_assessment_questions" to "authenticated";

grant delete on table "programs"."remote_assessment_questions" to "service_role";

grant insert on table "programs"."remote_assessment_questions" to "service_role";

grant references on table "programs"."remote_assessment_questions" to "service_role";

grant select on table "programs"."remote_assessment_questions" to "service_role";

grant trigger on table "programs"."remote_assessment_questions" to "service_role";

grant truncate on table "programs"."remote_assessment_questions" to "service_role";

grant update on table "programs"."remote_assessment_questions" to "service_role";

create policy "Select to all"
on "programs"."remote_assessment_questions"
as permissive
for select
to public
using (true);




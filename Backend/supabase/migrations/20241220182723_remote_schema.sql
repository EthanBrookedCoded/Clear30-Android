alter table "programs"."program_guides" add column "related_guide_1" text not null;

alter table "programs"."program_guides" add column "related_guide_2" text;

alter table "programs"."program_guides" add constraint "program_guides_related_guide_1_fkey" FOREIGN KEY (related_guide_1) REFERENCES programs.program_guides(id) ON UPDATE CASCADE ON DELETE SET NULL not valid;

alter table "programs"."program_guides" validate constraint "program_guides_related_guide_1_fkey";

alter table "programs"."program_guides" add constraint "program_guides_related_guide_2_fkey" FOREIGN KEY (related_guide_2) REFERENCES programs.program_guides(id) ON UPDATE CASCADE ON DELETE SET NULL not valid;

alter table "programs"."program_guides" validate constraint "program_guides_related_guide_2_fkey";



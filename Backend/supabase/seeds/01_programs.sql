-- Seed: programs.programs
-- Source: 2025_03_17_programs.csv (fixed from original which had clear30 commented out)

INSERT INTO "programs"."programs" ("id", "descriptions", "updated_at", "start_soon")
VALUES
    ('clear30', 'The Clear30 program.', '2025-02-23 18:52:03+00', 'clear30-start-soon'),
    ('clear30-start-soon', 'The Clear30 (start soon) program.', '2025-03-17 18:46:49.002468+00', NULL),
    ('life', 'The Life program.', '2024-12-17 22:23:10+00', NULL)
ON CONFLICT (id) DO NOTHING;

INSERT INTO
    "programs"."programs" ("id", "descriptions", "updated_at", "start_soon")
VALUES
    (
        'clear30',
        'The Clear30 program.',
        '2024-11-26 19:20:22.40297+00',
        'clear30-start-soon'
    ),
    (
        'clear30-start-soon',
        'The start soon program for Clear30.',
        '2025-03-10 16:54:47.654838+00',
        null
    )
ON CONFLICT (id) DO NOTHING;

INSERT INTO
    "programs"."program_stages" (
        "stage",
        "title",
        "subtitle",
        "body",
        "color1",
        "color2",
        "fred_experience"
    )
VALUES
    (
        'preparation',
        'Preparation',
        'Gearing Up',
        'Welcome to the Preparation Stage!

This stage is about getting ready and excited for your break.',
        'a32eb8',
        'b93fcf',
        null
    );

INSERT INTO
    "programs"."program_messages" (
        "id",
        "day",
        "title",
        "subtitle",
        "body",
        "program",
        "question_id",
        "question_response",
        "claire_prompts",
        "journal_prompts",
        "meditation",
        "page_info",
        "resources",
        "stage",
        "guide_id",
        "notification_body",
        "notification_title"
    )
VALUES
    (
        '2',
        '1',
        'Day before break!',
        'this is the day before your break!',
        'woooo',
        'clear30-start-soon',
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        'preparation',
        null,
        null,
        null
    ),
    (
        '3',
        '2',
        '2 days until break',
        'wooo',
        'you got 2 days!',
        'clear30-start-soon',
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        'preparation',
        null,
        null,
        null
    ),
    (
        '4',
        '3',
        '3 days until your break',
        'This is cool',
        '3 days way to go!',
        'clear30-start-soon',
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        'preparation',
        null,
        null,
        null
    ),
    (
        '5',
        '4',
        '4 Days until break !',
        'Yeahyijjj',
        '4 Days until break !',
        'clear30-start-soon',
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        'preparation',
        null,
        null,
        null
    ),
    (
        '6',
        '0',
        'Should be first start soon',
        'FIrst start sooon',
        'AHHH',
        'clear30-start-soon',
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        'preparation',
        null,
        null,
        null
    );




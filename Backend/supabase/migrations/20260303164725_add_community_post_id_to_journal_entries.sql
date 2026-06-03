ALTER TABLE journal.journal_entries
  ADD COLUMN community_post_id UUID
    REFERENCES community.posts(id) ON DELETE SET NULL;
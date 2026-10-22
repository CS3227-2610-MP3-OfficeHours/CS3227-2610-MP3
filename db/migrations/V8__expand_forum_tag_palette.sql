-- Expand the permitted custom forum tag colors without changing existing tags.
ALTER TABLE course_forum_tags
  DROP CONSTRAINT course_forum_tags_color_check;
ALTER TABLE course_forum_tags
  ADD CONSTRAINT course_forum_tags_color_check
  CHECK (color IN (
    '#475569', '#1D4ED8', '#166534', '#854D0E', '#B91C1C',
    '#6D28D9', '#0F766E', '#0369A1', '#A21CAF', '#1F2937'
  ));

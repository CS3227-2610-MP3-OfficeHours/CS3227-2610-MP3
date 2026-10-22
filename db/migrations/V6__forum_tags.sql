-- Durable course-scoped forum tags and required post association.
CREATE TABLE course_forum_tags (
  id UUID PRIMARY KEY,
  course_id UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
  name VARCHAR(20) NOT NULL,
  normalized_name TEXT NOT NULL,
  color VARCHAR(7) NOT NULL,
  is_default BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (course_id, id),
  UNIQUE (course_id, normalized_name),
  CHECK (color IN ('#475569', '#1D4ED8', '#166534', '#854D0E', '#B91C1C', '#6D28D9', '#0F766E')),
  CHECK (NOT is_default OR (name = 'General' AND color = '#475569'))
);
CREATE UNIQUE INDEX course_forum_one_general_idx ON course_forum_tags(course_id) WHERE is_default;
INSERT INTO course_forum_tags(id, course_id, name, normalized_name, color, is_default, created_at)
SELECT gen_random_uuid(), id, 'General', 'general', '#475569', TRUE, CURRENT_TIMESTAMP FROM courses;
ALTER TABLE posts ADD COLUMN tag_id UUID;
UPDATE posts p SET tag_id = t.id FROM course_forum_tags t WHERE t.course_id = p.course_id AND t.is_default;
ALTER TABLE posts ALTER COLUMN tag_id SET NOT NULL;
ALTER TABLE posts ADD CONSTRAINT posts_course_tag_fk FOREIGN KEY (course_id, tag_id) REFERENCES course_forum_tags(course_id, id);
CREATE INDEX posts_course_tag_created_idx ON posts(course_id, tag_id, created_at DESC);
-- Serialize concurrent inserts per course and enforce the seven-tag cap in the database.
CREATE FUNCTION enforce_course_forum_tag_limit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE tag_count INTEGER;
BEGIN
  PERFORM 1 FROM courses WHERE id = NEW.course_id FOR UPDATE;
  SELECT COUNT(*) INTO tag_count FROM course_forum_tags WHERE course_id = NEW.course_id;
  IF tag_count >= 7 THEN RAISE EXCEPTION 'course forum tag limit reached' USING ERRCODE = '23514'; END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER course_forum_tag_limit BEFORE INSERT ON course_forum_tags
FOR EACH ROW EXECUTE FUNCTION enforce_course_forum_tag_limit();

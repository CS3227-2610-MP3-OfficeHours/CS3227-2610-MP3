CREATE TABLE course_materials (
  id UUID PRIMARY KEY,
  course_id UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
  uploader_id UUID NOT NULL REFERENCES users(id),
  filename VARCHAR(255) NOT NULL,
  status VARCHAR(16) NOT NULL CHECK (status IN ('processing', 'active', 'failed', 'deleted')),
  failure_message VARCHAR(500),
  content BYTEA NOT NULL,
  page_count INTEGER NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX course_materials_course_status_idx ON course_materials(course_id, status, created_at DESC);

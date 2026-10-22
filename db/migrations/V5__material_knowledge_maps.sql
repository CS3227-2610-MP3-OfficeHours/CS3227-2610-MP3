-- Development-stage replacement for the obsolete course-wide map representation.
-- The following drop deliberately discards disposable legacy course Knowledge Map test data; no preservation is required.
DROP TABLE IF EXISTS course_knowledge_maps;

CREATE TABLE material_knowledge_maps (
  id UUID PRIMARY KEY,
  course_id UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
  material_id UUID NOT NULL REFERENCES course_materials(id) ON DELETE CASCADE,
  material_digest VARCHAR(128) NOT NULL,
  requested_by UUID NOT NULL REFERENCES users(id),
  slot VARCHAR(16) NOT NULL CHECK (slot IN ('candidate', 'approved')),
  content TEXT NOT NULL,
  verification_message VARCHAR(1000),
  reviewed_by UUID REFERENCES users(id),
  reviewed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  UNIQUE (material_id, slot)
);

CREATE TABLE material_knowledge_map_jobs (
  id UUID PRIMARY KEY,
  course_id UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
  material_id UUID NOT NULL UNIQUE REFERENCES course_materials(id) ON DELETE CASCADE,
  material_digest VARCHAR(128) NOT NULL,
  requested_by UUID NOT NULL REFERENCES users(id),
  status VARCHAR(40) NOT NULL CHECK (status IN ('queued', 'generating', 'completed', 'failed', 'cancelled_due_to_source_change')),
  failure_message VARCHAR(500),
  output_map_id UUID REFERENCES material_knowledge_maps(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE course_knowledge_maps (
  id UUID PRIMARY KEY,
  course_id UUID NOT NULL UNIQUE REFERENCES courses(id) ON DELETE CASCADE,
  requested_by UUID NOT NULL REFERENCES users(id),
  status VARCHAR(20) NOT NULL CHECK (status IN ('generating', 'pending_review', 'approved', 'rejected', 'stale', 'failed')),
  content TEXT,
  failure_message VARCHAR(500),
  source_material_ids UUID[] NOT NULL,
  reviewed_by UUID REFERENCES users(id),
  reviewed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

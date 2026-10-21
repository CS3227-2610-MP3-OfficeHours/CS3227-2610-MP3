CREATE TABLE course_material_pdf_pages (
  material_id UUID NOT NULL REFERENCES course_materials(id) ON DELETE CASCADE,
  course_id UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
  material_digest VARCHAR(64) NOT NULL,
  page_number INTEGER NOT NULL CHECK (page_number > 0),
  text TEXT NOT NULL,
  text_digest VARCHAR(64) NOT NULL,
  parser_version VARCHAR(80) NOT NULL,
  state VARCHAR(32) NOT NULL,
  safe_failure_code VARCHAR(80),
  created_at TIMESTAMPTZ NOT NULL,
  invalidated_at TIMESTAMPTZ,
  PRIMARY KEY (material_id, page_number)
);

CREATE INDEX course_material_pdf_pages_course_material_idx
  ON course_material_pdf_pages(course_id, material_id);

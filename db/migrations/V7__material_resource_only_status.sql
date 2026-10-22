ALTER TABLE course_materials DROP CONSTRAINT course_materials_status_check;
ALTER TABLE course_materials ADD CONSTRAINT course_materials_status_check
  CHECK (status IN ('processing', 'active', 'resource_only', 'failed', 'deleted'));

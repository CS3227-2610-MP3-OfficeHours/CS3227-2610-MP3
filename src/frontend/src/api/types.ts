export type Role = "student" | "staff";
export type User = {
  id: string;
  username: string;
  role: Role;
};
export type Course = {
  id: string;
  code: string;
  name: string;
};
export type Member = {
  user: User;
  membershipRole: Role;
};
export type ForumTag = {
  id: string;
  courseId: string;
  name: string;
  color: string;
  isDefault: boolean;
};
export type Post = {
  id: string;
  courseId: string;
  tag: ForumTag;
  title: string;
  description: string;
  renderedDescription: string;
  author: User;
  createdAt: string;
  updatedAt: string;
};
export type Comment = {
  id: string;
  postId: string;
  body: string;
  renderedBody: string;
  author: User;
  createdAt: string;
  updatedAt: string;
};
export type Resource = {
  id: string;
  filename: string;
  downloadPath: string;
};
export type Material = {
  id: string;
  filename: string;
  status: string;
  pageCount: number;
  failureMessage?: string;
};
export type KnowledgeMap = {
  id: string;
  materialId: string;
  status: string;
  content: string;
  verificationMessage?: string | null;
  reviewedBy?: string | null;
  reviewedAt?: string | null;
  createdAt: string;
  updatedAt: string;
};
export type KnowledgeMapJob = {
  id: string;
  materialId: string;
  status: string;
  failureMessage?: string | null;
  createdAt: string;
  updatedAt: string;
};
export type MaterialMapState = {
  material: Material;
  candidate: KnowledgeMap | null;
  approved: KnowledgeMap | null;
  job: KnowledgeMapJob | null;
};
export type Tab = "forum" | "resources" | "materials" | "members" | "settings";

type ApiRecord = Record<string, unknown>;

function invalidResponse(): never {
  throw new Error("The server returned an invalid response. Please try again.");
}

function isRecord(value: unknown): value is ApiRecord {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function isString(value: unknown): value is string {
  return typeof value === "string";
}

function isBoolean(value: unknown): value is boolean {
  return typeof value === "boolean";
}

function optionalString(value: unknown): boolean {
  return value === undefined || value === null || isString(value);
}

function isUser(value: unknown): value is ApiRecord {
  return (
    isRecord(value) &&
    isString(value.id) &&
    isString(value.username) &&
    (value.role === "student" || value.role === "staff")
  );
}

function isCourse(value: unknown): value is ApiRecord {
  return (
    isRecord(value) &&
    isString(value.id) &&
    isString(value.code) &&
    isString(value.name)
  );
}

function isForumTag(value: unknown): value is ApiRecord {
  return (
    isRecord(value) &&
    isString(value.id) &&
    isString(value.courseId) &&
    isString(value.name) &&
    isString(value.color) &&
    isBoolean(value.isDefault)
  );
}

function isPost(value: unknown): value is ApiRecord {
  return (
    isRecord(value) &&
    isString(value.id) &&
    isString(value.courseId) &&
    isForumTag(value.tag) &&
    isString(value.title) &&
    isString(value.description) &&
    isString(value.renderedDescription) &&
    isUser(value.author) &&
    isString(value.createdAt) &&
    isString(value.updatedAt)
  );
}

function isComment(value: unknown): value is ApiRecord {
  return (
    isRecord(value) &&
    isString(value.id) &&
    isString(value.postId) &&
    isString(value.body) &&
    isString(value.renderedBody) &&
    isUser(value.author) &&
    isString(value.createdAt) &&
    isString(value.updatedAt)
  );
}

function isMember(value: unknown): value is ApiRecord {
  return (
    isRecord(value) &&
    isUser(value.user) &&
    (value.membershipRole === "student" || value.membershipRole === "staff")
  );
}

function isResource(value: unknown): value is ApiRecord {
  return (
    isRecord(value) &&
    isString(value.id) &&
    isString(value.filename) &&
    isString(value.downloadPath)
  );
}

function isMaterial(value: unknown): value is ApiRecord {
  return (
    isRecord(value) &&
    isString(value.id) &&
    isString(value.filename) &&
    isString(value.status) &&
    typeof value.pageCount === "number" &&
    optionalString(value.failureMessage)
  );
}

function isKnowledgeMap(value: unknown): value is ApiRecord {
  return (
    isRecord(value) &&
    isString(value.id) &&
    isString(value.materialId) &&
    isString(value.status) &&
    isString(value.content) &&
    optionalString(value.verificationMessage) &&
    optionalString(value.reviewedBy) &&
    optionalString(value.reviewedAt) &&
    isString(value.createdAt) &&
    isString(value.updatedAt)
  );
}

function isKnowledgeMapJob(value: unknown): value is ApiRecord {
  return (
    isRecord(value) &&
    isString(value.id) &&
    isString(value.materialId) &&
    isString(value.status) &&
    optionalString(value.failureMessage) &&
    isString(value.createdAt) &&
    isString(value.updatedAt)
  );
}

function isMaterialMapState(value: unknown): value is ApiRecord {
  return (
    isRecord(value) &&
    isMaterial(value.material) &&
    (value.candidate === null || isKnowledgeMap(value.candidate)) &&
    (value.approved === null || isKnowledgeMap(value.approved)) &&
    (value.job === null || isKnowledgeMapJob(value.job))
  );
}

function hasArray(
  value: ApiRecord,
  key: string,
  predicate: (item: unknown) => boolean,
): boolean {
  return Array.isArray(value[key]) && value[key].every(predicate);
}

function decodeResponse(
  path: string,
  method: string,
  payload: unknown,
): ApiRecord {
  if (!isRecord(payload)) invalidResponse();

  const pathname = path.split("?")[0];
  if (pathname === "/api/me")
    return isUser(payload.user) ? payload : invalidResponse();
  if (pathname === "/api/courses" && method === "GET")
    return hasArray(payload, "courses", isCourse) ? payload : invalidResponse();
  if (pathname === "/api/courses" && method === "POST")
    return isCourse(payload.course) ? payload : invalidResponse();
  if (/^\/api\/courses\/[^/]+$/.test(pathname) && method === "PATCH")
    return isCourse(payload.course) ? payload : invalidResponse();
  if (
    (pathname === "/api/auth/signin" || pathname === "/api/auth/signup") &&
    method === "POST"
  )
    return isUser(payload.user) ? payload : invalidResponse();
  if (/^\/api\/courses\/[^/]+\/forum-tags$/.test(pathname) && method === "GET")
    return hasArray(payload, "tags", isForumTag) ? payload : invalidResponse();
  if (/^\/api\/courses\/[^/]+\/posts$/.test(pathname) && method === "GET")
    return hasArray(payload, "posts", isPost) ? payload : invalidResponse();
  if (
    /^\/api\/courses\/[^/]+\/posts\/[^/]+$/.test(pathname) &&
    method === "GET"
  )
    return isPost(payload.post) && hasArray(payload, "comments", isComment)
      ? payload
      : invalidResponse();
  if (
    /^\/api\/courses\/[^/]+\/posts(?:\/[^/]+)?$/.test(pathname) &&
    ["POST", "PATCH"].includes(method)
  )
    return isPost(payload.post) ? payload : invalidResponse();
  if (
    /^\/api\/courses\/[^/]+\/posts\/[^/]+\/comments(?:\/[^/]+)?$/.test(
      pathname,
    ) &&
    ["POST", "PATCH"].includes(method)
  )
    return isComment(payload.comment) ? payload : invalidResponse();
  if (/^\/api\/courses\/[^/]+\/members$/.test(pathname) && method === "GET")
    return hasArray(payload, "members", isMember) ? payload : invalidResponse();
  if (/^\/api\/courses\/[^/]+\/resources$/.test(pathname) && method === "GET")
    return hasArray(payload, "resources", isResource)
      ? payload
      : invalidResponse();
  if (
    /^\/api\/courses\/[^/]+\/knowledge-maps$/.test(pathname) &&
    method === "GET"
  )
    return hasArray(payload, "maps", isMaterialMapState) &&
      isBoolean(payload.fallbackConfigured)
      ? payload
      : invalidResponse();
  return payload;
}

async function readJson(response: Response): Promise<unknown> {
  try {
    return await response.json();
  } catch {
    return invalidResponse();
  }
}

function readErrorMessage(payload: unknown): string | undefined {
  if (!isRecord(payload) || !isRecord(payload.error)) return undefined;
  return isString(payload.error.message) ? payload.error.message : undefined;
}

export async function api<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const response = await fetch(path, {
    credentials: "same-origin",
    ...options,
    headers: {
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...options.headers,
    },
  });

  if (!response.ok) {
    const payload = await response.json().catch(() => ({}));
    throw Object.assign(
      new Error(
        readErrorMessage(payload) ??
          "The request could not be completed. Please try again.",
      ),
      { status: response.status },
    );
  }

  if (response.status === 204) return undefined as T;
  return decodeResponse(
    path,
    options.method ?? "GET",
    await readJson(response),
  ) as T;
}

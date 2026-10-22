import { useCallback, useEffect, useRef, useState } from "react";
import type { CSSProperties, Dispatch, FormEvent, SetStateAction } from "react";
import { api } from "../api/client";
import type { Comment, Course, ForumTag, Post, User } from "../api/types";
import { canDelete, date, DeleteButton, EmptyState } from "../shared/ui";
import { StaffAuthorBadge } from "./tagPresentation";
export function Forum({
  user,
  course,
  reportError,
  reportNotice,
}: {
  user: User;
  course: Course;
  reportError: (message: string) => void;
  reportNotice: (message: string) => void;
}) {
  const [posts, setPosts] = useState<Post[]>([]);
  const [tags, setTags] = useState<ForumTag[]>([]);
  const [loading, setLoading] = useState(true);
  const [composer, setComposer] = useState(false);
  const [selected, setSelected] = useState<Post | null>(null);
  const [activeTag, setActiveTag] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);
  const requestId = useRef(0);
  const refresh = useCallback(
    async (filter = activeTag) => {
      const request = ++requestId.current;
      setLoading(true);
      setFailed(false);
      try {
        const tagResult = await api<{
          tags: ForumTag[];
        }>(`/api/courses/${course.id}/forum-tags`);
        if (request !== requestId.current) return;
        setTags(tagResult.tags);
        if (filter && !tagResult.tags.some((tag) => tag.id === filter)) {
          setActiveTag(null);
          reportNotice("That tag was removed. Showing all posts.");
          filter = null;
        }
        const query = filter ? `?tagId=${encodeURIComponent(filter)}` : "";
        const result = await api<{
          posts: Post[];
        }>(`/api/courses/${course.id}/posts${query}`);
        if (request === requestId.current) setPosts(result.posts);
      } catch (e) {
        if (request === requestId.current) {
          setPosts([]);
          setTags([]);
          setSelected(null);
          setComposer(false);
          setFailed(true);
          reportError((e as Error).message);
        }
      } finally {
        if (request === requestId.current) setLoading(false);
      }
    },
    [course.id, activeTag, reportError, reportNotice],
  );
  useEffect(() => {
    setSelected(null);
    setComposer(false);
    setActiveTag(null);
    void refresh(null);
  }, [course.id]);
  const filterBy = (id: string) => {
    const next = activeTag === id ? null : id;
    setActiveTag(next);
    void refresh(next);
  };
  if (selected)
    return (
      <Thread
        user={user}
        course={course}
        post={selected}
        back={() => {
          setSelected(null);
          void refresh();
        }}
        onDeleted={(postId) => {
          setPosts((items) => items.filter((item) => item.id !== postId));
          setSelected(null);
          reportNotice("Post deleted.");
        }}
        reportError={reportError}
        reportNotice={reportNotice}
      />
    );
  return (
    <div>
      <div className="section-heading">
        <div>
          <h2>Forum</h2>
          <p className="muted">
            Discuss course questions with your classmates and course staff.
          </p>
        </div>
        <button className="primary" onClick={() => setComposer(true)}>
          New post
        </button>
      </div>
      <div className="tag-filter" role="group" aria-label="Filter by tag">
        {loading && !tags.length ? (
          <p className="loading">Loading tags…</p>
        ) : (
          tags.map((tag) => (
            <button
              key={tag.id}
              className={`tag-pill ${activeTag === tag.id ? "selected" : ""}`}
              style={
                {
                  "--tag-color": tag.color,
                } as CSSProperties
              }
              aria-pressed={activeTag === tag.id}
              onClick={() => filterBy(tag.id)}
            >
              {activeTag === tag.id && <span aria-hidden="true">✓ </span>}
              {tag.name}
            </button>
          ))
        )}
      </div>
      {composer && (
        <PostEditor
          course={course}
          onSaved={(post) => {
            setComposer(false);
            if (!activeTag || post.tag.id === activeTag)
              setPosts((items) => [post, ...items]);
            else void refresh();
            reportNotice("Post published.");
          }}
          onCancel={() => setComposer(false)}
          reportError={reportError}
        />
      )}
      {loading ? (
        <p className="loading">Loading forum…</p>
      ) : failed ? (
        <EmptyState
          title="Forum unavailable"
          text="Your course access may have changed. Try again to refresh access."
          action="Retry"
          onAction={() => void refresh()}
        />
      ) : posts.length ? (
        <div className="cards">
          {posts.map((post) => (
            <article className="post-card" key={post.id}>
              <button className="post-link" onClick={() => setSelected(post)}>
                <h3>{post.title}</h3>
                <p className="post-preview-meta">
                  <span
                    className="post-preview-tag"
                    style={{
                      color: post.tag.color,
                    }}
                  >
                    {post.tag.name}
                  </span>
                  <span>
                    {post.author.username}
                    {post.author.role === "staff" && <StaffAuthorBadge />}
                  </span>
                  <span>{date(post.createdAt)}</span>
                </p>
                <div
                  className="markdown"
                  dangerouslySetInnerHTML={{
                    __html: post.renderedDescription,
                  }}
                />
              </button>
            </article>
          ))}
        </div>
      ) : activeTag ? (
        <EmptyState
          title={`No posts tagged “${tags.find((tag) => tag.id === activeTag)?.name ?? ""}”.`}
          text="Try another tag or show all course posts."
          action="Clear tag filter"
          onAction={() => filterBy(activeTag)}
        />
      ) : (
        <EmptyState
          title="No posts yet"
          text="Start the course discussion by asking the first question."
        />
      )}
    </div>
  );
}
function Thread({
  user,
  course,
  post: initialPost,
  back,
  onDeleted,
  reportError,
  reportNotice,
}: {
  user: User;
  course: Course;
  post: Post;
  back: () => void;
  onDeleted: (postId: string) => void;
  reportError: (message: string) => void;
  reportNotice: (message: string) => void;
}) {
  const [post, setPost] = useState<Post | null>(initialPost);
  const [comments, setComments] = useState<Comment[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [editingPost, setEditingPost] = useState(false);
  const [reply, setReply] = useState("");
  const [busy, setBusy] = useState(false);
  const load = useCallback(async () => {
    setLoading(true);
    setFailed(false);
    try {
      const result = await api<{
        post: Post;
        comments: Comment[];
      }>(`/api/courses/${course.id}/posts/${initialPost.id}`);
      setPost(result.post);
      setComments(result.comments);
    } catch (e) {
      setPost(null);
      setComments([]);
      setEditingPost(false);
      setFailed(true);
      reportError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, [course.id, initialPost.id, reportError]);
  useEffect(() => {
    void load();
  }, [load]);
  const submitReply = async (event: FormEvent) => {
    event.preventDefault();
    if (!post || !reply.trim()) return reportError("Reply text is required.");
    setBusy(true);
    try {
      const result = await api<{
        comment: Comment;
      }>(`/api/courses/${course.id}/posts/${post.id}/comments`, {
        method: "POST",
        body: JSON.stringify({
          body: reply,
        }),
      });
      setComments((items) => [...items, result.comment]);
      setReply("");
      reportNotice("Reply published.");
    } catch (e) {
      reportError((e as Error).message);
      void load();
    } finally {
      setBusy(false);
    }
  };
  if (!post)
    return (
      <div>
        <button className="back" onClick={back}>
          ← Back to forum
        </button>
        {loading ? (
          <p className="loading">Loading discussion…</p>
        ) : (
          <EmptyState
            title="Discussion unavailable"
            text="This discussion may have been removed or your course access changed."
            action="Retry"
            onAction={() => void load()}
          />
        )}
      </div>
    );
  return (
    <div>
      <button className="back" onClick={back}>
        ← Back to forum
      </button>
      {editingPost ? (
        <PostEditor
          course={course}
          post={post}
          onSaved={(next) => {
            setPost(next);
            setEditingPost(false);
            reportNotice("Post updated.");
          }}
          onCancel={() => setEditingPost(false)}
          reportError={reportError}
        />
      ) : (
        <article className="thread">
          <div className="section-heading">
            <div>
              <h2>{post.title}</h2>
              <p className="post-preview-meta">
                <span
                  className="post-preview-tag"
                  style={{
                    color: post.tag.color,
                  }}
                >
                  {post.tag.name}
                </span>
                <span>
                  {post.author.username}
                  {post.author.role === "staff" && <StaffAuthorBadge />}
                </span>
                <span>{date(post.createdAt)}</span>
              </p>
            </div>
            <div className="actions">
              {user.id === post.author.id && (
                <button onClick={() => setEditingPost(true)}>Edit post</button>
              )}
              {canDelete(user, post.author) && (
                <DeleteButton
                  label="post"
                  action={async () => {
                    await api(`/api/courses/${course.id}/posts/${post.id}`, {
                      method: "DELETE",
                    });
                    onDeleted(post.id);
                  }}
                  reportError={reportError}
                  onFailure={() => void load()}
                />
              )}
            </div>
          </div>
          <div
            className="markdown"
            dangerouslySetInnerHTML={{
              __html: post.renderedDescription,
            }}
          />
        </article>
      )}
      <section className="replies">
        <h2>Replies</h2>
        {loading ? (
          <p className="loading">Loading replies…</p>
        ) : comments.length ? (
          comments.map((comment) => (
            <CommentCard
              key={comment.id}
              user={user}
              course={course}
              postId={post.id}
              comment={comment}
              onChanged={setComments}
              reload={() => void load()}
              reportError={reportError}
              reportNotice={reportNotice}
            />
          ))
        ) : (
          <p className="muted no-replies">
            No replies yet. Add a helpful response below.
          </p>
        )}
        <form className="editor reply" onSubmit={submitReply}>
          <label htmlFor="reply">Write a response</label>
          <textarea
            id="reply"
            value={reply}
            onChange={(event) => setReply(event.target.value)}
            disabled={busy}
            required
          />
          <p className="muted">
            Markdown is supported. Submitted text is safely rendered.
          </p>
          <button className="primary" disabled={busy}>
            {busy ? "Posting…" : "Post reply"}
          </button>
        </form>
      </section>
    </div>
  );
}
function PostEditor({
  course,
  post,
  onSaved,
  onCancel,
  reportError,
}: {
  course: Course;
  post?: Post;
  onSaved: (post: Post) => void;
  onCancel: () => void;
  reportError: (message: string) => void;
}) {
  const [title, setTitle] = useState(post?.title ?? "");
  const [description, setDescription] = useState(post?.description ?? "");
  const [tags, setTags] = useState<ForumTag[]>([]);
  const [tagId, setTagId] = useState(post?.tag.id ?? "");
  const tagIdRef = useRef(tagId);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => {
    tagIdRef.current = tagId;
  }, [tagId]);
  const loadTags = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const current = (
        await api<{
          tags: ForumTag[];
        }>(`/api/courses/${course.id}/forum-tags`)
      ).tags;
      setTags(current);
      if (!current.some((tag) => tag.id === tagIdRef.current)) {
        const general = current.find((tag) => tag.isDefault);
        const nextId = general?.id ?? "";
        tagIdRef.current = nextId;
        setTagId(nextId);
        if (post)
          setError("The previous tag was removed. General is selected.");
      }
    } catch (e) {
      setTags([]);
      tagIdRef.current = "";
      setTagId("");
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, [course.id, post?.id]);
  useEffect(() => {
    void loadTags();
  }, [loadTags]);
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!title.trim()) return reportError("A post title is required.");
    if (title.length > 120)
      return reportError("Post title must be at most 120 characters.");
    if (description.length > 2000)
      return reportError("Description must be at most 2,000 characters.");
    if (!tagId || !tags.some((tag) => tag.id === tagId))
      return reportError("Choose a current tag for this course.");
    setBusy(true);
    try {
      const result = await api<{
        post: Post;
      }>(
        post
          ? `/api/courses/${course.id}/posts/${post.id}`
          : `/api/courses/${course.id}/posts`,
        {
          method: post ? "PATCH" : "POST",
          body: JSON.stringify({
            title,
            description,
            tagId,
          }),
        },
      );
      onSaved(result.post);
    } catch (e) {
      reportError((e as Error).message);
      await loadTags();
    } finally {
      setBusy(false);
    }
  };
  return (
    <form className="editor post-editor" onSubmit={submit}>
      <h2>{post ? "Edit post" : "Create a post"}</h2>
      <label htmlFor="post-title">
        Title{" "}
        <small>
          {title.length}
          /120
        </small>
      </label>
      <input
        id="post-title"
        maxLength={120}
        value={title}
        onChange={(event) => setTitle(event.target.value)}
        disabled={busy}
        required
      />
      <fieldset className="post-tag-fieldset" disabled={busy || !tags.length}>
        <legend>Tag</legend>
        {loading ? (
          <p className="loading">Loading course tags…</p>
        ) : (
          <div className="post-tag-picker" role="radiogroup" aria-label="Tag">
            {tags.map((tag, index) => (
              <button
                key={tag.id}
                type="button"
                role="radio"
                aria-checked={tagId === tag.id}
                tabIndex={tagId === tag.id ? 0 : -1}
                className="tag-pill"
                style={
                  {
                    "--tag-color": tag.color,
                  } as CSSProperties
                }
                onClick={() => setTagId(tag.id)}
                onKeyDown={(event) => {
                  const buttons = Array.from(
                    event.currentTarget.parentElement?.querySelectorAll<HTMLButtonElement>(
                      'button[role="radio"]',
                    ) ?? [],
                  );
                  let nextIndex = -1;
                  if (event.key === "ArrowRight" || event.key === "ArrowDown")
                    nextIndex = (index + 1) % tags.length;
                  else if (event.key === "ArrowLeft" || event.key === "ArrowUp")
                    nextIndex = (index - 1 + tags.length) % tags.length;
                  else if (event.key === "Home") nextIndex = 0;
                  else if (event.key === "End") nextIndex = tags.length - 1;
                  if (nextIndex >= 0) {
                    event.preventDefault();
                    setTagId(tags[nextIndex].id);
                    buttons[nextIndex]?.focus();
                  }
                }}
              >
                {tag.name}
              </button>
            ))}
          </div>
        )}
      </fieldset>
      {error && (
        <p role="status" className="muted">
          {error}{" "}
          {!tags.length && (
            <button type="button" onClick={() => void loadTags()}>
              Retry
            </button>
          )}
        </p>
      )}
      <label htmlFor="post-description">
        Description{" "}
        <small>
          {description.length}
          /2000
        </small>
      </label>
      <textarea
        id="post-description"
        maxLength={2000}
        value={description}
        onChange={(event) => setDescription(event.target.value)}
        disabled={busy}
      />
      <p className="muted">Markdown is supported. HTML is not executed.</p>
      <div className="actions">
        <button type="button" onClick={onCancel} disabled={busy}>
          Cancel
        </button>
        <button
          className="primary"
          disabled={busy || loading || !tags.some((tag) => tag.id === tagId)}
        >
          {busy ? "Saving…" : post ? "Save changes" : "Publish post"}
        </button>
      </div>
    </form>
  );
}
function CommentCard({
  user,
  course,
  postId,
  comment,
  onChanged,
  reload,
  reportError,
  reportNotice,
}: {
  user: User;
  course: Course;
  postId: string;
  comment: Comment;
  onChanged: Dispatch<SetStateAction<Comment[]>>;
  reload: () => void;
  reportError: (message: string) => void;
  reportNotice: (message: string) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [body, setBody] = useState(comment.body);
  const [busy, setBusy] = useState(false);
  const save = async (event: FormEvent) => {
    event.preventDefault();
    if (!body.trim()) return reportError("Reply text is required.");
    setBusy(true);
    try {
      const result = await api<{
        comment: Comment;
      }>(`/api/courses/${course.id}/posts/${postId}/comments/${comment.id}`, {
        method: "PATCH",
        body: JSON.stringify({
          body,
        }),
      });
      onChanged((items) =>
        items.map((item) => (item.id === comment.id ? result.comment : item)),
      );
      setEditing(false);
      reportNotice("Reply updated.");
    } catch (e) {
      reportError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };
  return (
    <article className="comment">
      <p className="meta">
        <b>{comment.author.username}</b>
        {comment.author.role === "staff" ? <StaffAuthorBadge /> : " · student"}
        {" · "}
        {date(comment.createdAt)}
      </p>
      {editing ? (
        <form onSubmit={save}>
          <textarea
            value={body}
            onChange={(event) => setBody(event.target.value)}
            disabled={busy}
            required
          />
          <div className="actions">
            <button
              type="button"
              onClick={() => {
                setBody(comment.body);
                setEditing(false);
              }}
              disabled={busy}
            >
              Cancel
            </button>
            <button className="primary" disabled={busy}>
              Save reply
            </button>
          </div>
        </form>
      ) : (
        <>
          <div
            className="markdown"
            dangerouslySetInnerHTML={{
              __html: comment.renderedBody,
            }}
          />
          {canDelete(user, comment.author) && (
            <div className="card-actions">
              {user.id === comment.author.id && (
                <button onClick={() => setEditing(true)}>Edit</button>
              )}
              <DeleteButton
                label="reply"
                action={async () => {
                  await api(
                    `/api/courses/${course.id}/posts/${postId}/comments/${comment.id}`,
                    {
                      method: "DELETE",
                    },
                  );
                  onChanged((items) =>
                    items.filter((item) => item.id !== comment.id),
                  );
                  reportNotice("Reply deleted.");
                }}
                reportError={reportError}
                onFailure={reload}
              />
            </div>
          )}
        </>
      )}
    </article>
  );
}

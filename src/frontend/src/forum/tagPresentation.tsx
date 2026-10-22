import type { CSSProperties } from "react";
import type { ForumTag } from "../api/types";

export const TAG_PALETTE = [
  { name: "Blue", color: "#1D4ED8" },
  { name: "Green", color: "#166534" },
  { name: "Amber", color: "#854D0E" },
  { name: "Violet", color: "#6D28D9" },
  { name: "Teal", color: "#0F766E" },
  { name: "Red", color: "#B91C1C" },
  { name: "Light Blue", color: "#0369A1" },
  { name: "Magenta", color: "#A21CAF" },
  { name: "Charcoal", color: "#1F2937" },
];

export function TagBadge({ tag }: { tag: ForumTag }) {
  return (
    <span
      className="tag-pill tag-badge"
      style={{ "--tag-color": tag.color } as CSSProperties}
    >
      {tag.name}
    </span>
  );
}

export function StaffAuthorBadge() {
  return <span className="badge staff-badge staff-author-badge">STAFF</span>;
}

export function trimTagWhitespace(value: string) {
  return value.replace(
    /^[\p{White_Space}\uFEFF]+|[\p{White_Space}\uFEFF]+$/gu,
    "",
  );
}

export function tagColorName(tag: ForumTag) {
  if (tag.isDefault) return "Grey";
  return TAG_PALETTE.find((item) => item.color === tag.color)?.name ?? "Color";
}

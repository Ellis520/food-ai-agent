import { base, request } from "./index";

export const communityCategories = [
  { value: "share", label: "日常分享", icon: "Utensils" },
  { value: "recommend", label: "美食推荐", icon: "Heart" },
  { value: "avoid", label: "避雷分享", icon: "TriangleAlert" },
  { value: "buddy", label: "找饭搭子", icon: "MessageCircleHeart" },
] as const;

export type Category = (typeof communityCategories)[number]["value"];

export interface PostDraft {
  category: Category;
  text: string;
  food: string;
  place: string;
  meetup: string;
}

export interface Reply {
  id: number;
  author: string;
  avatar: string | null;
  text: string;
  createdAt: string;
  mine: boolean;
}

export interface Post extends Reply, PostDraft {
  imageCount: number;
  replyCount: number;
  likeCount: number;
  liked: boolean;
  saved: boolean;
}

export interface Reaction {
  likeCount: number;
  liked: boolean;
  saved: boolean;
}

interface Page<T> {
  items: T[];
  next: number | null;
}

export const community = {
  posts(category: Category | "", q: string, before: number | null) {
    const query = new URLSearchParams({ q });
    if (category) query.set("category", category);
    if (before) query.set("before", String(before));
    return request<Page<Post>>("/community/posts?" + query);
  },
  storePosts(store: string, before: number | null) {
    const query = new URLSearchParams({ store });
    if (before) query.set("before", String(before));
    return request<Page<Post>>("/community/posts?" + query);
  },
  publish(draft: PostDraft, images: string[], requestId: string) {
    return request<Post>("/community/posts", { ...draft, images, requestId });
  },
  image(postId: number, index: number) {
    return `${base}/community/posts/${postId}/images/${index}`;
  },
  remove(postId: number) {
    return request(`/community/posts/${postId}`, undefined, "DELETE");
  },
  saved() {
    return request<Page<Post>>("/community/posts/saved");
  },
  like(postId: number, on: boolean) {
    return request<Reaction>(`/community/posts/${postId}/like`, { on }, "PUT");
  },
  save(postId: number, on: boolean) {
    return request<Reaction>(`/community/posts/${postId}/save`, { on }, "PUT");
  },
  replies(postId: number, before: number | null) {
    return request<Page<Reply>>(
      `/community/posts/${postId}/replies` +
        (before ? `?before=${before}` : ""),
    );
  },
  reply(postId: number, text: string, requestId: string) {
    return request<Reply>(`/community/posts/${postId}/replies`, {
      text,
      requestId,
    });
  },
  removeReply(postId: number, replyId: number) {
    return request(
      `/community/posts/${postId}/replies/${replyId}`,
      undefined,
      "DELETE",
    );
  },
};

const timeFormat = new Intl.DateTimeFormat("zh-CN", {
  dateStyle: "short",
  timeStyle: "short",
});
export function communityTime(value: string) {
  return timeFormat.format(new Date(value));
}

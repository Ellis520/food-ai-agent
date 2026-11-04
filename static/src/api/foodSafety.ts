import catalog from "../../../shared/food-safety.json";

export interface SafetySource {
  id: string;
  title: string;
  url: string;
}
export interface SafetyTurn {
  role: "user" | "assistant";
  text: string;
}
export interface SafetyReply {
  answer: string;
  steps: string[];
  followUp: string;
  sources: SafetySource[];
  kind: "general" | "professional" | "urgent";
  provider: "qwen" | "local-safety-rule" | "demo";
  reviewedAt: string;
  note: string;
}
export const safetyTips = catalog.tips;
export const safetySources: SafetySource[] = catalog.sources;
export const safetyReviewedAt = catalog.reviewedAt;
export function demoSafetyReply(message: string): SafetyReply {
  const urgent = catalog.urgentKeywords.some((word) => message.includes(word));
  const tip = safetyTips.find(
    (tip) =>
      tip.id ===
      (/头孢|药|酒/.test(message)
        ? "alcohol"
        : /过敏|花生/.test(message)
          ? "allergens"
          : /剩|保存|冷藏|隔夜/.test(message)
            ? "leftovers"
            : /生熟|污染|砧板/.test(message)
              ? "separate"
              : "cook"),
  )!;
  const content = urgent
    ? catalog.urgent
    : {
        answer: `这是预置规则演示，不是千问回答。${tip.summary}`,
        steps: [
          "可以查看下方资料了解适用范围。具体药物、过敏或不适问题，请咨询医生或药师。",
        ],
        followUp: "配置千问后，可根据你确认发送的问题提供有上下文的科普答复。",
        sourceIds: tip.sourceIds,
      };
  return {
    ...content,
    sources: safetySources.filter((source) =>
      content.sourceIds.includes(source.id),
    ),
    kind: urgent
      ? "urgent"
      : /药|酒|过敏/.test(message)
        ? "professional"
        : "general",
    provider: urgent ? "local-safety-rule" : "demo",
    reviewedAt: safetyReviewedAt,
    note: "演示模式 · 未调用千问；依据预置资料，不代表个体诊疗。",
  };
}

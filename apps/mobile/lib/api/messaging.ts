import type { ConversationSummary, MessageSummary, PageResponse, SendMessageRequest, StartConversationRequest } from "@havyn/shared";
import { apiFetch } from "./http";

export function startConversation(accessToken: string, propertyId: string, request: StartConversationRequest) {
  return apiFetch<ConversationSummary>(`/api/v1/properties/${propertyId}/conversations`, { method: "POST", accessToken, body: request });
}

export function listConversations(accessToken: string, page = 0, size = 20) {
  return apiFetch<PageResponse<ConversationSummary>>(`/api/v1/conversations?page=${page}&size=${size}`, { accessToken });
}

export function getConversation(accessToken: string, id: string) {
  return apiFetch<ConversationSummary>(`/api/v1/conversations/${id}`, { accessToken });
}

export function listMessages(accessToken: string, conversationId: string, page = 0, size = 50) {
  return apiFetch<PageResponse<MessageSummary>>(`/api/v1/conversations/${conversationId}/messages?page=${page}&size=${size}`, {
    accessToken,
  });
}

export function sendMessage(accessToken: string, conversationId: string, request: SendMessageRequest) {
  return apiFetch<MessageSummary>(`/api/v1/conversations/${conversationId}/messages`, { method: "POST", accessToken, body: request });
}

export function markConversationRead(accessToken: string, conversationId: string) {
  return apiFetch<void>(`/api/v1/conversations/${conversationId}/read`, { method: "POST", accessToken });
}

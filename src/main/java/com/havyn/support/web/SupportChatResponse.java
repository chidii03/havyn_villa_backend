package com.havyn.support.web;

import java.util.List;

public record SupportChatResponse(List<SupportChatMessageSummary> messages) {
}

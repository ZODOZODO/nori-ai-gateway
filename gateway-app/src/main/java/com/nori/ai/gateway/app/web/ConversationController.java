package com.nori.ai.gateway.app.web;

import com.nori.ai.gateway.core.expiry.ExpiryCheckpoint;
import com.nori.ai.gateway.persistence.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public final class ConversationController {
    private final ConversationRepository conversations;private final MessageRepository messages;private final QueryController query;private final ResultResponseAssembler results;private final ExpiryCheckpoint expiry;
    public ConversationController(ConversationRepository conversations,MessageRepository messages,QueryController query,ResultResponseAssembler results,ExpiryCheckpoint expiry) { this.conversations=conversations;this.messages=messages;this.query=query;this.results=results;this.expiry=expiry; }
    @PostMapping("/v1/conversations") public ResponseEntity<?> create(HttpServletRequest request) {
        var scope=ApiRequestInput.scope(request);String id=UUID.randomUUID().toString();if(!conversations.insert(scope,id))throw new IllegalStateException("대화를 저장할 수 없습니다");
        var conversation=conversations.find(scope,id).orElseThrow(UniformNotFound::new);expiry.assertLive(conversation.expiresAt());return ResponseEntity.status(201).body(conversation);
    }
    @GetMapping("/v1/conversations/{id}") public Map<String,Object> find(HttpServletRequest request,@PathVariable String id) {
        var scope=ApiRequestInput.scope(request);var conversation=conversations.find(scope,id).orElseThrow(UniformNotFound::new);
        var history=new ArrayList<Map<String,Object>>();
        for(var message:messages.findByConversation(scope,id)) {
            var item=new LinkedHashMap<String,Object>();item.put("messageId",message.id());item.put("role",message.role());
            if(message.responseRef()!=null) {var access=results.assemble(scope,message.responseRef());item.put("content",access.response());}
            else item.put("content",message.contentMasked());
            history.add(item);
        }
        expiry.assertLive(conversation.expiresAt());return Map.of("conversation",conversation,"messages",history);
    }
    @PostMapping("/v1/conversations/{id}/messages") public ResponseEntity<?> message(HttpServletRequest request,@PathVariable String id,@RequestBody Map<String,Object> body) {
        if(body.get("conversationId")!=null&&!id.equals(body.get("conversationId")))throw new IllegalArgumentException("대화 경로가 일치하지 않습니다");return query.accept(request,body,id);
    }
}

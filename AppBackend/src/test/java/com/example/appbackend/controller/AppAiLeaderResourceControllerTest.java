package com.example.appbackend.controller;

import com.example.appbackend.dto.AssistantResourceInteractionRequest;
import com.example.appbackend.dto.AssistantResourceInteractionResponse;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.impl.AssistantResourceInteractionService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppAiLeaderResourceControllerTest {

    @Test
    void exposesTheResourceSpecificRouteAndUsesAuthenticatedUserOnly() throws Exception {
        AssistantResourceInteractionService service = mock(AssistantResourceInteractionService.class);
        AppAiLeaderResourceController controller = new AppAiLeaderResourceController(service);
        AssistantResourceInteractionRequest request = new AssistantResourceInteractionRequest();
        request.setAction("open");
        AssistantResourceInteractionResponse recorded =
                new AssistantResourceInteractionResponse("recorded", false, "ari_1", null);
        when(service.record(42L, "session-1", 101L, "res_doc", request)).thenReturn(recorded);
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setAttribute("userId", 42L);

        Result<AssistantResourceInteractionResponse> result = controller.interact(
                "session-1", 101L, "res_doc", request, httpRequest);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isSameAs(recorded);
        verify(service).record(42L, "session-1", 101L, "res_doc", request);

        RequestMapping base = AppAiLeaderResourceController.class.getAnnotation(RequestMapping.class);
        assertThat(base.value()).containsExactly("/api/ai/leader");
        Method method = AppAiLeaderResourceController.class.getMethod(
                "interact", String.class, Long.class, String.class,
                AssistantResourceInteractionRequest.class, jakarta.servlet.http.HttpServletRequest.class);
        PostMapping mapping = method.getAnnotation(PostMapping.class);
        assertThat(mapping.value()).containsExactly(
                "/sessions/{sessionId}/messages/{messageId}/resources/{resourceId}/interactions");
    }

    @Test
    void rejectsRequestsWithoutAnAuthenticatedUserContext() {
        AppAiLeaderResourceController controller =
                new AppAiLeaderResourceController(mock(AssistantResourceInteractionService.class));
        AssistantResourceInteractionRequest request = new AssistantResourceInteractionRequest();
        request.setAction("view");

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.interact("session-1", 101L, "res_doc", request,
                        new MockHttpServletRequest()));

        assertThat(error.getCode()).isEqualTo(401);
    }
}

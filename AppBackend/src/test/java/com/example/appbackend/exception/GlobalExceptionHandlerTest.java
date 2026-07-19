package com.example.appbackend.exception;

import com.example.appbackend.entity.Result;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    @Test
    void illegalArgumentUsesBadRequestForHttpStatusAndResponseBody() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        Result<?> result = new GlobalExceptionHandler().handleIllegalArgumentException(
                new IllegalArgumentException("参数错误"), response);

        assertEquals(400, response.getStatus());
        assertEquals(400, result.getCode());
        assertEquals("参数错误", result.getMsg());
    }
}

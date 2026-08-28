package com.example.appbackend.controller;

import com.example.appbackend.dto.StudyGoalDTO;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.StudyGoalService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudyGoalControllerTest {

    @Mock
    private StudyGoalService studyGoalService;

    @Mock
    private HttpServletRequest httpRequest;

    @Test
    void completionEndpointsRejectMissingCompletionValueInsteadOfTreatingItAsUnchecked() {
        when(httpRequest.getAttribute("userId")).thenReturn(7L);
        StudyGoalController controller = new StudyGoalController(studyGoalService);
        StudyGoalDTO.TaskCompletionRequest request = new StudyGoalDTO.TaskCompletionRequest();

        assertThrows(BusinessException.class,
                () -> controller.updateCompletion(1L, request, httpRequest));
        assertThrows(BusinessException.class,
                () -> controller.updateSubtaskCompletion(11L, request, httpRequest));

        verifyNoInteractions(studyGoalService);
    }
}

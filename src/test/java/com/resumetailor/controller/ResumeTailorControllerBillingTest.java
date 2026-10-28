package com.resumetailor.controller;

import com.resumetailor.dto.TailorResponse;
import com.resumetailor.exception.InsufficientCreditsException;
import com.resumetailor.repository.ResumeHistoryRepository;
import com.resumetailor.repository.UserRepository;
import com.resumetailor.service.ResumeTailorService;
import com.resumetailor.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Credit charging and refunds for the optional cover letter (via the API endpoint). */
@ExtendWith(MockitoExtension.class)
class ResumeTailorControllerBillingTest {

    private static final String EMAIL = "user@example.com";
    private static final String JOB = "Senior Java developer with Spring Boot and PostgreSQL";

    @Mock private ResumeTailorService resumeTailorService;
    @Mock private ResumeHistoryRepository historyRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserService userService;

    @InjectMocks private ResumeTailorController controller;

    private final Authentication auth = new TestingAuthenticationToken(EMAIL, null);
    private final MockMultipartFile file =
            new MockMultipartFile("resumeFile", "cv.pdf", "application/pdf", new byte[] {1});

    @Test
    void resumeOnlyCostsOneCredit() {
        when(resumeTailorService.tailorResume(any(), anyString(), anyString(), eq(false)))
                .thenReturn(TailorResponse.builder().success(true).build());

        call(false);

        verify(userService).deductCredits(EMAIL, 1);
        verify(userService, never()).refundCredits(anyString(), anyInt());
    }

    @Test
    void resumeWithCoverLetterCostsTwoCredits() {
        when(resumeTailorService.tailorResume(any(), anyString(), anyString(), eq(true)))
                .thenReturn(TailorResponse.builder().success(true).coverLetterText("Dear Hiring Manager,").build());

        ResponseEntity<TailorResponse> response = call(true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userService).deductCredits(EMAIL, 2);
        verify(userService, never()).refundCredits(anyString(), anyInt());
    }

    @Test
    void failedCoverLetterRefundsOnlyItsCredit() {
        when(resumeTailorService.tailorResume(any(), anyString(), anyString(), eq(true)))
                .thenReturn(TailorResponse.builder().success(true).coverLetterFailed(true).build());

        call(true);

        verify(userService).deductCredits(EMAIL, 2);
        verify(userService).refundCredits(EMAIL, 1);
    }

    @Test
    void failedResumeRefundsEverything() {
        when(resumeTailorService.tailorResume(any(), anyString(), anyString(), eq(true)))
                .thenReturn(TailorResponse.builder().success(false).message("boom").build());

        ResponseEntity<TailorResponse> response = call(true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        verify(userService).refundCredits(EMAIL, 2);
    }

    @Test
    void insufficientCreditsForCoverLetterExplainsTheCost() {
        doThrow(new InsufficientCreditsException()).when(userService).deductCredits(EMAIL, 2);

        ResponseEntity<TailorResponse> response = call(true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        assertThat(response.getBody().getMessage()).contains("2 credits").contains("cover letter");
        verify(resumeTailorService, never()).tailorResume(any(), anyString(), anyString(), anyBoolean());
    }

    private ResponseEntity<TailorResponse> call(boolean includeCoverLetter) {
        return controller.tailorResumeApi(file, JOB, "pdf", includeCoverLetter, auth);
    }
}

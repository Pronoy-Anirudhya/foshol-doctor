package com.rootcause.foshol.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rootcause.foshol.common.contract.ErrorCodes;
import com.rootcause.foshol.review.domain.ReviewException;
import com.rootcause.foshol.review.web.ReviewExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    @Test
    void unknownRuntimeBecomesInternalProblemWithoutLeakingMessage() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new BoomController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCodes.ERR_INTERNAL))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.detail").value(not(containsString("secret-stack"))))
                .andExpect(jsonPath("$.status").value(500));
    }

    @Test
    void reviewExceptionKeepsStatusWhenGlobalAdviceIsAlsoRegistered() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ReviewStubController())
                .setControllerAdvice(new GlobalExceptionHandler(), new ReviewExceptionHandler())
                .build();
        mvc.perform(get("/advisory-missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCodes.ERR_ADVISORY_NOT_FOUND))
                .andExpect(jsonPath("$.status").value(404));
        mvc.perform(get("/remedy-mismatch"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCodes.ERR_REMEDY_DISEASE_MISMATCH))
                .andExpect(jsonPath("$.status").value(400));
    }

    @RestController
    static class BoomController {
        @GetMapping("/boom")
        void boom() {
            throw new RuntimeException("secret-stack leaked");
        }
    }

    @RestController
    static class ReviewStubController {
        @GetMapping("/advisory-missing")
        void missingAdvisory() {
            throw new ReviewException(ErrorCodes.ERR_ADVISORY_NOT_FOUND, 404, "No advisory for this case.");
        }

        @GetMapping("/remedy-mismatch")
        void remedyMismatch() {
            throw ReviewException.remedyDiseaseMismatch();
        }
    }
}

package com.rootcause.foshol.identity.web;

import com.rootcause.foshol.identity.application.command.AuthTokenResult;
import com.rootcause.foshol.identity.application.command.OfficerLoginCommand;
import com.rootcause.foshol.identity.application.command.OfficerLoginCommandHandler;
import com.rootcause.foshol.identity.application.command.RequestOtpCommand;
import com.rootcause.foshol.identity.application.command.RequestOtpCommandHandler;
import com.rootcause.foshol.identity.application.command.RequestOtpResult;
import com.rootcause.foshol.identity.application.command.VerifyOtpCommand;
import com.rootcause.foshol.identity.application.command.VerifyOtpCommandHandler;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RequestOtpCommandHandler requestOtp;
    private final VerifyOtpCommandHandler verifyOtp;
    private final OfficerLoginCommandHandler officerLogin;

    public AuthController(
            RequestOtpCommandHandler requestOtp,
            VerifyOtpCommandHandler verifyOtp,
            OfficerLoginCommandHandler officerLogin) {
        this.requestOtp = requestOtp;
        this.verifyOtp = verifyOtp;
        this.officerLogin = officerLogin;
    }

    @PostMapping("/otp/request")
    public ResponseEntity<OtpAcceptedResponse> requestOtp(@Valid @RequestBody OtpRequest request) {
        RequestOtpResult result = requestOtp.handle(new RequestOtpCommand(request.phone()));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new OtpAcceptedResponse(result.expiresInSeconds(), result.otpDeliveryMode()));
    }

    @PostMapping("/otp/verify")
    public AuthResponse verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        AuthTokenResult result = verifyOtp.handle(new VerifyOtpCommand(request.phone(), request.code()));
        return toResponse(result);
    }

    @PostMapping("/officer/login")
    public AuthResponse officerLogin(@Valid @RequestBody OfficerLoginRequest request) {
        return toResponse(officerLogin.handle(new OfficerLoginCommand(request.username(), request.password())));
    }

    private static AuthResponse toResponse(AuthTokenResult result) {
        return new AuthResponse(
                result.token(),
                result.expiresAt(),
                new PrincipalResponse(
                        result.subjectId(),
                        result.name(),
                        result.role().name(),
                        result.districtCode(),
                        result.preferredLanguage(),
                        result.username()));
    }
}

package com.rootcause.foshol.identity.web;

import com.rootcause.foshol.common.cqrs.CommandBus;
import com.rootcause.foshol.identity.application.command.AuthTokenResult;
import com.rootcause.foshol.identity.application.command.OfficerLoginCommand;
import com.rootcause.foshol.identity.application.command.RequestOtpCommand;
import com.rootcause.foshol.identity.application.command.RequestOtpResult;
import com.rootcause.foshol.identity.application.command.VerifyOtpCommand;
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

    private final CommandBus commands;

    public AuthController(CommandBus commands) {
        this.commands = commands;
    }

    @PostMapping("/otp/request")
    public ResponseEntity<OtpAcceptedResponse> requestOtp(@Valid @RequestBody OtpRequest request) {
        RequestOtpResult result = commands.handle(new RequestOtpCommand(request.phone()));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new OtpAcceptedResponse(result.expiresInSeconds(), result.otpDeliveryMode()));
    }

    @PostMapping("/otp/verify")
    public AuthResponse verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        AuthTokenResult result = commands.handle(new VerifyOtpCommand(request.phone(), request.code()));
        return toResponse(result);
    }

    @PostMapping("/officer/login")
    public AuthResponse officerLogin(@Valid @RequestBody OfficerLoginRequest request) {
        AuthTokenResult result = commands.handle(new OfficerLoginCommand(request.username(), request.password()));
        return toResponse(result);
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
                        result.divisionCode(),
                        result.districtNameBn(),
                        result.districtNameEn(),
                        result.divisionNameBn(),
                        result.divisionNameEn(),
                        result.preferredLanguage(),
                        result.username()));
    }
}

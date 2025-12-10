package com.diogotoporcov.authservice.api;

import com.diogotoporcov.authservice.api.dto.AuthResponse;
import com.diogotoporcov.authservice.api.dto.LoginRequest;
import com.diogotoporcov.authservice.api.dto.RegisterRequest;
import com.diogotoporcov.authservice.application.LoginUserUseCase;
import com.diogotoporcov.authservice.application.RegisterUserUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final RegisterUserUseCase registerUser;
    private final LoginUserUseCase loginUser;

    public AuthController(RegisterUserUseCase registerUser, LoginUserUseCase loginUser) {
        this.registerUser = registerUser;
        this.loginUser = loginUser;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return registerUser.execute(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return loginUser.execute(request);
    }
}

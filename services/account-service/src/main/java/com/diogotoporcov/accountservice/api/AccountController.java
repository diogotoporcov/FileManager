package com.diogotoporcov.accountservice.api;

import com.diogotoporcov.accountservice.api.dto.AccountResponse;
import com.diogotoporcov.accountservice.api.dto.UpdateAccountRequest;
import com.diogotoporcov.accountservice.application.GetAccountUseCase;
import com.diogotoporcov.accountservice.application.UpdateAccountUseCase;
import com.diogotoporcov.accountservice.account.entity.UserAccount;
import com.diogotoporcov.accountservice.security.AllowInactive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/account")
public class AccountController {

    private final GetAccountUseCase getAccount;
    private final UpdateAccountUseCase updateAccount;

    public AccountController(GetAccountUseCase getAccount, UpdateAccountUseCase updateAccount) {
        this.getAccount = getAccount;
        this.updateAccount = updateAccount;
    }

    @AllowInactive
    @GetMapping("/me")
    public AccountResponse me(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        UserAccount account = getAccount.execute(userId);
        return toResponse(account);
    }

    @AllowInactive
    @PatchMapping("/me")
    public AccountResponse update(@AuthenticationPrincipal Jwt jwt, @RequestBody UpdateAccountRequest req) {
        UUID userId = UUID.fromString(jwt.getSubject());

        UserAccount account = updateAccount.execute(userId, req);
        return toResponse(account);
    }

    @GetMapping("/{userId:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}}")
    public AccountResponse getUserFromId(@PathVariable("userId") UUID userId) {
        UserAccount account = getAccount.execute(userId);
        return toResponse(account);
    }

    @GetMapping("/{username}")
    public AccountResponse getUserFromUsername(@PathVariable("username") String username) {
        UserAccount account = getAccount.execute(username);
        return toResponse(account);
    }

    private static AccountResponse toResponse(UserAccount account) {
        return new AccountResponse(
                account.getUserId().toString(),
                account.getFullName(),
                account.getUsername(),
                account.getLocale(),
                account.getTimezone(),
                account.getStatus().name()
        );
    }
}

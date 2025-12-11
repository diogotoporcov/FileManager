package com.diogotoporcov.accountservice.api;

import com.diogotoporcov.accountservice.api.dto.MyAccountResponse;
import com.diogotoporcov.accountservice.api.dto.UpdateMyAccountRequest;
import com.diogotoporcov.accountservice.application.GetMyAccountUseCase;
import com.diogotoporcov.accountservice.application.UpdateMyAccountUseCase;
import com.diogotoporcov.accountservice.account.entity.UserAccount;
import com.diogotoporcov.accountservice.security.AllowInactive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/account")
public class AccountController {

    private final GetMyAccountUseCase getMyAccount;
    private final UpdateMyAccountUseCase updateMyAccount;

    public AccountController(GetMyAccountUseCase getMyAccount, UpdateMyAccountUseCase updateMyAccount) {
        this.getMyAccount = getMyAccount;
        this.updateMyAccount = updateMyAccount;
    }

    @AllowInactive
    @GetMapping("/me")
    public MyAccountResponse me(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        UserAccount account = getMyAccount.execute(userId);
        return toResponse(account);
    }

    @AllowInactive
    @PatchMapping("/me")
    public MyAccountResponse update(@AuthenticationPrincipal Jwt jwt, @RequestBody UpdateMyAccountRequest req) {
        UUID userId = UUID.fromString(jwt.getSubject());

        UserAccount account = updateMyAccount.execute(userId, req);
        return toResponse(account);
    }

    private static MyAccountResponse toResponse(UserAccount account) {
        return new MyAccountResponse(
                account.getUserId().toString(),
                account.getFullName(),
                account.getUsername(),
                account.getLocale(),
                account.getTimezone(),
                account.getStatus().name()
        );
    }
}

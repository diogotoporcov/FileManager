package com.diogotoporcov.accountservice.application;

import com.diogotoporcov.accountservice.api.dto.UpdateMyAccountRequest;
import com.diogotoporcov.accountservice.error.InvalidUsernameException;
import com.diogotoporcov.accountservice.error.UsernameAlreadyInUseException;
import com.diogotoporcov.accountservice.account.LocaleUtil;
import com.diogotoporcov.accountservice.account.TimezoneUtil;
import com.diogotoporcov.accountservice.account.entity.AccountStatus;
import com.diogotoporcov.accountservice.account.entity.UserAccount;
import com.diogotoporcov.accountservice.account.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class UpdateMyAccountUseCase {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,32}$");

    private final UserAccountRepository accounts;
    private final GetMyAccountUseCase getMyAccount;

    public UpdateMyAccountUseCase(UserAccountRepository accounts, GetMyAccountUseCase getMyAccount) {
        this.accounts = accounts;
        this.getMyAccount = getMyAccount;
    }

    @Transactional
    public UserAccount execute(UUID userId, UpdateMyAccountRequest req) {
        UserAccount account = getMyAccount.execute(userId);

        if (req.fullName() != null) {
            String v = req.fullName().trim();
            account.setFullName(v.isBlank() ? null : v);
        }

        if (req.username() != null) {
            String candidate = req.username().trim();
            if (!USERNAME_PATTERN.matcher(candidate).matches()) {
                throw new InvalidUsernameException("Username must match " + USERNAME_PATTERN.pattern());
            }
            if (accounts.existsByUsernameAndUserIdNot(candidate, userId)) {
                throw new UsernameAlreadyInUseException(candidate);
            }
            account.setUsername(candidate);
        }

        if (req.locale() != null) {
            account.setLocale(LocaleUtil.normalizeOrThrow(req.locale()));
        }

        if (req.timezone() != null) {
            account.setTimezone(TimezoneUtil.normalizeOrThrow(req.timezone()));
        }

        account.setStatus(account.isAccountComplete() ? AccountStatus.ACTIVE : AccountStatus.INACTIVE);

        return accounts.save(account);
    }
}

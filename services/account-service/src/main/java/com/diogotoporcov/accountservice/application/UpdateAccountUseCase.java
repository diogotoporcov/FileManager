package com.diogotoporcov.accountservice.application;

import com.diogotoporcov.accountservice.api.dto.UpdateAccountRequest;
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
public class UpdateAccountUseCase {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,32}$");

    private final UserAccountRepository accounts;
    private final GetAccountUseCase getMyAccount;

    public UpdateAccountUseCase(UserAccountRepository accounts, GetAccountUseCase getAccount) {
        this.accounts = accounts;
        this.getMyAccount = getAccount;
    }

    @Transactional
    public UserAccount execute(UUID userId, UpdateAccountRequest req) {
        UserAccount account = getMyAccount.execute(userId);

        if (req.fullName() != null) {
            String value = req.fullName().trim();
            account.setFullName(value.isBlank() ? null : value);
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

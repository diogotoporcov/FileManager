package com.diogotoporcov.accountservice.profile;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class UsernameGenerator {

    private static final SecureRandom RNG = new SecureRandom();
    private static final char[] ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    public String generate() {
        String nano = NanoIdUtils.randomNanoId(RNG, ALPHABET, 10);
        return "user_" + nano;
    }
}

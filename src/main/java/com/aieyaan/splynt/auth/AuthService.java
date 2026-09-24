package com.aieyaan.splynt.auth;

import com.aieyaan.splynt.auth.dto.LoginRequest;
import com.aieyaan.splynt.auth.dto.LoginResponse;
import com.aieyaan.splynt.auth.dto.RegisterRequest;
import com.aieyaan.splynt.auth.dto.RegisterResponse;
import com.aieyaan.splynt.auth.exception.DuplicateEmailException;
import com.aieyaan.splynt.auth.exception.DuplicateOrganizationSlugException;
import com.aieyaan.splynt.auth.exception.InvalidCredentialsException;
import com.aieyaan.splynt.security.JwtTokenService;
import com.aieyaan.splynt.security.JwtTokenService.GeneratedToken;
import com.aieyaan.splynt.tenant.AppUser;
import com.aieyaan.splynt.tenant.AppUserRepository;
import com.aieyaan.splynt.tenant.MembershipRole;
import com.aieyaan.splynt.tenant.Organization;
import com.aieyaan.splynt.tenant.OrganizationMembership;
import com.aieyaan.splynt.tenant.OrganizationMembershipRepository;
import com.aieyaan.splynt.tenant.OrganizationRepository;
import com.aieyaan.splynt.tenant.Store;
import com.aieyaan.splynt.tenant.StoreRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final OrganizationRepository organizationRepository;
    private final StoreRepository storeRepository;
    private final OrganizationMembershipRepository
            membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(
            AppUserRepository appUserRepository,
            OrganizationRepository organizationRepository,
            StoreRepository storeRepository,
            OrganizationMembershipRepository membershipRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService
    ) {
        this.appUserRepository = appUserRepository;
        this.organizationRepository = organizationRepository;
        this.storeRepository = storeRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        String organizationSlug = request.organizationSlug()
                .trim()
                .toLowerCase(Locale.ROOT);

        String storeSlug = request.storeSlug()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (appUserRepository.existsByEmailIgnoreCase(
                normalizedEmail
        )) {
            throw new DuplicateEmailException(normalizedEmail);
        }

        if (organizationRepository.existsBySlug(
                organizationSlug
        )) {
            throw new DuplicateOrganizationSlugException(
                    organizationSlug
            );
        }

        String passwordHash =
                passwordEncoder.encode(request.password());

        AppUser user = appUserRepository.save(
                new AppUser(
                        normalizedEmail,
                        passwordHash,
                        request.firstName(),
                        request.lastName()
                )
        );

        Organization organization =
                organizationRepository.save(
                        new Organization(
                                request.organizationName(),
                                organizationSlug
                        )
                );

        Store store = storeRepository.save(
                new Store(
                        organization,
                        request.storeName(),
                        storeSlug
                )
        );

        OrganizationMembership membership =
                membershipRepository.save(
                        new OrganizationMembership(
                                organization,
                                user,
                                MembershipRole.OWNER
                        )
                );

        membershipRepository.flush();

        return new RegisterResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                organization.getId(),
                organization.getName(),
                organization.getSlug(),
                store.getId(),
                store.getName(),
                store.getSlug(),
                membership.getRole()
        );
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        AppUser user = appUserRepository
                .findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.isEnabled()) {
            throw new InvalidCredentialsException();
        }

        boolean passwordMatches = passwordEncoder.matches(
                request.password(),
                user.getPasswordHash()
        );

        if (!passwordMatches) {
            throw new InvalidCredentialsException();
        }

        user.recordSuccessfulLogin();

        GeneratedToken generatedToken =
                jwtTokenService.generateAccessToken(user);

        return new LoginResponse(
                generatedToken.value(),
                "Bearer",
                generatedToken.expiresInSeconds(),
                generatedToken.expiresAt(),
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName()
        );
    }

    private String normalizeEmail(String email) {
        return email
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
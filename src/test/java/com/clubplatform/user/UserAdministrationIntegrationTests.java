package com.clubplatform.user;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.clubplatform.club.entity.Club;
import com.clubplatform.club.repository.ClubManagerRepository;
import com.clubplatform.club.repository.ClubRepository;
import com.clubplatform.club.service.ClubManagerAdministrationService;
import com.clubplatform.club.service.ClubPermissionService;
import com.clubplatform.global.security.AuthenticatedUser;
import com.clubplatform.support.PostgreSqlTestConfig;
import com.clubplatform.user.service.UserAccountService;
import com.clubplatform.user.service.UserAdministrationService;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgreSqlTestConfig.class)
class UserAdministrationIntegrationTests {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserAccountService accounts;
    @Autowired private UserAdministrationService users;
    @Autowired private ClubManagerAdministrationService managerAdmin;
    @Autowired private ClubRepository clubs;
    @Autowired private ClubManagerRepository managers;
    @Autowired private ClubPermissionService permissions;
    private long owner;
    private long admin;
    private long member;
    private long clubId;
    private long otherClubId;

    @BeforeEach
    void prepare() {
        jdbc.update("DELETE FROM club_managers");
        jdbc.update("DELETE FROM clubs");
        jdbc.update("DELETE FROM users");
        owner = accounts.synchronize42Profile(101, "owner", "Owner").id();
        admin = accounts.synchronize42Profile(102, "admin", "Admin").id();
        member = accounts.synchronize42Profile(103, "alpha_member", "Member").id();
        jdbc.update("UPDATE users SET role='SUPER_ADMIN' WHERE id=?", owner);
        jdbc.update("UPDATE users SET role='ADMIN' WHERE id=?", admin);
        clubId = clubs.saveAndFlush(new Club("A", "Summary", "Description")).getId();
        otherClubId = clubs.saveAndFlush(new Club("B", "Summary", "Description")).getId();
    }

    @Test
    void searchSeparatesRoleVisibilityAndFiltersWithStablePagination() throws Exception {
        mvc.perform(get("/api/v1/admin/users").session(session(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].id").value(owner))
                .andExpect(jsonPath("$.content[0].role").doesNotExist())
                .andExpect(jsonPath("$.content[0].intraId").doesNotExist());
        mvc.perform(get("/api/v1/admin/users").session(session(owner)).param("role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(admin))
                .andExpect(jsonPath("$.content[0].role").value("ADMIN"));
        mvc.perform(get("/api/v1/admin/users").session(session(admin)).param("role", "ADMIN"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        get("/api/v1/admin/users")
                                .session(session(owner))
                                .param("intraLogin", "  ALPHA  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(member));
        mvc.perform(
                        get("/api/v1/admin/users")
                                .session(session(owner))
                                .param("role", "ADMIN")
                                .param("intraLogin", "alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(
                        get("/api/v1/admin/users")
                                .session(session(owner))
                                .param("page", "1")
                                .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content[0].id").value(admin));
        mvc.perform(get("/api/v1/admin/users").session(session(owner)).param("page", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void searchTreatsSqlWildcardsAsLiteralCharacters() throws Exception {
        for (String query : List.of("%", "_", "\\")) {
            mvc.perform(
                            get("/api/v1/admin/users")
                                    .session(session(owner))
                                    .param("intraLogin", query))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(query.equals("_") ? 1 : 0));
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "intraLogin=",
                "intraLogin=   ",
                "role=USER",
                "role=",
                "page=-1",
                "page=x",
                "size=0",
                "size=101"
            })
    void invalidSearchReturns400(String input) throws Exception {
        var pair = input.split("=", -1);
        mvc.perform(get("/api/v1/admin/users").session(session(owner)).param(pair[0], pair[1]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "GET_USERS",
                "PUT_ADMIN",
                "DELETE_ADMIN",
                "GET_MANAGERS",
                "PUT_MANAGER",
                "DELETE_MANAGER"
            })
    void allEndpointsEnforceAuthenticationAndRole(String operation) throws Exception {
        mvc.perform(request(operation).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(request(operation).session(session(member)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(request(operation).session(session(admin)).with(csrf()))
                .andExpect(
                        operation.equals("GET_USERS") ? status().isOk() : status().isForbidden());
        mvc.perform(request(operation).session(session(owner)).with(csrf()))
                .andExpect(operation.startsWith("GET") ? status().isOk() : status().isNoContent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT_ADMIN", "DELETE_ADMIN", "PUT_MANAGER", "DELETE_MANAGER"})
    void changesRequireCsrf(String operation) throws Exception {
        mvc.perform(request(operation).session(session(owner))).andExpect(status().isForbidden());
        mvc.perform(request(operation).session(session(owner)).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
        assertThat(accounts.findIdentity(member).orElseThrow().role()).isEqualTo("USER");
        assertThat(managers.count()).isZero();
    }

    @Test
    void roleChangesAreIdempotentProtectSuperAdminAndRefreshExistingSessions() throws Exception {
        var targetSession = session(member);
        var ownerSession = session(owner);
        managerAdmin.change(owner, clubId, member, true);
        for (int i = 0; i < 2; i++) {
            mvc.perform(
                            put("/api/v1/admin/users/{id}/admin-role", member)
                                    .session(ownerSession)
                                    .with(csrf()))
                    .andExpect(status().isNoContent());
        }
        mvc.perform(get("/api/v1/admin/users").session(targetSession)).andExpect(status().isOk());
        for (int i = 0; i < 2; i++) {
            mvc.perform(
                            delete("/api/v1/admin/users/{id}/admin-role", member)
                                    .session(ownerSession)
                                    .with(csrf()))
                    .andExpect(status().isNoContent());
        }
        mvc.perform(get("/api/v1/admin/users").session(targetSession))
                .andExpect(status().isForbidden());
        assertThat(accounts.findIdentity(member).orElseThrow().role()).isEqualTo("USER");
        assertThat(managers.existsByUserIdAndClubId(member, clubId)).isTrue();
        for (var request :
                List.of(
                        put("/api/v1/admin/users/{id}/admin-role", owner),
                        delete("/api/v1/admin/users/{id}/admin-role", owner))) {
            mvc.perform(request.session(ownerSession).with(csrf()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SUPER_ADMIN_ROLE_PROTECTED"));
        }
        // 최고 관리자 역할을 잃은 기존 세션도 다음 요청부터 권한 관리에 접근할 수 없다.
        jdbc.update("UPDATE users SET role='ADMIN' WHERE id=?", owner);
        mvc.perform(
                        put("/api/v1/admin/users/{id}/admin-role", member)
                                .session(ownerSession)
                                .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerChangesStayWithinTargetClubAndRevocationImmediatelyRemovesWritingPermission()
            throws Exception {
        String path = "/api/v1/admin/clubs/{club}/managers/{user}";
        for (int i = 0; i < 2; i++) {
            mvc.perform(put(path, clubId, member).session(session(owner)).with(csrf()))
                    .andExpect(status().isNoContent());
        }
        managerAdmin.change(owner, otherClubId, member, true);
        permissions.requireManager(member, clubId);
        mvc.perform(get("/api/v1/admin/clubs/{club}/managers", clubId).session(session(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(member))
                .andExpect(jsonPath("$.content[0].role").doesNotExist());
        for (int i = 0; i < 2; i++) {
            mvc.perform(delete(path, clubId, member).session(session(owner)).with(csrf()))
                    .andExpect(status().isNoContent());
        }
        assertThatThrownBy(() -> permissions.requireManager(member, clubId))
                .isInstanceOf(AccessDeniedException.class);
        permissions.requireManager(member, otherClubId);
        assertThat(accounts.findIdentity(member)).isPresent();
        assertThat(managers.count()).isEqualTo(1);
        // 운영 상태와 관계없이 회장 관계는 관리하되 작성 권한은 ACTIVE일 때만 허용한다.
        jdbc.update("UPDATE clubs SET operation_status='ARCHIVED' WHERE id=?", clubId);
        managerAdmin.change(owner, clubId, member, true);
        assertThatThrownBy(() -> permissions.requireManager(member, clubId))
                .isInstanceOf(AccessDeniedException.class);
        managerAdmin.change(owner, clubId, member, false);
    }

    @Test
    void missingTargetsAndInvalidIdsAreRejectedWithoutPartialChanges() throws Exception {
        for (var request :
                List.of(
                        put("/api/v1/admin/users/999999/admin-role"),
                        delete("/api/v1/admin/users/999999/admin-role"),
                        get("/api/v1/admin/clubs/999999/managers"),
                        put("/api/v1/admin/clubs/999999/managers/{id}", member),
                        delete("/api/v1/admin/clubs/999999/managers/{id}", member),
                        put("/api/v1/admin/clubs/{id}/managers/999999", clubId),
                        delete("/api/v1/admin/clubs/{id}/managers/999999", clubId))) {
            mvc.perform(request.session(session(owner)).with(csrf()))
                    .andExpect(status().isNotFound());
        }
        for (var request :
                List.of(
                        put("/api/v1/admin/users/0/admin-role"),
                        delete("/api/v1/admin/users/-1/admin-role"),
                        get("/api/v1/admin/clubs/0/managers"),
                        put("/api/v1/admin/clubs/{id}/managers/0", clubId),
                        get("/api/v1/admin/clubs/{id}/managers", clubId).param("size", "101"))) {
            mvc.perform(request.session(session(owner)).with(csrf()))
                    .andExpect(status().isBadRequest());
        }
        assertThat(managers.count()).isZero();
    }

    @Test
    void simultaneousGrantsAndRevocationsKeepOneRelationshipAndValidRole() throws Exception {
        concurrently(() -> managerAdmin.change(owner, clubId, member, true));
        assertThat(managers.count()).isEqualTo(1);
        concurrently(() -> users.changeAdminRole(owner, member, true));
        assertThat(accounts.findIdentity(member).orElseThrow().role()).isEqualTo("ADMIN");
        concurrently(() -> managerAdmin.change(owner, clubId, member, false));
        assertThat(managers.count()).isZero();
        concurrently(() -> users.changeAdminRole(owner, member, false));
        assertThat(accounts.findIdentity(member).orElseThrow().role()).isEqualTo("USER");
    }

    private void concurrently(Runnable action) throws Exception {
        var barrier = new CyclicBarrier(2);
        Runnable task =
                () -> {
                    try {
                        barrier.await(10, TimeUnit.SECONDS);
                        action.run();
                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                };
        CompletableFuture.allOf(CompletableFuture.runAsync(task), CompletableFuture.runAsync(task))
                .get(20, TimeUnit.SECONDS);
    }

    private MockHttpServletRequestBuilder request(String operation) {
        return switch (operation) {
            case "GET_USERS" -> get("/api/v1/admin/users");
            case "PUT_ADMIN" -> put("/api/v1/admin/users/{id}/admin-role", member);
            case "DELETE_ADMIN" -> delete("/api/v1/admin/users/{id}/admin-role", member);
            case "GET_MANAGERS" -> get("/api/v1/admin/clubs/{id}/managers", clubId);
            case "PUT_MANAGER" -> put("/api/v1/admin/clubs/{club}/managers/{user}", clubId, member);
            case "DELETE_MANAGER" ->
                    delete("/api/v1/admin/clubs/{club}/managers/{user}", clubId, member);
            default -> throw new IllegalArgumentException(operation);
        };
    }

    private MockHttpSession session(long id) {
        String role = accounts.findIdentity(id).orElseThrow().role();
        var principal = new AuthenticatedUser(id, role);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()));
        var session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", context);
        return session;
    }
}

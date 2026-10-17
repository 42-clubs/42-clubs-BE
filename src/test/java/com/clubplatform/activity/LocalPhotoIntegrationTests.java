package com.clubplatform.activity;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.clubplatform.activity.entity.*;
import com.clubplatform.activity.repository.*;
import com.clubplatform.activity.storage.*;
import com.clubplatform.club.entity.Club;
import com.clubplatform.club.repository.ClubRepository;
import com.clubplatform.support.PostgreSqlTestConfig;
import com.clubplatform.user.service.UserAccountService;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgreSqlTestConfig.class)
@Transactional
class LocalPhotoIntegrationTests {
    static final Path directory;

    static {
        try {
            directory = Files.createTempDirectory("clubs-photo-test");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.photos.local-directory", directory::toString);
        registry.add("app.photos.mode", () -> "local");
    }

    @Autowired MockMvc mvc;
    @Autowired LocalPhotoStorage storage;
    @Autowired ActivityImageRepository images;
    @Autowired ActivityRepository activities;
    @Autowired ClubRepository clubs;
    @Autowired UserAccountService users;
    @Autowired ApplicationContext context;

    @Test
    void anonymousGetOnlyServesPublishedPhotosWithoutAwsClients() throws Exception {
        assertThat(context.getBeansOfType(software.amazon.awssdk.services.s3.S3Client.class))
                .isEmpty();
        String key = "activities/" + UUID.randomUUID() + ".png";
        byte[] bytes = {1, 2, 3};
        storage.put(key, bytes, "image/png");
        try {
            String url = "/api/v1/photos/" + key;
            mvc.perform(get(url)).andExpect(status().isNotFound());
            long user = users.synchronize42Profile(999900, "photo-user", "Photo").id();
            long club = clubs.saveAndFlush(new Club("Photos", "Summary", "Description")).getId();
            var now = Instant.now();
            var activity =
                    activities.saveAndFlush(
                            new Activity(
                                    club,
                                    user,
                                    "Photo",
                                    "Body",
                                    now.plusSeconds(7200),
                                    now.plusSeconds(3600),
                                    2,
                                    true,
                                    now));
            images.saveAndFlush(new ActivityImage(activity.getId(), key, 0, now));
            mvc.perform(get(url))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("image/png"))
                    .andExpect(content().bytes(bytes));
            storage.delete(key);
            mvc.perform(get(url)).andExpect(status().isNotFound());
        } finally {
            storage.delete(key);
        }
    }
}

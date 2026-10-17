// Uses installed FE source unchanged. Only OAuth provider identity is a fixture;
// application APIs, HTTP transport, database, CSRF and photo storage are real.
import assert from "node:assert/strict";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { resolve, join } from "node:path";
import { pathToFileURL } from "node:url";

const [frontendPath, base] = process.argv.slice(2);
assert(frontendPath && /^http:\/\/127\.0\.0\.1:\d+$/.test(base));
const frontend = resolve(frontendPath);
const cache = await mkdtemp(join(tmpdir(), "clubs-fe-vite-"));
const { createServer } = await import(
    pathToFileURL(join(frontend, "node_modules/vite/dist/node/index.js"))
);
const vite = await createServer({
    root: frontend,
    configFile: false,
    cacheDir: cache,
    server: { middlewareMode: true, watch: null },
    appType: "custom",
    optimizeDeps: { noDiscovery: true, include: [] },
});
let group = "initialization";
let passed = 0;
let requests = 0;
try {
    const load = (path) => vite.ssrLoadModule(`/src/${path}.ts`);
    const { http, clearCsrf } = await load("shared/api/http");
    const { clubApi } = await load("features/clubs/api");
    const { gatheringApi } = await load("features/gatherings/api");
    const { noticeApi } = await load("features/notices/api");
    const { authApi, accessApi } = await load("features/auth/api");
    const { profileApi } = await load("features/profile/api");
    const { dateInSeoul, seoulDateTime } = await load("shared/date");
    http.defaults.baseURL = `${base}/api/v1`;
    const cookieJars = new Map();
    let actor = "anonymous";
    function remember(headers) {
        const values = headers.getSetCookie
            ? headers.getSetCookie()
            : (headers["set-cookie"] ?? []);
        for (const cookie of values) {
            if (cookie.startsWith("JSESSIONID="))
                cookieJars.set(actor, cookie.split(";")[0]);
        }
    }
    // Node has no browser cookie jar. Supply only this transport facility; keep
    // the FE CSRF interceptor and actual Axios HTTP adapter unchanged.
    http.interceptors.request.use((config) => {
        requests++;
        const cookie = cookieJars.get(actor);
        if (cookie) config.headers.set("Cookie", cookie);
        return config;
    });
    http.interceptors.response.use((response) => {
        remember(response.headers);
        return response;
    });
    function as(name) {
        actor = name;
        clearCsrf();
    }
    async function fetchSession(path, options = {}) {
        const response = await fetch(new URL(path, base), {
            ...options,
            redirect: "manual",
            headers: {
                ...options.headers,
                ...(cookieJars.get(actor)
                    ? { Cookie: cookieJars.get(actor) }
                    : {}),
            },
        });
        remember(response.headers);
        return response;
    }
    async function login(name, code) {
        as(name);
        const start = await fetchSession("/oauth2/authorization/42");
        assert.equal(start.status, 302);
        const state = new URL(start.headers.get("location")).searchParams.get(
            "state",
        );
        const callback = await fetchSession(
            `/login/oauth2/code/42?${new URLSearchParams({ code, state })}`,
        );
        assert.equal(callback.status, 302);
        assert.equal(
            callback.headers.get("location"),
            "http://localhost:5176/#/auth/callback",
        );
        clearCsrf();
        const me = await authApi.me();
        assert.equal(me.intraLogin, name);
        return me;
    }
    async function denied(status, operation) {
        await assert.rejects(
            operation,
            (error) => error.response?.status === status,
        );
    }
    function done(name) {
        passed++;
        console.log(`FE-LIVE PASS ${name}`);
    }

    group = "public pagination and authentication";
    assert.equal(await authApi.me(), null);
    const page = await clubApi.listPublic();
    assert.equal(page.content.length, 20);
    assert.equal(page.totalElements, 105);
    assert.equal((await clubApi.detailPublic("1")).memberCount, 0);
    assert.equal((await clubApi.detailPublic("1")).recentActivity, null);
    assert.equal((await clubApi.allPublic()).length, 105);
    assert.equal((await accessApi.clubs()).length, 106);
    assert.equal(
        (await clubApi.detailPublic("106")).operationStatus,
        "ARCHIVED",
    );
    await denied(404, () => clubApi.detailPublic("999999"));
    await denied(401, () => profileApi.clubs());
    done(group);

    group = "OAuth fixture sessions and current roles";
    const users = {};
    for (const [i, name] of [
        "operator",
        "admin",
        "manager",
        "member",
        "outsider",
    ].entries()) {
        users[name] = await login(name, String(9001 + i));
    }
    assert.equal(users.operator.role, "ADMIN");
    assert.equal(users.admin.role, "ADMIN");
    done(group);

    group = "role management and membership are independent";
    as("operator");
    await accessApi.setManager("1", users.manager.id, true);
    assert.equal((await accessApi.managers("1"))[0].id, users.manager.id);
    // Public detail exposes only the approved display fields, without granting admin access.
    as("anonymous");
    assert.deepEqual((await clubApi.detailPublic("1")).managers, [
        { intraLogin: "manager", displayName: "manager" },
    ]);
    assert.equal((await clubApi.detailPublic("1")).memberCount, 1);
    assert.deepEqual((await clubApi.detailPublic("2")).managers, []);
    as("operator");
    await accessApi.setManager("1", users.member.id, true);
    as("anonymous");
    assert.deepEqual((await clubApi.detailPublic("1")).managers, [
        { intraLogin: "manager", displayName: "manager" },
        { intraLogin: "member", displayName: "member" },
    ]);
    assert.equal((await clubApi.detailPublic("1")).memberCount, 2);
    as("operator");
    await accessApi.setManager("1", users.member.id, false);
    as("anonymous");
    assert.deepEqual((await clubApi.detailPublic("1")).managers, [
        { intraLogin: "manager", displayName: "manager" },
    ]);
    as("operator");
    await accessApi.setOperator(users.outsider.id, true);
    as("outsider");
    assert.equal((await authApi.me()).role, "ADMIN");
    as("operator");
    await accessApi.setOperator(users.outsider.id, false);
    as("admin");
    await accessApi.setOperator(users.admin.id, false);
    assert.equal((await authApi.me()).role, "USER");
    await denied(403, () => accessApi.users({ page: 0 }));
    as("operator");
    await denied(409, () => accessApi.setOperator(users.operator.id, false));
    await accessApi.setOperator(users.admin.id, true);
    as("admin");
    assert(
        (await clubApi.searchUsers("member")).some(
            (user) => user.id === users.member.id,
        ),
    );
    assert.equal((await accessApi.managers("1"))[0].id, users.manager.id);
    await accessApi.setManager("1", users.member.id, true);
    await accessApi.setManager("1", users.member.id, false);
    const adminUsers = await accessApi.users({ role: "ADMIN", page: 0 });
    assert(adminUsers.content.every((user) => user.role === "ADMIN"));
    assert.equal(adminUsers.totalElements, 2);
    await clubApi.registerMember("1", users.member.id);
    await clubApi.registerMember("1", users.member.id);
    assert.equal(
        (await clubApi.membership(users.member.id)).activeClubCount,
        1,
    );
    await denied(403, () => clubApi.members("1"));
    as("manager");
    const permissions = await clubApi.permissions("1");
    assert.equal(permissions.canCreateActivity, true);
    assert.equal(permissions.canViewMembers, false);
    await denied(403, () => clubApi.members("1"));
    as("member");
    assert.equal((await clubApi.members("1")).length, 1);
    assert.equal((await clubApi.detailPublic("1")).memberCount, 2);
    as("operator");
    await accessApi.setManager("1", users.member.id, true);
    as("anonymous");
    const countedDetail = await clubApi.detailPublic("1");
    assert.equal(countedDetail.memberCount, 2);
    assert.equal(Object.hasOwn(countedDetail, "contact"), false);
    assert.equal(Object.hasOwn(countedDetail, "publicContact"), false);
    as("operator");
    await accessApi.setManager("1", users.member.id, false);
    done(group);

    group = "recent activity public counts and organizer attendance";
    as("anonymous");
    const recent = (await clubApi.detailPublic("106")).recentActivity;
    assert.equal(recent.title, "Recorded past activity");
    assert(new Date(recent.startsAt).getTime() <= Date.now());
    assert.equal(recent.currentParticipantCount, 2);
    assert.equal(recent.presentParticipantCount, 1);
    assert.deepEqual(Object.keys(recent).sort(), [
        "currentParticipantCount", "id", "presentParticipantCount", "startsAt", "title",
    ]);
    const pastId = String(recent.id);
    assert.equal((await clubApi.activity("106", pastId)).presentParticipantCount, 1);
    assert.equal((await clubApi.activities("106"))[0].presentParticipantCount, 1);
    await denied(401, () => clubApi.participants("106", pastId));
    as("manager");
    await clubApi.attendance("106", pastId, users.manager.id, "PRESENT");
    as("anonymous");
    assert.equal((await clubApi.detailPublic("106")).recentActivity.presentParticipantCount, 2);
    as("manager");
    await clubApi.attendance("106", pastId, users.manager.id, "UNCHECKED");
    await clubApi.attendance("106", pastId, users.member.id, "UNCHECKED");
    as("anonymous");
    assert.equal((await clubApi.detailPublic("106")).recentActivity.presentParticipantCount, 0);
    done(group);

    group = "notices visibility and resource nesting";
    as("admin");
    const announcement = await noticeApi.create({
        title: "Live announcement",
        content: "Operator content",
    });
    as("manager");
    const publicNotice = await clubApi.createNotice("1", {
        title: "Public",
        content: "Public content",
        visibility: "PUBLIC",
    });
    const privateNotice = await clubApi.createNotice("1", {
        title: "Private",
        content: "Private content",
        visibility: "MEMBERS_ONLY",
    });
    as("anonymous");
    assert.equal(
        (await noticeApi.announcement(String(announcement.id))).title,
        "Live announcement",
    );
    assert.equal((await noticeApi.announcements()).content.length, 1);
    assert.equal((await noticeApi.clubNotices("1")).content.length, 1);
    assert.equal(
        (await noticeApi.clubNotice("1", String(publicNotice.id))).title,
        "Public",
    );
    await denied(404, () =>
        noticeApi.clubNotice("1", String(privateNotice.id)),
    );
    await denied(404, () => noticeApi.clubNotice("2", String(publicNotice.id)));
    as("member");
    assert.equal((await noticeApi.clubNotices("1")).content.length, 2);
    done(group);

    group = "activity multipart and real photo URL";
    const instant = (hours) =>
        new Date(Date.now() + hours * 3600000).toISOString();
    const input = {
        title: "Live activity",
        content: "Real multipart",
        startsAt: instant(72),
        recruitmentClosesAt: instant(48),
        capacity: 3,
        allowExternalParticipants: true,
    };
    // Valid one-pixel PNG; the backend decodes and re-encodes this upload.
    const png = Buffer.from(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aX1sAAAAASUVORK5CYII=",
        "base64",
    );
    as("manager");
    const activity = await clubApi.createActivity("1", input, [
        new File([png], "live.png", { type: "image/png" }),
    ]);
    const aid = String(activity.id);
    const detail = await clubApi.activity("1", aid);
    assert.equal(detail.allowExternalParticipants, true);
    assert.equal(detail.currentParticipantCount, 1);
    assert.equal(detail.presentParticipantCount, 0);
    assert.equal(detail.images.length, 1);
    const photo = await fetch(detail.images[0].url);
    assert.equal(photo.status, 200);
    assert.match(photo.headers.get("content-type"), /^image\//);
    assert((await photo.arrayBuffer()).byteLength > 0);
    assert.equal((await clubApi.activities("1")).length, 1);
    await denied(404, () => clubApi.activity("2", aid));
    await denied(409, () => clubApi.cancelActivity("1", aid));
    await denied(403, () => clubApi.createActivity("2", input, []));
    await denied(400, () =>
        clubApi.createActivity("1", { ...input, title: "" }, []),
    );
    done(group);

    group = "activity join cancel rejoin and organizer attendance";
    as("member");
    await clubApi.joinActivity("1", aid);
    assert.equal((await clubApi.myActivity("1", aid)).status, "CONFIRMED");
    await denied(409, () => clubApi.joinActivity("1", aid));
    await clubApi.cancelActivity("1", aid);
    assert.equal((await clubApi.myActivity("1", aid)).status, "CANCELLED");
    await clubApi.joinActivity("1", aid);
    await denied(403, () => clubApi.participants("1", aid));
    as("manager");
    assert.equal((await clubApi.participants("1", aid)).length, 2);
    await clubApi.attendance("1", aid, users.member.id, "PRESENT");
    assert.equal((await clubApi.activity("1", aid)).presentParticipantCount, 1);
    assert.equal((await clubApi.detailPublic("1")).recentActivity, null);
    as("member");
    assert.equal(
        (await clubApi.myActivity("1", aid)).attendanceStatus,
        "PRESENT",
    );
    await denied(409, () => clubApi.cancelActivity("1", aid));
    as("manager");
    await clubApi.attendance("1", aid, users.member.id, "UNCHECKED");
    as("outsider");
    assert.equal((await authApi.me()).role, "USER");
    await clubApi.joinActivity("1", aid);
    as("admin");
    await denied(409, () => clubApi.joinActivity("1", aid));
    await denied(403, () => clubApi.participants("1", aid));
    done(group);

    group = "immutable activity policy and member-only participation";
    const memberOnly = { ...input, allowExternalParticipants: false };
    as("manager");
    await denied(403, () => clubApi.createActivity("1", memberOnly, []));
    const missingPolicy = { ...input };
    delete missingPolicy.allowExternalParticipants;
    await denied(400, () => clubApi.createActivity("1", missingPolicy, []));
    as("admin");
    await clubApi.registerMember("1", users.manager.id);
    as("manager");
    const restricted = await clubApi.createActivity("1", memberOnly, []);
    const rid = String(restricted.id);
    assert.equal((await clubApi.activity("1", rid)).allowExternalParticipants, false);
    await denied(405, () => http.patch(`/clubs/1/activities/${rid}`, {
        allowExternalParticipants: true,
    }));
    as("outsider");
    await denied(403, () => clubApi.joinActivity("1", rid));
    as("admin");
    await denied(403, () => clubApi.joinActivity("1", rid));
    as("member");
    await clubApi.joinActivity("1", rid);
    await clubApi.cancelActivity("1", rid);
    await clubApi.joinActivity("1", rid);
    as("anonymous");
    const publicRestricted = await clubApi.activity("1", rid);
    assert.equal(publicRestricted.allowExternalParticipants, false);
    assert.equal(publicRestricted.currentParticipantCount, 2);
    assert.equal((await clubApi.activities("1")).find((item) => item.id === restricted.id)
        .allowExternalParticipants, false);
    done(group);

    group = "gathering first and next session independent participation";
    as("manager");
    const sessionInput = {
        title: "First",
        startsAt: instant(80),
        endsAt: instant(81),
        recruitmentClosesAt: instant(50),
        location: "Live room",
        capacity: 3,
    };
    const created = await gatheringApi.createPublic({
        title: "Live gathering",
        content: "Live content",
        session: sessionInput,
    });
    const gid = String(created.gatheringId),
        sid = String(created.sessionId);
    const next = await gatheringApi.addSession(gid, {
        ...sessionInput,
        title: "Next",
        startsAt: instant(100),
        endsAt: instant(101),
    });
    const nextId = String(next.sessionId);
    assert.equal((await gatheringApi.allPublic()).length, 1);
    assert.equal(
        (await gatheringApi.detailPublic(gid)).title,
        "Live gathering",
    );
    assert.equal((await gatheringApi.sessions(gid)).length, 2);
    assert.equal((await gatheringApi.session(gid, sid)).title, "First");
    await denied(404, () => gatheringApi.session("999999", sid));
    as("member");
    await gatheringApi.joinSession(gid, sid);
    assert.equal((await gatheringApi.mySession(gid, nextId)).status, null);
    await gatheringApi.cancelSession(gid, sid);
    await gatheringApi.joinSession(gid, sid);
    await denied(409, () => gatheringApi.joinSession(gid, sid));
    await denied(403, () => gatheringApi.addSession(gid, sessionInput));
    await denied(403, () => gatheringApi.participants(gid, sid));
    as("manager");
    assert.equal((await gatheringApi.participants(gid, sid)).length, 2);
    await gatheringApi.attendance(gid, sid, users.member.id, "PRESENT");
    as("member");
    assert.equal(
        (await gatheringApi.mySession(gid, sid)).attendanceStatus,
        "PRESENT",
    );
    await denied(409, () => gatheringApi.cancelSession(gid, sid));
    done(group);

    group = "mypage and Seoul calendar source data";
    const clubs = await profileApi.clubs();
    assert.equal(clubs.length, 1);
    assert.equal(clubs[0].isMember, true);
    const history = await profileApi.activities();
    assert(
        history.some(
            (g) =>
                g.type === "CLUB" &&
                g.items.some((item) => item.activityId === activity.id),
        ),
    );
    assert(
        history.some(
            (g) =>
                g.type === "GATHERING" &&
                g.items.some((item) => item.sessionId === created.sessionId),
        ),
    );
    const gatherings = await profileApi.gatherings();
    assert.equal(gatherings.length, 1);
    assert.equal(gatherings[0].items.length, 1);
    assert.equal(dateInSeoul("2026-10-17T15:00:00Z"), "2026-10-18");
    assert.equal(
        seoulDateTime("2026-10-17T15:00:00Z"),
        "2026-10-18T00:00:00+09:00",
    );
    done(group);

    group = "CSRF and logout invalidate session";
    const missingCsrf = await fetchSession("/api/v1/auth/logout", {
        method: "POST",
    });
    assert.equal(missingCsrf.status, 403);
    const oldToken = (await http.get("/auth/csrf")).data;
    await authApi.logout();
    assert.equal(await authApi.me(), null);
    await denied(401, () => profileApi.activities());
    const fresh = (await http.get("/auth/csrf")).data;
    assert.notEqual(fresh.token, oldToken.token);
    // A pre-logout token is rejected against the new anonymous session.
    const stale = await fetchSession("/api/v1/auth/logout", {
        method: "POST",
        headers: { [oldToken.headerName]: oldToken.token },
    });
    assert.equal(stale.status, 403);
    done(group);
    console.log(
        `FE-LIVE SUMMARY groups=${passed} apiRequests=${requests} real42=false browser=false`,
    );
} catch (error) {
    // Axios errors include credentials in config; print only a safe diagnostic.
    console.error(
        `FE-LIVE FAIL ${group}: ${error.code ?? error.name ?? "Error"} status=${error.response?.status ?? "-"} ${error.response?.data?.code ?? ""}`,
    );
    if (!error.isAxiosError) console.error(error.message);
    process.exitCode = 1;
} finally {
    await vite.close();
    await rm(cache, { recursive: true, force: true });
}

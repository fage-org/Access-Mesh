import { afterEach, expect, it, vi } from "vitest";

vi.mock("vite-plugin-fake-server/client", () => ({
  defineFakeRoute: (routes: unknown) => routes
}));
afterEach(() => vi.unstubAllEnvs());

it("keeps the mock non-admin login identity when loading its menu", async () => {
  vi.stubEnv("VITE_MOCK_LOGIN", "true");
  vi.resetModules();
  const { default: routes } = await import("./login");
  if (!Array.isArray(routes))
    throw new Error("mock login routes must be an array");
  type Reply = { data: { sessionId: string; permissions: string[] } };
  type Respond = (input: {
    body?: { username: string };
    headers?: Record<string, string>;
  }) => Reply;
  const login = routes.find(
    route => route.url === "/api/access/auth/browser-login"
  )!.response as unknown as Respond;
  const menu = routes.find(route => route.url === "/api/access/auth/user-menu")!
    .response as unknown as Respond;
  const loggedIn = await login({ body: { username: "sec" } });
  const admin = await login({ body: { username: "admin" } });
  const loaded = await menu({
    headers: { "x-am-session": loggedIn.data.sessionId }
  });
  expect(loaded.data.permissions).toEqual(loggedIn.data.permissions);
  expect(loaded.data.permissions).not.toEqual(admin.data.permissions);
});

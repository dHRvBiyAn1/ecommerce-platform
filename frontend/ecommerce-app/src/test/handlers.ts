import { http, HttpResponse } from "msw";

export const handlers = [
  http.get("*/api/test", () => HttpResponse.json({ message: "MSW intercepted" })),
];

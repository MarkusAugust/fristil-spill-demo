import { createRouter } from "@tanstack/react-router"

import { routeTree } from "./routeTree.gen"

/**
 * Ruteren.
 *
 * Spillet har én side, så ruteren gjør lite her. Den er likevel med, siden
 * dette skal være en vanlig TanStack Start-app og ikke et oppsett laget for
 * å se enkelt ut i en demo.
 */
export function getRouter() {
  return createRouter({
    routeTree,
    defaultPreload: "intent",
    scrollRestoration: true,
  })
}

declare module "@tanstack/react-router" {
  interface Register {
    router: ReturnType<typeof getRouter>
  }
}

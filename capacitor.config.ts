import type { CapacitorConfig } from "@capacitor/cli";

const config: CapacitorConfig = {
  appId: "com.worlds.cinematicguess",
  appName: "Worlds",
  webDir: "dist",
  server: {
    androidScheme: "https",
  },
};

export default config;

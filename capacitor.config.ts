import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.artemkz147.neonapex',
  appName: 'Neon Apex Racing',
  webDir: 'dist',
  backgroundColor: '#060914',
  android: {
    allowMixedContent: false,
    captureInput: true,
    webContentsDebuggingEnabled: false
  }
};

export default config;

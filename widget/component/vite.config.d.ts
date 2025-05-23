declare const config: ({ command }: {
    command: any;
}) => Promise<import("vite").UserConfig>;
export default config;

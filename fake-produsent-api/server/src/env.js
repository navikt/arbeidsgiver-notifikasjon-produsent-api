function parseBoolean(value) {
    return ['true', 'T', '1'].includes(value)
}

const mockEnv = {
    ALWAYS_SUCCESSFUL_RESPONSE:
        parseBoolean(process.env.ALWAYS_SUCCESSFUL_RESPONSE)
};
export default mockEnv;

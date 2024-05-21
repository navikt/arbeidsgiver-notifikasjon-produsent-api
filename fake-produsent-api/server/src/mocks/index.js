import casual from "casual";
import successfulMocks from "./successfulMocks.js";
import mockEnv from "../env.js";

const mocks = {
    Int: () => casual.integer(0, 1000),
    String: () => casual.string,
    ISO8601DateTime: () => new Date().toISOString(),
    ...(mockEnv.ALWAYS_SUCCESSFUL_RESPONSE ? successfulMocks : {})
}

export default mocks;
const {
    AZURE_OPENID_CONFIG_TOKEN_ENDPOINT = "",
    AZURE_APP_TENANT_ID = "",
    AZURE_APP_CLIENT_ID = "",
    AZURE_APP_CLIENT_SECRET = "",
    NAIS_CLUSTER_NAME = 'local',
} = process.env;

export const fetchAccessToken = async () => {
    if (NAIS_CLUSTER_NAME === 'local') {
        return "dummy-token";
    }

    const body = new URLSearchParams();
    body.append("tenant", AZURE_APP_TENANT_ID);
    body.append("client_id", AZURE_APP_CLIENT_ID);
    body.append("scope", "api://dev-gcp.fager.notifikasjon-produsent-api/.default");
    body.append("client_secret", AZURE_APP_CLIENT_SECRET);
    body.append("grant_type", "client_credentials");

    const response = await fetch(AZURE_OPENID_CONFIG_TOKEN_ENDPOINT, {
        headers: {"Content-Type": "application/x-www-form-urlencoded"},
        method: "POST",
        body: body,
    });

    const json = await response.json();
    return json.access_token;
};
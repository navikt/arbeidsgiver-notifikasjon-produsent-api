import React from 'react';
import { Button, Textarea } from "@navikt/ds-react";
import {gql, useLazyQuery} from "@apollo/client";

const defaultQueryString = `query init {whoami}`
const initQuery = gql`${defaultQueryString}`

export const AdHoc: React.FunctionComponent = () => {
    const [executeQuery, { loading, error: apolloError, data }] = useLazyQuery(initQuery);

    const [variables, setVariables] = React.useState("");
    const [query, setQuery] = React.useState(defaultQueryString);
    const [error, setError] = React.useState<string | null>(null);
    const handleRunQuery =  async () => {
        try {
            const parsedVariables = variables !== "" ? JSON.parse(variables) : {};
            if (query === "") {
                setError("Skriv inn en query ")
            }
            await executeQuery({
                variables: parsedVariables,
                query: gql`${query}`,
            });
            setError(null)
        } catch (error) {
            setError(`Feilet parsing. Skriv i JSON-format {"{\"key\":\"value\"}"} ${error}`)
        }
    };

    return (
        <>
            <div style={{
                display: "flex",
                flexDirection: "column",
                gap: "16px"

            }}>
                <Textarea
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    label="Skriv inn GraphQL-query her"
                />
                <Textarea
                    value={variables}
                    onChange={(e) => setVariables(e.target.value)}
                    label="Fyll inn variablene her (JSON-format)"
                />
                <Button onClick={handleRunQuery} disabled={loading}>
                    Kjør Query
                </Button>
            </div>
            <div>
                {loading && <p>Laster...</p>}
                {apolloError && <p>Error: {apolloError.message}</p>}
                {error && <p>{error}</p>}
                {data && <pre>{JSON.stringify(data, null, 2)}</pre>}
            </div>
        </>
    );
};
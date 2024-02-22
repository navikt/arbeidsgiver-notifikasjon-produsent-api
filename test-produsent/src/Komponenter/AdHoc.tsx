import React, { useState } from 'react';
import { Button, Textarea } from "@navikt/ds-react";
import {gql, useLazyQuery} from "@apollo/client";

export const AdHoc: React.FunctionComponent = () => {
    const [query, setQuery] = useState<string>('query init{whoami}');
    const [variables, setVariables] = useState<string>('');
    const [executeQuery, { loading, error, data }] = useLazyQuery(gql`${query}`); // Legg til din GraphQL spørring her

    const handleRunQuery = () => {
        try {
            const parsedVariables = variables !== "" ? JSON.parse(variables) : {};
            if (query === "") {
                console.error("Query må fylles ut");
                return;
            }
            executeQuery({
                variables: parsedVariables,
                query: gql`${query}`,
            });
        } catch (error) {
            console.error("Feilet parsing. Skriv i JSON-format \'{\"key\":\"value\"}\'", error);
        }
    };

    if (loading) return <p>Laster...</p>
    if (error) return <p>Error: {error.message}</p>

    return (
        <>
            <div>
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
                {data && <pre>{JSON.stringify(data, null, 2)}</pre>}
            </div>
        </>
    );
};
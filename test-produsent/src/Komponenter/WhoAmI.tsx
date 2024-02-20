import {gql, useQuery} from "@apollo/client";
import React from "react";
import {Query} from "../api/graphql-types.ts";

const WHO_AM_I = gql`
    query WhoAmI {
        whoami
    }
`

export const WhoAmI: React.FunctionComponent = () => {
    const {loading, error, data} = useQuery<Pick<Query, "whoami">>(WHO_AM_I)
    if (loading) return <p> Loading...</p>
    if (error) return <p>Error </p>
    if (data === undefined) return null
    return <pre>{JSON.stringify(data)}</pre>
}
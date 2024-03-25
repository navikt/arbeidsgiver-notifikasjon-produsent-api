import {gql, useLazyQuery} from "@apollo/client";
import {Search} from "@navikt/ds-react";
import {darcula} from "react-syntax-highlighter/dist/esm/styles/prism";
import SyntaxHighlighter from "react-syntax-highlighter/dist/cjs/prism";

const HENT_SAK = gql`
    query HentSak($saksid: ID!) {
        hentSak(id: $saksid) {
            ... on HentetSak {
                sak {
                    id,
                    tittel,
                    virksomhetsnummer,
                    grupperingsid,
                    merkelapp,
                    lenke
                    
                }
            }
        }
    }`

export const HentSak = () => {
    const [hentSak, {loading, error, data}] = useLazyQuery(HENT_SAK);

    const handleClick = (saksid: string) => {
        hentSak({variables: {saksid: saksid}})
    }

    if (loading) return <p>Laster...</p>
    if (error) return <p>Error: {error.message}</p>

    return (
        <div>
            <Search label="Søk alle NAV sine sider" variant="primary" onSearchClick={handleClick}/>
            {loading && <p>Laster...</p>}
            {error &&
                <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(error, null, 2)}</SyntaxHighlighter>}
            {data &&
                <SyntaxHighlighter language="json" style={darcula}>{JSON.stringify(data, null, 2)}</SyntaxHighlighter>}
        </div>
    );
}
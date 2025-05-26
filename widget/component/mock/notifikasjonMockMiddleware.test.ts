import express, { Express } from 'express';
import request from 'supertest';
import { describe, it, expect, beforeAll } from 'vitest';
import { applyNotifikasjonMockMiddleware } from './notifikasjonMockMiddleware';

describe('applyNotifikasjonMockMiddleware', () => {
  let app: Express;

  beforeAll(async () => {
    app = express();
    app.use(express.json());
    await applyNotifikasjonMockMiddleware(app);
  });

  it('should respond with mocked notifikasjoner', async () => {
    const res = await request(app)
      .post('/graphql')
      .send({
        query: `
          query {
            notifikasjoner {
              notifikasjoner {
                __typename
                ... on Oppgave {
                  id
                  tekst
                  virksomhet { navn }
                }
                ... on Beskjed {
                  id
                  tekst
                  virksomhet { navn }
                }
                ... on Kalenderavtale {
                  id
                  tekst
                  virksomhet { navn }
                }
              }
              feilAltinn
              feilDigiSyfo
            }
          }
        `,
      })
      .set('Content-Type', 'application/json');

    expect(res.status).toBe(200);
    expect(res.body.data.notifikasjoner).toHaveProperty('notifikasjoner');
    expect(res.body.data.notifikasjoner.notifikasjoner.length).toBeGreaterThan(0);
    expect(res.body.data.notifikasjoner.notifikasjoner[0]).toHaveProperty('id');
    expect(res.body.data.notifikasjoner.notifikasjoner[0]).toHaveProperty('__typename');
  });
});

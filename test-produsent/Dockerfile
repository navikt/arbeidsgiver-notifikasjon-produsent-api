FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/node:22-slim

ENV NODE_ENV=production
ENV NPM_CONFIG_CACHE=/tmp

WORKDIR /usr/src/app
COPY build/ build/
COPY server-deploy/ server/

WORKDIR /usr/src/app/server

EXPOSE 8080
CMD ["server.js"]
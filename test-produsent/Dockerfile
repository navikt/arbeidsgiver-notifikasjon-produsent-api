FROM gcr.io/distroless/nodejs20-debian11

WORKDIR /usr/src/app
COPY build/ build/
COPY server/ server/

WORKDIR /usr/src/app/server

EXPOSE 8080
CMD ["server.js"]
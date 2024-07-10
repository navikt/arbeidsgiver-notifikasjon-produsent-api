FROM gcr.io/distroless/nodejs22-debian12

WORKDIR /usr/src/app
COPY build/ build/
COPY server/ server/

WORKDIR /usr/src/app/server

EXPOSE 8080
CMD ["server.js"]
FROM gcr.io/distroless/nodejs22-debian12

# Healtcheck lokalt/test
COPY --from=busybox:stable-musl /bin/wget /usr/bin/wget

WORKDIR /usr/src/app/server
COPY server/ .

EXPOSE 8080
CMD ["server.js"]

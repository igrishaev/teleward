FROM alpine
RUN addgroup -g 2000 teleward
RUN adduser -u 2000 -G teleward -s /bin/sh -D teleward
COPY --chown=teleward:teleward ./builds/teleward-Linux-x86_64 /teleward/teleward
RUN chmod +x /teleward/teleward
USER 2000
WORKDIR /teleward
ENTRYPOINT ["teleward"]

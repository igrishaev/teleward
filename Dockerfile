FROM debian:stable-slim
RUN useradd -s /bin/bash -d /home/teleward/ -m teleward
COPY --chown=teleward:teleward ./builds/teleward-Linux-x86_64 /home/teleward/teleward
USER teleward
WORKDIR /home/teleward
ENTRYPOINT ["/home/teleward/teleward"]

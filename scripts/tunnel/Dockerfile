FROM amazon/aws-cli:2.35.14@sha256:b9c4a4fc34cb699c90776cb1c7d6d7152d95ca5b3f856adf908d329b2c9f7222

RUN curl "https://s3.amazonaws.com/session-manager-downloads/plugin/latest/linux_arm64/session-manager-plugin.rpm" -o "session-manager-plugin.rpm"
RUN yum install -y session-manager-plugin.rpm openssh-clients nc jq socat

COPY port-forward.sh .

EXPOSE 1111
HEALTHCHECK --interval=1s --start-period=30s CMD ["nc", "-z", "localhost", "1111"]
ENTRYPOINT ["./port-forward.sh"]

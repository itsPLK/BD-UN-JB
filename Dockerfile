FROM eclipse-temurin:8-jdk

# Install build dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    git \
    pkg-config \
    libbsd-dev \
    && rm -rf /var/lib/apt/lists/*

# Clone bdj-sdk (provides makefs, bdsigner, resources)
RUN git clone --depth 1 --recurse-submodules https://github.com/john-tornblom/bdj-sdk /opt/bdj-sdk

# Build makefs and place it in bdj-sdk host/bin (where Makefile expects it)
RUN cd /opt/bdj-sdk/host/src/makefs_termux \
    && make \
    && cp makefs /opt/bdj-sdk/host/bin/makefs \
    && chmod +x /opt/bdj-sdk/host/bin/makefs \
    && cp makefs /usr/local/bin/makefs \
    && chmod +x /usr/local/bin/makefs

# Copy Temurin JDK 8 to bdj-sdk expected location
RUN cp -r /opt/java/openjdk /opt/bdj-sdk/host/jdk8

# Create target/lib with required jars (rt.jar + bdj-sdk host libs)
RUN mkdir -p /opt/bdj-sdk/target/lib \
    && cp /opt/bdj-sdk/host/lib/*.jar /opt/bdj-sdk/target/lib/ \
    && cp /opt/bdj-sdk/host/jdk8/jre/lib/rt.jar /opt/bdj-sdk/target/lib/

# Set environment variables
ENV BDJSDK_HOME=/opt/bdj-sdk
ENV JAVA8_HOME=/opt/bdj-sdk/host/jdk8

WORKDIR /workspace

FROM jupyter/scipy-notebook:latest

USER root
RUN apt-get update && apt-get install -y graphviz openjdk-11-jdk
USER $NB_UID

# Install latest almond for Scala 2.13.11
RUN curl -Lo coursier https://git.io/coursier-cli && \
    chmod +x coursier && \
    ./coursier bootstrap \
      -r jitpack \
      -i user -I user:sh.almond:scala-kernel-api_2.13.11:0.13.11 \
      sh.almond:scala-kernel_2.13.11:0.13.11 \
      -o almond && \
    ./almond --install --id scala --display-name "Scala" && \
    rm almond coursier

# Copy notebooks
COPY --chown=1000:100 notebooks/ /home/jovyan/work/

# Verify installation
RUN jupyter kernelspec list

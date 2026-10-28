FROM almondsh/almond:0.15.0

USER root
# dot, used by reftree in interactive_computing_article.ipynb
RUN apt-get -y update && \
    apt-get install --no-install-recommends -y graphviz && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*
USER $NB_UID

# The plotly package ships the JupyterLab extension rendering plotly-scala charts.
# numpy, pandas and matplotlib are used from Scala via ScalaPy, in scalapy.ipynb.
RUN pip install --no-cache-dir \
      plotly==7.1.0 \
      numpy==2.4.6 \
      pandas==3.1.0 \
      matplotlib==3.11.2

# Spark needs these on Java 17 (see spark.ipynb). Jupyter runs the kernels with java directly,
# which picks up JDK_JAVA_OPTIONS.
ENV JDK_JAVA_OPTIONS="--add-opens=java.base/java.lang=ALL-UNNAMED \
--add-opens=java.base/java.lang.invoke=ALL-UNNAMED \
--add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
--add-opens=java.base/java.io=ALL-UNNAMED \
--add-opens=java.base/java.net=ALL-UNNAMED \
--add-opens=java.base/java.nio=ALL-UNNAMED \
--add-opens=java.base/java.util=ALL-UNNAMED \
--add-opens=java.base/java.util.concurrent=ALL-UNNAMED \
--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED \
--add-opens=java.base/jdk.internal.ref=ALL-UNNAMED \
--add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
--add-opens=java.base/sun.nio.cs=ALL-UNNAMED \
--add-opens=java.base/sun.security.action=ALL-UNNAMED \
--add-opens=java.base/sun.util.calendar=ALL-UNNAMED \
-Djdk.reflect.useDirectMethodHandle=false"

COPY --chown=${NB_UID}:${NB_GID} notebooks/ ${HOME}/

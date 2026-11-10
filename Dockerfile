FROM almondsh/almond:0.15.0

USER root
# dot, used by reftree in interactive_computing_article.ipynb
RUN apt-get -y update && \
    apt-get install --no-install-recommends -y graphviz && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# The JupyterLab setup below is the one of the dev.jupyter* Mill commands of almond, copied in jupyter/
# by scripts/update-jupyter-setup.sh (run it to update it).

# Node.js, for the ACP agents below, that need Node.js 22 or later (the conda environment of the image
# has Node.js 20). The SHA-256 checksums are those of the NODE_VERSION archives, listed in
# https://nodejs.org/dist/v<NODE_VERSION>/SHASUMS256.txt, and need to be updated along with it.
ARG NODE_VERSION=24.22.2
ARG NODE_X64_SHA256=361718c444e49af2ca5a110c83ba6a8901143f23ab285ebbef0341f391f482a9
ARG NODE_ARM64_SHA256=95c264150aceb7f3437a29fd6984924f5f8d1511ea3cbd6c508b83f6c555dae2
RUN set -eu; \
    case "$(uname -m)" in \
      x86_64) NODE_ARCH=x64; NODE_SHA256="$NODE_X64_SHA256" ;; \
      aarch64) NODE_ARCH=arm64; NODE_SHA256="$NODE_ARM64_SHA256" ;; \
      *) echo "Unsupported CPU architecture: $(uname -m)" >&2; exit 1 ;; \
    esac; \
    curl -fLo /tmp/node.tar.gz "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-${NODE_ARCH}.tar.gz"; \
    echo "$NODE_SHA256  /tmp/node.tar.gz" | sha256sum --strict -c -; \
    mkdir -p /opt/node; \
    tar -xzf /tmp/node.tar.gz -C /opt/node --strip-components=1 --no-same-owner; \
    rm -f /tmp/node.tar.gz; \
    mkdir -p /opt/acp-agents; \
    chown "${NB_UID}:${NB_GID}" /opt/acp-agents
USER $NB_UID
ENV PATH="/opt/acp-agents/node_modules/.bin:/opt/node/bin:${PATH}"

# The ACP agents Jupyter AI uses for its Claude and Codex personas. They use the ANTHROPIC_API_KEY and
# OPENAI_API_KEY environment variables (or the credentials of 'codex login', for Codex).
COPY --chown=${NB_UID}:${NB_GID} jupyter/acp-agents/ /opt/acp-agents/
RUN cd /opt/acp-agents && \
    npm ci --no-audit --no-fund --loglevel=error --cache /tmp/npm-cache && \
    rm -rf /tmp/npm-cache

# JupyterLab, its extensions (the variable inspector, …) and Jupyter AI, along with the packages the
# notebooks need:
# - plotly ships the JupyterLab extension rendering plotly-scala charts
# - numpy, pandas and matplotlib are used from Scala via ScalaPy, in scalapy.ipynb
# pyopenssl is updated too, as the version of the image (that conda uses) rejects the
# cryptography version that Jupyter AI pulls.
COPY --chown=${NB_UID}:${NB_GID} jupyter/requirements.txt /tmp/jupyter-requirements.txt
RUN pip install --no-cache-dir \
      -r /tmp/jupyter-requirements.txt \
      pyopenssl==26.4.0 \
      plotly==7.1.0 \
      numpy==2.4.6 \
      pandas==3.1.0 \
      matplotlib==3.11.2 && \
    rm -f /tmp/jupyter-requirements.txt

# Default JupyterLab settings (completions shown while typing, 2-space indentation, …). Settings
# changed by users still take precedence.
COPY --chown=${NB_UID}:${NB_GID} jupyter/jupyterlab-overrides.json ${CONDA_DIR}/share/jupyter/lab/settings/overrides.d/almond.json

# Makes the kernels track variables from the start, for the variable inspector
COPY --chown=${NB_UID}:${NB_GID} jupyter/add-kernel-options.py /tmp/add-kernel-options.py
RUN python /tmp/add-kernel-options.py --variable-inspector && \
    rm -f /tmp/add-kernel-options.py

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

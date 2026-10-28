# Almond Examples [![Binder](https://mybinder.org/badge_logo.svg)](https://mybinder.org/v2/gh/almond-sh/examples/main?urlpath=lab%2Ftree%2Findex.ipynb) [![nbviewer](https://img.shields.io/badge/view%20on-nbviewer-brightgreen.svg)](https://nbviewer.org/github/almond-sh/examples/tree/main/notebooks/index.ipynb) [![Deepnote](https://deepnote.com/buttons/launch-in-deepnote-small.svg)](https://deepnote.com/project/9970220b-94df-4e6e-9695-85dda6b14c59#%2Fexamples%2Fnotebooks%2Findex.ipynb)


A collection of [Jupyter](https://jupyter.org/) notebooks showing what you can do with the [Almond](https://almond.sh) Scala kernel.

The notebooks use almond 0.15.0, with Scala 2.13.

## Run interactively on Binder
The easiest way to get started is to [run the examples on Binder](https://mybinder.org/v2/gh/almond-sh/examples/main?urlpath=lab%2Ftree%2Findex.ipynb).
All you need is a browser!

Binder is an amazing service that allows you to create an executable environment out of a Git repository containing
Jupyter notebooks. That way, you can play with the examples and try new things without having to install anything locally.
Binder builds its environment from the [`Dockerfile`](Dockerfile) of this repository.

## See Notebook Output in nbviewer
You can view the notebooks directly on GitHub, as it has a basic renderer for Jupyter notebooks. It doesn't execute any JavaScript though, which severely limits its ability to show dynamically generated plots, e.g. from plotly.

A much better option is to render them through [nbviewer](https://nbviewer.org/). nbviewer supports loading notebooks directly from a repo on GitHub.

**[List of all notebooks in this project in nbviewer](https://nbviewer.org/github/almond-sh/examples/tree/main/notebooks/index.ipynb)**

## Run interactively in Deepnote
You can also try Almond very quickly by cloning [this Deepnote project with Almond kernel and examples](https://deepnote.com/project/9970220b-94df-4e6e-9695-85dda6b14c59#%2Fexamples%2Fnotebooks%2Findex.ipynb). Deepnote offers hosted notebooks with real-time collaboration capabilities for free.

## Running locally
An even better way to learn about Jupyter and Almond is to run it locally so you can try things out for yourself.

### Via docker
The [`Dockerfile`](Dockerfile) of this repository builds an image with the notebooks, based on the
[almond docker image](https://almond.sh/docs/try-docker) (whose images are only available for x86-64 as of almond 0.15.0).
Build and run it with

```bash
docker build -t almond-examples .
docker run -it --rm -p 8888:8888 almond-examples
```

Then copy the URL shown in the Docker output into your browser.

The image sets `JDK_JAVA_OPTIONS`, so that the kernels can run Spark (see [the Spark notebook](notebooks/spark.ipynb)).
To pass Java options of your own to the kernels, like `-Xmx8g`, use `JAVA_TOOL_OPTIONS` rather than
`JDK_JAVA_OPTIONS` (which would override the options of the image), like
```bash
docker run -it --rm -p 8888:8888 -e JAVA_TOOL_OPTIONS=-Xmx8g almond-examples
```

### Locally on your machine
To run these notebooks locally:
1. Install [JupyterLab](https://jupyterlab.readthedocs.io/en/stable/) (along with the [`plotly`](https://pypi.org/project/plotly/) Python package, for plotly charts to be shown in JupyterLab,
   and the `numpy`, `pandas` and `matplotlib` ones, for [the ScalaPy notebook](notebooks/scalapy.ipynb))
2. Install an [Almond kernel](https://almond.sh/docs/quick-start-install) for Scala 2.13, with the id `scala213`, like
   ```bash
   cs launch --use-bootstrap almond:0.15.0 --scala 2.13.18 -- --install --id scala213 --display-name "Scala 2.13"
   ```
3. Clone the project and run `jupyter lab` in the `notebooks` directory
4. Open one of the example notebooks and play with it!

## Checking the notebooks
CI runs all the notebooks in the Docker image, on each change and weekly, with the [Scala CLI](https://scala-cli.virtuslab.org)
script [`scripts/run-notebooks.sc`](scripts/run-notebooks.sc).
Cells showing code that doesn't compile or throws on purpose are tagged `raises-exception`: they must fail, and
no other cell may. To run some notebooks the same way locally, with the `scala213` kernel installed as above:
```bash
scala-cli scripts/run-notebooks.sc -- notebooks/scala-tour notebooks/spark.ipynb
```
or with [Mill](https://mill-build.org), like
```bash
./mill 'notebook[scala-tour].check'  # notebooks/scala-tour
./mill 'notebook[spark].check'       # notebooks/spark.ipynb
./mill __.check                      # all of them
```
On PRs, CI only runs the notebooks affected by the PR, using
[Mill selective execution](https://mill-build.org/mill/large/selective-execution.html): each module of
[`build.mill`](build.mill) depends on the files its notebooks use, and on those all notebook runs use
(`build.mill`, the `Dockerfile`, `scripts/run-notebooks.sc` and the CI workflow).

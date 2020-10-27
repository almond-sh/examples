# Almond Examples [![Binder](https://mybinder.org/badge_logo.svg)](https://mybinder.org/v2/gh/almond-sh/examples/master?urlpath=lab%2Ftree%2Fnotebooks%2Findex.ipynb) [![nbviewer](https://img.shields.io/badge/view%20on-nbviewer-brightgreen.svg)](https://nbviewer.jupyter.org/github/almond-sh/almond-examples/tree/master/notebooks/index.ipynb) [![Deepnote](https://deepnote.com/buttons/launch-in-deepnote-small.svg)](https://deepnote.com/project/9970220b-94df-4e6e-9695-85dda6b14c59#%2Fexamples%2Fnotebooks%2Findex.ipynb)


A collection of [Jupyter](http://jupyter.org/) notebooks showing what you can do with the [Almond](https://almond.sh) Scala kernel.

## Run interactively on Binder
The easiest way to get started is to [run the examples on Binder](https://mybinder.org/v2/gh/almond-sh/examples/master?urlpath=lab%2Ftree%2Fnotebooks%2Findex.ipynb).
All your need is a browser!

Binder is an amazing service that allows you to create an executable environment out of a Git repository containing
Jupyter notebooks. That way, you can play with the examples and try new things without having to install anything locally.

## See Notebook Output in nbviewer
You can view the notebooks directly on GitHub, as it has a basic renderer for Jupyter notebooks. It doesn't execute any JavaScript though, which severly limits its ability to show dynamically generated plots i.e. from plotly and vegas.

A much better option is to render them through [nbviewer](https://nbviewer.jupyter.org/). nbviewer supports loading notebooks directly from a repo on GitHub.

**[List of all notebooks in this project in nbviewer](https://nbviewer.jupyter.org/github/almond-sh/almond-examples/tree/master/notebooks/index.ipynb)**

## Run interactively in Deepnote
You can also try Almond very quickly by cloning [this Deepnote project with Almond kernel and examples](https://deepnote.com/project/9970220b-94df-4e6e-9695-85dda6b14c59#%2Fexamples%2Fnotebooks%2Findex.ipynb). Deepnote offers hosted notebooks with real-time collaboration capabilities for free.

## Running locally
An even better way to learn about Jupyter and Almond is to run it locally so you can try things out for yourself.

### Via docker
We provide a docker image for the current almond version, based on the [latest almond docker image](https://almond.sh/docs/try-docker). Run it with

```bash
docker run -it --rm -p 8888:8888 almondsh/examples:latest
```

Then copy the URL shown in the Docker output into your browser.
To use JupyterLab instead of the classic Notebook interface, replace *tree* with *lab* after opening the URL.

### Locally on your machine
To run these notebooks locally:
1. Install [Jupyter Notebook](http://jupyter.org/install) or [JupyterLab](https://jupyterlab.readthedocs.io/en/stable/)
2. Install an [Almond kernel](https://almond.sh/docs/quick-start-install)
3. Clone the project and run `jupyter notebook` or `jupyter lab` in the project directory
4. Open one of the example notebooks and play with it!

# Kviklet Demo Deployment Helm Chart
This helm chart is used to deploy a demo installation of kviklet onto a GKE cluster.
The demo is available at [demo.kviklet.dev](https://demo.kviklet.dev). However it's gated behind google identity aware proxy so only members of the kviklet org can access it.
If you want to access the demo, please reach out to me under jascha@kviklet.dev. You can also simply access a demo by running the container locally (see the [README](../../README.md) for instructions).

If you want to deploy Kviklet on Kubernetes yourself please use the base-chart for this. The demo chart is only meant to be used for the demo deployment and also serve as an example for how to use it.